package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.TrustError
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.RecordingLogger
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/** [ClaudeJsonTrust] on real files in a temporary directory (plan AM-309, rule 3). */
@Suppress("InjectDispatcher")
class ClaudeJsonTrustTest {
    @TempDir
    lateinit var dir: Path

    private val logger = RecordingLogger()
    private val io: CoroutineDispatcher = Dispatchers.IO

    private val home: Path get() = dir.resolve("home").createDirectories()
    private val config: Path get() = home.resolve(".claude.json")
    private val project: Path get() = dir.resolve("project").createDirectories().toRealPath()

    private fun trust(tuning: TrustTuning = TrustTuning()) = ClaudeJsonTrust(home, io, logger, tuning)

    private fun writeConfig(text: String): Path = config.also { it.writeText(text) }

    private fun tree(file: Path = config): JsonElement = Json.parseToJsonElement(file.readText())

    private fun trustFlag(key: Path): JsonElement? =
        (((tree() as JsonObject)["projects"] as? JsonObject)?.get(key.toString()) as? JsonObject)?.get(FLAG)

    private fun otherFiles(): List<String> = home.listDirectoryEntries().map { it.name }.filter { it != ".claude.json" }

    @Test
    fun `writes trust and keeps other content`() =
        runTest {
            val key = project
            writeConfig(
                """{"numStartups":7,"big":12345678901234567890,""" +
                    """"projects":{"/other":{"$FLAG":false,"allowedTools":["Bash"]}}}""",
            )

            trust().trust(key) shouldBe Outcome.Ok(Unit)

            tree() shouldBe
                Json.parseToJsonElement(
                    """{"numStartups":7,"big":12345678901234567890,"projects":{"/other":{"$FLAG":false,""" +
                        """"allowedTools":["Bash"]},"$key":{"$FLAG":true}}}""",
                )
            otherFiles() shouldBe emptyList()
        }

    @Test
    fun `already trusted leaves the bytes untouched`() =
        runTest {
            val key = project
            val odd = """{ "projects" :{"$key":{"$FLAG":  true}}   }"""
            writeConfig(odd)
            val before = config.readBytes()

            trust().trust(key) shouldBe Outcome.Ok(Unit)

            config.readBytes().toList() shouldBe before.toList()
            otherFiles() shouldBe emptyList()
        }

    @Test
    fun `missing config gives CONFIG_MISSING and creates nothing`() =
        runTest {
            val result = trust().trust(project)

            assertSoftly {
                result shouldBe Outcome.Err(TrustError.CONFIG_MISSING)
                config.exists() shouldBe false
                otherFiles() shouldBe emptyList()
            }
        }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("dev.quayside.agentruntime.impl.TrustJsonTest#invalidInputs")
    fun `invalid config is never written`(input: String) =
        runTest {
            writeConfig(input.replace("/Users/test/projects/app", project.toString()))
            val before = config.readBytes()

            val result = trust().trust(project)

            assertSoftly {
                result shouldBe Outcome.Err(TrustError.CONFIG_INVALID)
                config.readBytes().toList() shouldBe before.toList()
                otherFiles() shouldBe emptyList()
            }
        }

    @Test
    fun `invalid utf 8 gives CONFIG_INVALID`() =
        runTest {
            val bytes =
                "{\"projects\":{},\"x\":\"".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28) + "\"}".toByteArray()
            config.writeBytes(bytes)

            val result = trust().trust(project)

