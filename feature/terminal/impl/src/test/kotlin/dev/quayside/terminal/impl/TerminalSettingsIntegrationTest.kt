package dev.quayside.terminal.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.JsonStorage
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsIssue
import dev.quayside.terminal.api.TerminalSettingsRepo
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

/**
 * `terminalSettingsModule` on a real [JsonStorage] in a temp directory (docs/12: adapters on local resources).
 * Blocking file IO runs on a real thread pool; nothing waits on wall-clock time.
 */
class TerminalSettingsIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val storageDir: Path get() = tempDir.resolve("storage")
    private val file: Path get() = storageDir.resolve("terminal.json")
    private val broken: Path get() = storageDir.resolve("terminal.json.broken")

    private suspend fun open(): TerminalSettingsRepo = terminalSettingsModule(JsonStorage(storageDir, io), NoOpLogger)

    private val custom =
        TerminalSettings(
            shell = ShellProfile.ZSH,
            startDirectory = StartDirectory.HOME,
            fontFamily = "Menlo",
            fontSize = 15,
            scrollbackLines = 50_000,
            ligatures = false,
            optionAsMeta = true,
            bellNotification = false,
        )

    @Test
    fun `saved settings survive a new module`() =
        runTest {
            val first = open()
            first.update { custom } shouldBe Outcome.Ok(custom)

            val second = open()

            second.settings.value shouldBe custom
            second.issue.value shouldBe null
        }

    @Test
    fun `missing file gives the defaults`() =
        runTest {
            val repo = open()

            repo.settings.value shouldBe TerminalSettings()
            file.shouldNotExist()
        }

    @Test
    fun `broken terminal json is moved aside`() =
        runTest {
            Files.createDirectories(storageDir)
            file.writeText("{ not json")
            val original = file.readBytes()

            val repo = open()

            repo.issue.value shouldBe TerminalSettingsIssue.RECOVERED_FROM_BROKEN_FILE
            repo.settings.value shouldBe TerminalSettings()
            broken.readBytes() shouldBe original
            file.shouldNotExist()
            repo.update { custom } shouldBe Outcome.Ok(custom)
            file.shouldExist()
            broken.readBytes() shouldBe original
        }

    @Test
    fun `file layout matches ADR 0016`() =
        runTest {
            open().update { TerminalSettings() }

            val root = Json.parseToJsonElement(Files.readString(file))

            root shouldBe
                JsonObject(
                    mapOf(
                        "schemaVersion" to JsonPrimitive(1),
                        "data" to
                            JsonObject(
                                mapOf(
                                    "shell" to JsonPrimitive("LOGIN_DEFAULT"),
                                    "startDirectory" to JsonPrimitive("SESSION_WORKTREE"),
                                    "fontFamily" to JsonNull,
                                    "fontSize" to JsonPrimitive(13),
                                    "scrollbackLines" to JsonPrimitive(100_000),
                                    "ligatures" to JsonPrimitive(true),
                                    "optionAsMeta" to JsonPrimitive(false),
                                ),
                            ),
                    ),
                )
        }

    @Test
    fun `one bad field in the file gives its default and keeps the rest`() =
        runTest {
            Files.createDirectories(storageDir)
            file.writeText("""{"schemaVersion":1,"data":{"shell":"TCSH","fontSize":18,"unknown":true}}""")

            val repo = open()

            repo.settings.value shouldBe TerminalSettings(fontSize = 18)
            repo.issue.value shouldBe null
            broken.shouldNotExist()
        }
}
