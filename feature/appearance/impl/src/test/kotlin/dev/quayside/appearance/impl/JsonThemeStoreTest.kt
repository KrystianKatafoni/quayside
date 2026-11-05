@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.storage.BrokenFile
import dev.quayside.core.storage.InMemoryJsonStore
import dev.quayside.core.storage.JsonStore
import dev.quayside.core.storage.StorageError
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** A store that tells a test what the API published at the moment a save started. */
private class ObservingStore(
    private val inner: InMemoryJsonStore<AppearanceSettings>,
    private val onWrite: () -> Unit,
) : JsonStore<AppearanceSettings> by inner {
    override suspend fun save(value: AppearanceSettings) = inner.save(value).also { onWrite() }

    override suspend fun update(change: (AppearanceSettings) -> AppearanceSettings) =
        inner.update(change).also { onWrite() }
}

/** `JsonThemeStore` over an in-memory store: loading, saving, refusing, logging (plan AM-804). */
class JsonThemeStoreTest {
    private val logger = RecordingLogger()
    private val catalog = builtInCatalog()
    private val systemDark = MutableStateFlow<Boolean?>(null)
    private val brokenPath = Path.of("/Users/secret-name/theme.json.broken")
    private val custom =
        AppearanceSettings(
            SchemeId("graphite"),
            TypographyId("geist"),
            UiScale.L,
            ListDensity.COMPACT,
            DayNight(true, SchemeId("sand"), SchemeId("amber")),
        )

    private suspend fun TestScope.open(store: JsonStore<AppearanceSettings>): JsonThemeStore =
        JsonThemeStore.open(store, catalog, systemDark, backgroundScope, logger).also { runCurrent() }

