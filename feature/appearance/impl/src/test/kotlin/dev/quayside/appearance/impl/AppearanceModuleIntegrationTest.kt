@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.SampleSchemes
import dev.quayside.appearance.api.SchemeError
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.ContrastAudit
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * `appearanceModule` and `themeFilesModule` on a real [JsonStorage] in a temp directory and a fake `defaults`
 * (docs/12: adapters on local resources). File IO runs on real threads; the polling runs in virtual time.
 */
class AppearanceModuleIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val logger = RecordingLogger()
    private val home = Path.of("/Users/test")

    /** What `defaults` answers; a test changes it to turn macOS dark or light. */
    private var answer: ProcessResult = ProcessResult(1, "", "does not exist")
    private val runner = FakeProcessRunner { _: ShellCommand -> Outcome.Ok(answer) }

    @AfterEach
    fun closeDispatcher() = io.close()

    private val mine = SampleSchemes.mine()
    private val storageDir: Path get() = tempDir.resolve("storage")
    private val file: Path get() = storageDir.resolve("theme.json")

    private suspend fun TestScope.open(): AppearanceApi =
        appearanceModule(JsonStorage(storageDir, io), runner, home, backgroundScope, logger)

    private val dayNight = DayNight(enabled = true, day = SchemeId("sand"), night = SchemeId("amber"))

    @Test
    fun `appearance survives restart`() =
        runTest {
            val wanted = AppearanceSettings(scheme = SchemeId("graphite"), uiScale = UiScale.S, dayNight = dayNight)
            open().update { wanted } shouldBe Outcome.Ok(wanted)

            val second = open()

            second.settings.value shouldBe wanted
        }

    @Test
    fun `missing file gives the defaults and creates no file`() =
        runTest {
            val api = open()

            api.settings.value shouldBe AppearanceSettings()
            api.catalog.value shouldBe builtInCatalog()
            runner.calls.shouldBeEmpty()
            file.shouldNotExist()
        }

    @Test
    fun `no process is run while day night is off`() =
        runTest {
            open().update { it.copy(uiScale = UiScale.L) }

            advanceTimeBy(60_000)
            runCurrent()

            runner.calls.shouldBeEmpty()
        }

    @Test
    fun `first read of the mode happens before the module returns`() =
        runTest {
            open().update { it.copy(dayNight = dayNight) }
            answer = ProcessResult(0, "Dark\n", "")

            val second = open()

            second.applied.value.scheme shouldBe SchemeId("amber")
            second.settings.value.scheme shouldBe AppearanceSettings().scheme
            runner.commands.first().argv shouldBe listOf("defaults", "read", "-g", "AppleInterfaceStyle")
        }

    @Test
    fun `a changed mode reaches applied within 5 seconds`() =
        runTest {
            val api = open()
            api.update { it.copy(dayNight = dayNight) }
            runCurrent()
            api.applied.value.scheme shouldBe SchemeId("sand")

            answer = ProcessResult(0, "Dark\n", "")
            advanceTimeBy(5_001)
            runCurrent()

            api.applied.value.scheme shouldBe SchemeId("amber")
        }

    @Test
    fun `theme json is written in the envelope format`() =
        runTest {
            open().update { it.copy(uiScale = UiScale.L, dayNight = dayNight) }

            val root = Json.parseToJsonElement(file.readText()).jsonObject
            root["schemaVersion"] shouldBe JsonPrimitive(1)
            root["data"] shouldBe
                encodeSettings(AppearanceSettings(uiScale = UiScale.L, dayNight = dayNight))
        }

    @Test
    fun `a file with a bad value loads with the default of that field`() =
        runTest {
            storageDir.toFile().mkdirs()
            file.writeText("""{"schemaVersion":1,"data":{"scheme":"graphite","uiScale":"XL","dayScheme":"ghost"}}""")

            val api = open()

            api.settings.value shouldBe AppearanceSettings(scheme = SchemeId("graphite"))
            val logged = logger.entriesAt(LogLevel.WARN).joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldContain "uiScale"
            logged shouldContain "dayScheme"
            logged shouldNotContain "ghost"
            logged shouldNotContain "XL"
        }

    @Test
    fun `a file that is not an object is moved to theme json broken`() =
        runTest {
            storageDir.toFile().mkdirs()
            file.writeText("[1, 2, 3]")

            val api = open()

            api.settings.value shouldBe AppearanceSettings()
            storageDir.resolve("theme.json.broken").shouldExist()
            api.update { it.copy(uiScale = UiScale.L) } shouldBe Outcome.Ok(AppearanceSettings(uiScale = UiScale.L))
        }

    @Test
    fun `a newer file is left untouched and updates fail`() =
        runTest {
            storageDir.toFile().mkdirs()
            file.writeText("""{"schemaVersion":2,"data":{"scheme":"graphite"}}""")
            val before = file.readBytes().toList()

            val api = open()

            api.settings.value shouldBe AppearanceSettings()
            api.update { it.copy(uiScale = UiScale.L) } shouldBe Outcome.Err(AppearanceError.StorageFailed)
            file.readBytes().toList() shouldBe before
        }

    @Test
    fun `exported file loads as theme json at start`() =
        runTest {
            val wanted = AppearanceSettings(scheme = SchemeId("graphite"), uiScale = UiScale.S, dayNight = dayNight)
            storageDir.toFile().mkdirs()
            val files = themeFilesModule(open(), io, NoOpLogger)

            files.write(wanted, file) shouldBe Outcome.Ok(Unit)

            open().settings.value shouldBe wanted
        }

    @Test
    fun `added scheme survives restart and can be chosen`() =
        runTest {
            val first = open()
            first.addScheme(mine).shouldBeInstanceOf<Outcome.Ok<*>>()
            first.update { it.copy(scheme = mine.id) } shouldBe
                Outcome.Ok(AppearanceSettings(scheme = mine.id, customSchemes = listOf(mine)))

            val second = open()

            second.catalog.value
                .theme(mine.id)
                ?.name shouldBe "Mine"
            second.settings.value shouldBe AppearanceSettings(scheme = mine.id, customSchemes = listOf(mine))
        }

    @Test
    fun `theme json without custom schemes has no customSchemes key`() =
        runTest {
            val api = open()

            api.update { it.copy(uiScale = UiScale.S) }

            file.readText() shouldNotContain "customSchemes"
        }

    @Test
    fun `scheme with low contrast is not saved`() =
        runTest {
            val api = open()

            val grey = mine.copy(colors = mine.colors.copy(text = SampleSchemes.color("#222222")))

            api.addScheme(grey) shouldBe
                Outcome.Err(SchemeError.LowContrast(ContrastAudit.problems(themeOf(grey).colors)))

            file.toFile().exists() shouldBe false
        }

    @Test
    fun `exported custom schemes can be imported again`() =
        runTest {
            storageDir.toFile().mkdirs()
            val api = open()
            api.addScheme(mine)
            val files = themeFilesModule(api, io, NoOpLogger)
            val exported = tempDir.resolve("export.json")

            files.write(api.settings.value, exported) shouldBe Outcome.Ok(Unit)

            files.readSchemes(exported) shouldBe Outcome.Ok(listOf(mine))
        }
}
