@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.UiScale
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.appearance.impl.builtInCatalog
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.storage.JsonStorage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors

/**
 * `appearanceModule` the way `main` builds it: a real [JsonStorage] in a temp directory and a fake `defaults`
 * (plan AM-804). File IO runs on real threads; nothing waits on the clock.
 */
class AppearanceStorageTest {
    @TempDir
    lateinit var tempDir: Path

    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private var defaults = ProcessResult(1, "", "does not exist")
    private val runner = FakeProcessRunner { Outcome.Ok(defaults) }
    private val catalog = builtInCatalog()
    private val dayNight = DayNight(enabled = true, day = SchemeId("paper"), night = SchemeId("graphite"))

    private suspend fun TestScope.open(): AppearanceApi =
        appearanceModule(
            JsonStorage(tempDir.resolve("storage"), io),
            runner,
            Path.of("/Users/test"),
            backgroundScope,
            NoOpLogger,
        )

    @Test
    fun `appearance survives restart`() =
        runTest {
            val wanted =
                AppearanceSettings(
                    SchemeId("sand"),
                    uiScale = UiScale.L,
                    density = ListDensity.COMPACT,
                    dayNight = dayNight,
                )
            open().update { wanted } shouldBe Outcome.Ok(wanted)

            val second = open()

            second.settings.value shouldBe wanted
        }

    @Test
    fun `the first window already has the look of the macOS mode`() =
        runTest {
            // Given a saved night theme and macOS dark at the next start
            open().update { it.copy(dayNight = dayNight) }
            defaults = ProcessResult(0, "Dark\n", "")

            // When the app builds the looks the way main does
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val looks = appLooks(open(), scope)

            // Then the first value is the night theme, with no switch after the window opens
            looks.value.colors shouldBe catalog.theme(SchemeId("graphite"))?.colors
            scope.cancel()
        }
}