    private fun loggedText(): String = logger.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }

    @Test
    fun `missing file gives defaults`() =
        runTest {
            val api = open(InMemoryJsonStore(AppearanceSettings()))

            api.settings.value shouldBe AppearanceSettings()
            api.applied.value shouldBe AppearanceSettings()
            api.catalog shouldBe catalog
        }

    @Test
    fun `saved settings survive reopen`() =
        runTest {
            val store = InMemoryJsonStore(AppearanceSettings())
            open(store).update { custom } shouldBe Outcome.Ok(custom)

            val reopened = open(store)

            reopened.settings.value shouldBe custom
        }

    @Test
    fun `update saves before publishing`() =
        runTest {
            val inner = InMemoryJsonStore(AppearanceSettings())
            var publishedDuringSave: AppearanceSettings? = null
            lateinit var api: AppearanceApi
            val store = ObservingStore(inner) { publishedDuringSave = api.settings.value }
            api = open(store)

            api.update { it.copy(uiScale = UiScale.L) }

            publishedDuringSave shouldBe AppearanceSettings()
            api.settings.value shouldBe AppearanceSettings(uiScale = UiScale.L)
            inner.saves shouldBe 1
            inner.load().getOrNull()?.value shouldBe AppearanceSettings(uiScale = UiScale.L)
        }

    @Test
    fun `failed save returns StorageFailed and keeps settings`() =
        runTest {
            val store = InMemoryJsonStore(AppearanceSettings())
            val api = open(store)
            store.failure = StorageError.IoFailed(Path.of("/data/theme.json"), "disk full")

            val result = api.update { custom }

            result shouldBe Outcome.Err(AppearanceError.StorageFailed)
            api.settings.value shouldBe AppearanceSettings()
            api.applied.value shouldBe AppearanceSettings()
            store.saves shouldBe 0
        }

    @Test
    fun `after a failed save the next update saves again`() =
        runTest {
            val store = InMemoryJsonStore(AppearanceSettings())
            val api = open(store)
            store.failure = StorageError.IoFailed(Path.of("/data/theme.json"), "disk full")
            api.update { custom }
            store.failure = null

            val result = api.update { custom }

            result shouldBe Outcome.Ok(custom)
            api.settings.value shouldBe custom
            store.saves shouldBe 1
        }

    @Test
    fun `newer schema file is never written`() =
        runTest {
            val store = InMemoryJsonStore(AppearanceSettings())
            store.failure = StorageError.NewerSchema(found = 2, supported = 1)
            val api = open(store)
            store.failure = null

            api.settings.value shouldBe AppearanceSettings()
            api.update { custom } shouldBe Outcome.Err(AppearanceError.StorageFailed)
            api.update { custom } shouldBe Outcome.Err(AppearanceError.StorageFailed)

            store.saves shouldBe 0
            api.settings.value shouldBe AppearanceSettings()
        }

    @Test
    fun `unreadable file is never written`() =
        runTest {
            val store = InMemoryJsonStore(AppearanceSettings())
            store.failure = StorageError.IoFailed(brokenPath, "denied")
            val api = open(store)
            store.failure = null

            api.update { custom } shouldBe Outcome.Err(AppearanceError.StorageFailed)

            store.saves shouldBe 0
            logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
            loggedText() shouldNotContain "secret-name"
        }

    @Test
    fun `broken file is moved aside and logged without contents`() =
        runTest {
            val store = InMemoryJsonStore(AppearanceSettings())
            store.recoverNextLoad = BrokenFile(brokenPath, "unparseable")

            val api = open(store)

            api.settings.value shouldBe AppearanceSettings()
            logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
            loggedText() shouldNotContain "secret-name"
            api.update { custom } shouldBe Outcome.Ok(custom)
        }

    @Test
    fun `unknown id in file falls back and logs a category`() =
        runTest {
            val stored =
                AppearanceSettings(
                    scheme = SchemeId("gone"),
                    typography = TypographyId("nope"),
                    uiScale = UiScale.L,
                    dayNight = DayNight(true, SchemeId("sand"), SchemeId("ghost")),
                )

            val api = open(InMemoryJsonStore(stored))

            api.settings.value shouldBe
                AppearanceSettings(
                    uiScale = UiScale.L,
                    dayNight = DayNight(true, SchemeId("sand"), SchemeId("plex")),
                )
            logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
            listOf("gone", "nope", "ghost").forEach { loggedText() shouldNotContain it }
        }

    @Test
    fun `update with unknown day scheme returns UnknownScheme and saves nothing`() =
        runTest {
            val store = InMemoryJsonStore(AppearanceSettings())
            val api = open(store)
            val unknown = SchemeId("no-such-day")

            val result = api.update { it.copy(dayNight = DayNight(true, unknown, SchemeId("plex"))) }

            result shouldBe Outcome.Err(AppearanceError.UnknownScheme(unknown))
            store.saves shouldBe 0
            api.settings.value shouldBe AppearanceSettings()
        }

    @Test
    fun `update with unknown night scheme returns UnknownScheme and saves nothing`() =
        runTest {
            val store = InMemoryJsonStore(AppearanceSettings())
            val api = open(store)
            val unknown = SchemeId("no-such-night")

            val result = api.update { it.copy(dayNight = DayNight(true, SchemeId("paper"), unknown)) }

            result shouldBe Outcome.Err(AppearanceError.UnknownScheme(unknown))
            store.saves shouldBe 0
        }

    @Test
    fun `update logs no values`() =
        runTest {
            val api = open(InMemoryJsonStore(AppearanceSettings()))

            api.update { custom }

            listOf("graphite", "sand", "amber", "geist").forEach { loggedText() shouldNotContain it }
        }

    @Test
    fun `concurrent updates keep both changes`() =
        runTest {
            val api = open(InMemoryJsonStore(AppearanceSettings()))

            awaitAll(
                async { api.update { it.copy(uiScale = UiScale.L) } },
                async { api.update { it.copy(density = ListDensity.COMPACT) } },
            )

            api.settings.value shouldBe AppearanceSettings(uiScale = UiScale.L, density = ListDensity.COMPACT)
        }

    @Test
    fun `applied follows the mode that changes while the app runs`() =
        runTest {
            val api = open(InMemoryJsonStore(custom))
            systemDark.value = null
            runCurrent()
            api.applied.value shouldBe custom

            systemDark.value = true
            runCurrent()
            api.applied.value shouldBe custom.copy(scheme = SchemeId("amber"))
            systemDark.value = false
            runCurrent()
            api.applied.value shouldBe custom.copy(scheme = SchemeId("sand"))
            systemDark.value = true
            runCurrent()
            api.applied.value shouldBe custom.copy(scheme = SchemeId("amber"))
        }
}