            assertSoftly {
                result shouldBe Outcome.Err(TrustError.CONFIG_INVALID)
                config.readBytes().toList() shouldBe bytes.toList()
            }
        }

    @Test
    fun `missing directory gives DIRECTORY_MISSING`() =
        runTest {
            writeConfig("""{"projects":{}}""")
            val before = config.readBytes()

            trust().trust(dir.resolve("nope")) shouldBe Outcome.Err(TrustError.DIRECTORY_MISSING)

            config.readBytes().toList() shouldBe before.toList()
        }

    @Test
    fun `a file instead of a directory gives DIRECTORY_MISSING`() =
        runTest {
            writeConfig("""{"projects":{}}""")
            val file = dir.resolve("file.txt").also { it.writeText("x") }

            trust().trust(file) shouldBe Outcome.Err(TrustError.DIRECTORY_MISSING)

            trustFlag(file) shouldBe null
        }

    @Test
    fun `a symlinked directory is trusted under its real path`() =
        runTest {
            writeConfig("""{"projects":{}}""")
            val real = project
            val link = dir.resolve("link").also { it.createSymbolicLinkPointingTo(real) }

            trust().trust(link) shouldBe Outcome.Ok(Unit)

            assertSoftly {
                trustFlag(real) shouldBe Json.parseToJsonElement("true")
                trustFlag(link) shouldBe null
            }
        }

    @Test
    fun `parents are never trusted`() =
        runTest {
            val child = project.resolve("sub").createDirectories()
            writeConfig("""{"projects":{"$project":{"$FLAG":false}}}""")

            trust().trust(child) shouldBe Outcome.Ok(Unit)

            assertSoftly {
                trustFlag(child) shouldBe Json.parseToJsonElement("true")
                trustFlag(project) shouldBe Json.parseToJsonElement("false")
                trustFlag(project.parent) shouldBe null
            }
        }

    @Test
    fun `keeps owner only permissions`() =
        runTest {
            writeConfig("""{"projects":{}}""")
            Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("rw-------"))

            trust().trust(project) shouldBe Outcome.Ok(Unit)

            PosixFilePermissions.toString(Files.getPosixFilePermissions(config)) shouldBe "rw-------"
        }

    @Test
    fun `keeps the permissions of the original`() =
        runTest {
            writeConfig("""{"projects":{}}""")
            Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("rw-r-----"))

            trust().trust(project) shouldBe Outcome.Ok(Unit)

            PosixFilePermissions.toString(Files.getPosixFilePermissions(config)) shouldBe "rw-r-----"
        }

    @Test
    fun `a symlinked config is refused and stays a link`() =
        runTest {
            val target = dir.resolve("real.json").also { it.writeText("""{"projects":{}}""") }
            config.createSymbolicLinkPointingTo(target)

            val result = trust().trust(project)

            assertSoftly {
                result shouldBe Outcome.Err(TrustError.CONFIG_UNSUPPORTED)
                config.isSymbolicLink() shouldBe true
                target.readText() shouldBe """{"projects":{}}"""
            }
        }

    @Test
    fun `too large config is refused`() =
        runTest {
            val text = """{"projects":{},"pad":"${"x".repeat(100)}"}"""
            writeConfig(text)

            val result = trust(TrustTuning(maxBytes = 50)).trust(project)

            assertSoftly {
                result shouldBe Outcome.Err(TrustError.CONFIG_UNSUPPORTED)
                config.readText() shouldBe text
            }
        }

    @Test
    fun `a change before commit is retried on the fresh content`() =
        runTest {
            val key = project
            writeConfig("""{"projects":{}}""")
            var calls = 0
            val tuning =
                TrustTuning(
                    beforeCommit = {
                        calls++
                        if (calls == 1) config.writeText("""{"projects":{},"foreign":1}""")
                    },
                )

            trust(tuning).trust(key) shouldBe Outcome.Ok(Unit)

            assertSoftly {
                tree() shouldBe Json.parseToJsonElement("""{"projects":{"$key":{"$FLAG":true}},"foreign":1}""")
                calls shouldBe 2
                otherFiles() shouldBe emptyList()
            }
        }

    @Test
    fun `changes on every attempt give CONCURRENT_CHANGE and keep the foreign content`() =
        runTest {
            val key = project
            writeConfig("""{"projects":{}}""")
            var calls = 0
            val tuning = TrustTuning(beforeCommit = { config.writeText("""{"projects":{},"foreign":${++calls}}""") })

            val result = trust(tuning).trust(key)

            assertSoftly {
                result shouldBe Outcome.Err(TrustError.CONCURRENT_CHANGE)
                calls shouldBe 3
                tree() shouldBe Json.parseToJsonElement("""{"projects":{},"foreign":3}""")
                otherFiles() shouldBe emptyList()
            }
        }

    @Test
    fun `a failed move leaves no temp file and the old content`() =
        runTest {
            val text = """{"projects":{}}"""
            writeConfig(text)
            val tuning = TrustTuning(move = { _, _ -> throw IOException("disk full at $home") })

            val result = trust(tuning).trust(project)

            assertSoftly {
                result shouldBe Outcome.Err(TrustError.WRITE_FAILED)
                config.readText() shouldBe text
                otherFiles() shouldBe emptyList()
            }
        }

    @Test
    fun `logs never contain the path or the content`() =
        runTest {
            val key = project
            val secret = "secret-token-4711"
            writeConfig("""{"oauthAccount":"$secret","projects":{}}""")
            val failing = TrustTuning(move = { _, _ -> throw IOException("cannot write $key $secret") })

            trust().trust(key)
            trust(failing).trust(project.resolve("sub").createDirectories())
            writeConfig("""{"projects":[],"x":"$secret"}""")
            trust().trust(key)
            trust().trust(dir.resolve("nope"))

            val logged = logger.entries.toString()
            assertSoftly {
                logged shouldNotContain key.toString()
                logged shouldNotContain dir.toString()
                logged shouldNotContain secret
                logged shouldNotContain "Exception"
            }
        }

    private companion object {
        const val FLAG = "hasTrustDialogAccepted"
    }
}
