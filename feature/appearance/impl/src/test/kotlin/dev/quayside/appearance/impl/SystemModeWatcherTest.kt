@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.SchemeId
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.storage.InMemoryJsonStore
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** The source that answers with [next] and counts the reads; a test changes [next] between polls. */
private class ScriptedSource(
    var next: Boolean? = true,
) : SystemModeSource {
    var reads: Int = 0
        private set

    override suspend fun read(): Boolean? {
        reads++
        return next
    }
}

/** Polling of the macOS mode only while day/night is on (plan AM-804, virtual time, no real clock). */
class SystemModeWatcherTest {
    private val logger = RecordingLogger()
    private val on = AppearanceSettings(dayNight = DayNight(enabled = true))
    private val off = AppearanceSettings()

    private fun TestScope.watcher(
        source: SystemModeSource,
        settings: MutableStateFlow<AppearanceSettings>,
    ) = SystemModeWatcher(source, settings, backgroundScope, logger, poll = 5.seconds)

    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun warnings(): Int = logger.entriesAt(LogLevel.WARN).size

    @Test
    fun `does not poll while day night is off`() =
        runTest {
            val source = ScriptedSource()
            val watcher = watcher(source, MutableStateFlow(off))

            watcher.start()
            elapse(60_000)

            source.reads shouldBe 0
            watcher.mode.value shouldBe null
        }

    @Test
    fun `first read happens before start returns`() =
        runTest {
            val source = ScriptedSource(next = true)
            val watcher = watcher(source, MutableStateFlow(on))

            watcher.start()

            source.reads shouldBe 1
            watcher.mode.value shouldBe true
        }

    @Test
    fun `polls every 5 seconds while on`() =
        runTest {
            val source = ScriptedSource()
            watcher(source, MutableStateFlow(on)).start()
            source.reads shouldBe 1

            elapse(4_999)
            source.reads shouldBe 1
            elapse(1)
            source.reads shouldBe 2
            elapse(5_000)
            source.reads shouldBe 3
        }

    @Test
    fun `mode follows the source at every poll`() =
        runTest {
            val source = ScriptedSource(next = true)
            val watcher = watcher(source, MutableStateFlow(on))
            watcher.start()
            watcher.mode.value shouldBe true

            source.next = false
            elapse(5_000)
            watcher.mode.value shouldBe false

            source.next = true
            elapse(5_000)
            watcher.mode.value shouldBe true
        }

    @Test
    fun `stops polling after turning off`() =
        runTest {
            val source = ScriptedSource()
            val settings = MutableStateFlow(on)
            watcher(source, settings).start()
            elapse(5_000)
            val before = source.reads

            settings.value = off
            runCurrent()
            elapse(60_000)

            source.reads shouldBe before
        }

    @Test
    fun `last known mode stays after turning off`() =
        runTest {
            val settings = MutableStateFlow(on)
            val watcher = watcher(ScriptedSource(next = true), settings)
            watcher.start()

            settings.value = off
            runCurrent()

            watcher.mode.value shouldBe true
        }

    @Test
    fun `turning on later reads at once and then polls`() =
        runTest {
            val source = ScriptedSource(next = true)
            val settings = MutableStateFlow(off)
            val watcher = watcher(source, settings)
            watcher.start()
            elapse(10_000)
            source.reads shouldBe 0

            settings.value = on
            runCurrent()

            source.reads shouldBe 1
            watcher.mode.value shouldBe true
            elapse(5_000)
            source.reads shouldBe 2
        }

    @Test
    fun `turning off and on again does not start a second loop`() =
        runTest {
            val source = ScriptedSource()
            val settings = MutableStateFlow(on)
            watcher(source, settings).start()
            settings.value = off
            runCurrent()
            settings.value = on
            runCurrent()
            val before = source.reads

            elapse(5_000)
            elapse(5_000)

            source.reads shouldBe before + 2
        }

    @Test
    fun `read error keeps the last known mode`() =
        runTest {
            val source = ScriptedSource(next = true)
            val watcher = watcher(source, MutableStateFlow(on))
            watcher.start()

            source.next = null
            elapse(5_000)

            source.reads shouldBe 2
            watcher.mode.value shouldBe true
        }

    @Test
    fun `mode is unknown until the first successful read`() =
        runTest {
            val source = ScriptedSource(next = null)
            val watcher = watcher(source, MutableStateFlow(on))

            watcher.start()
            watcher.mode.value shouldBe null
            source.next = false
            elapse(5_000)

            watcher.mode.value shouldBe false
        }

    @Test
    fun `read error is logged once per streak`() =
        runTest {
            val source = ScriptedSource(next = true)
            val watcher = watcher(source, MutableStateFlow(on))
            watcher.start()
            warnings() shouldBe 0

            source.next = null
            elapse(5_000)
            elapse(5_000)
            elapse(5_000)
            warnings() shouldBe 1

            source.next = false
            elapse(5_000)
            warnings() shouldBe 1
            source.next = null
            elapse(5_000)
            warnings() shouldBe 2
            watcher.mode.value shouldBe false
        }

    @Test
    fun `a failed first read is logged once`() =
        runTest {
            val source = ScriptedSource(next = null)
            watcher(source, MutableStateFlow(on)).start()

            elapse(5_000)

            warnings() shouldBe 1
        }

    @Test
    fun `applied switches to night when macOS turns dark`() =
        runTest {
            // Given day/night on with paper by day and sunset-like night, and macOS light
            val night = SchemeId("graphite")
            val settings = AppearanceSettings(dayNight = DayNight(true, SchemeId("sand"), night))
            val store = InMemoryJsonStore(settings)
            val source = ScriptedSource(next = false)
            val watcher = SystemModeWatcher(source, MutableStateFlow(settings), backgroundScope, logger)
            watcher.start()
            val api = JsonThemeStore.open(store, builtInCatalog(), watcher.mode, backgroundScope, logger)
            runCurrent()
            api.applied.value.scheme shouldBe SchemeId("sand")

            // When macOS turns dark and the next poll runs
            source.next = true
            elapse(5_000)

            // Then the applied theme is the night one, the saved scheme is not touched
            api.applied.value.scheme shouldBe night
            api.settings.value.scheme shouldBe settings.scheme
        }
}
