package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeFileError
import dev.quayside.appearance.api.ThemeFiles
import dev.quayside.appearance.api.ThemeFilesContract
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.RecordingLogger
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executors
import java.util.stream.Stream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/** `FileThemeFiles` on a temp directory: the shared contract plus the strict reading and the atomic writing. */
class FileThemeFilesTest : ThemeFilesContract() {
    @TempDir
    lateinit var tempDir: Path

    // Real threads: the files are read and written by blocking IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val logger = RecordingLogger()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val subject get() = FileThemeFiles(builtInCatalog(), io, logger)

    override fun files(): ThemeFiles = subject

    override fun path(name: String): Path = tempDir.resolve(name)

    override fun plantNewer(
        path: Path,
        found: Int,
    ) = path.writeText("""{"schemaVersion": $found, "data": {}}""")

    override fun plantUnknownScheme(path: Path) =
        path.writeText("""{"schemaVersion": 1, "data": {"scheme": "no-such-theme"}}""")

    private fun envelope(data: String) = """{"schemaVersion": 1, "data": $data}"""

    private fun text(file: String): Path = path(file)

    @ParameterizedTest(name = "{0}")
    @MethodSource("notThemeFiles")
    fun `file that is not a theme file is NotThemeFile`(
        name: String,
        content: String,
    ) = runTest {
        val target = text("x.json").also { it.writeText(content) }

        withClue(name) { subject.read(target) shouldBe Outcome.Err(ThemeFileError.NotThemeFile) }
    }

    @Test
    fun `bytes that are not UTF-8 are NotThemeFile`() =
        runTest {
            val target = text("bytes.json").also { it.writeBytes(byteArrayOf(0xC3.toByte(), 0x28, 0x7B, 0x7D)) }

            subject.read(target) shouldBe Outcome.Err(ThemeFileError.NotThemeFile)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValues")
    fun `present but invalid field is InvalidValue`(
        name: String,
        data: String,
    ) = runTest {
        val target = text("x.json").also { it.writeText(envelope(data)) }

        withClue(name) { subject.read(target) shouldBe Outcome.Err(ThemeFileError.InvalidValue) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lenientFiles")
    fun `missing and unknown fields are fine`(
        name: String,
        data: String,
        expected: AppearanceSettings,
    ) = runTest {
        val target = text("x.json").also { it.writeText(envelope(data)) }

        withClue(name) { subject.read(target) shouldBe Outcome.Ok(expected) }
    }

    @Test
    fun `newer version is reported before the data is looked at`() =
        runTest {
            val target = text("newer.json").also { it.writeText("""{"schemaVersion": 2}""") }

            subject.read(target) shouldBe Outcome.Err(ThemeFileError.NewerVersion(2))
        }

    @Test
    fun `a directory is ReadFailed`() =
        runTest {
            subject.read(tempDir) shouldBe Outcome.Err(ThemeFileError.ReadFailed)
        }

    @Test
    fun `file of exactly 64 KiB is read`() =
        runTest {
            val target = text("limit.json").also { it.writeText(padded(SIZE_LIMIT)) }

            subject.read(target) shouldBe Outcome.Ok(AppearanceSettings())
        }

    @Test
    fun `file over 64 KiB is ReadFailed without reading it all`() =
        runTest {
            val target = text("big.json").also { it.writeText(padded(SIZE_LIMIT + 1)) }
            val huge = text("huge.json")
            RandomAccessFile(huge.toFile(), "rw").use { it.setLength(HUGE) }

            subject.read(target) shouldBe Outcome.Err(ThemeFileError.ReadFailed)
            subject.read(huge) shouldBe Outcome.Err(ThemeFileError.ReadFailed)
        }

    @Test
    fun `read logs no path and no value`() =
        runTest {
            val secretFile =
                text(
                    "secret-user-name.json",
                ).also { it.writeText(envelope("""{"scheme": "SECRET-ID"}""")) }

            subject.read(secretFile)
            subject.read(text("missing-secret-user-name.json"))

            val logged = logger.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain "secret-user-name"
            logged shouldNotContain "SECRET-ID"
        }

    @Test
    fun `written file is theme json v1 with the flat data object`() =
        runTest {
            val settings =
                AppearanceSettings(
                    SchemeId("graphite"),
                    TypographyId("geist"),
                    UiScale.L,
                    ListDensity.COMPACT,
                    DayNight(true, SchemeId("sand"), SchemeId("amber")),
                )
            val target = text("out.json")

            subject.write(settings, target) shouldBe Outcome.Ok(Unit)

            val root = Json.parseToJsonElement(target.readText()).jsonObject
            root["schemaVersion"] shouldBe JsonPrimitive(1)
            root["data"] shouldBe encodeSettings(settings)
        }

    @Test
    fun `write leaves no temp file next to the target`() =
        runTest {
            val target = text("out.json")

            subject.write(AppearanceSettings(), target)
            subject.write(AppearanceSettings(uiScale = UiScale.S), target)

            tempDir.listDirectoryEntries().map { it.fileName.toString() } shouldBe listOf("out.json")
        }

    @Test
    fun `write to a read-only directory is WriteFailed and leaves no temp file`() =
        runTest {
            val locked = Files.createDirectory(tempDir.resolve("locked"))
            val target = locked.resolve("theme.json")
            subject.write(AppearanceSettings(uiScale = UiScale.S), target) shouldBe Outcome.Ok(Unit)
            val before = target.readBytes()
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"))
            try {
                val result = subject.write(AppearanceSettings(uiScale = UiScale.L), target)

                result shouldBe Outcome.Err(ThemeFileError.WriteFailed)
                locked.listDirectoryEntries().map { it.fileName.toString() } shouldBe listOf("theme.json")
                target.readBytes().toList() shouldBe before.toList()
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"))
            }
        }

    @Test
    fun `write onto a non-empty directory is WriteFailed and removes the temp file`() =
        runTest {
            val out = Files.createDirectory(tempDir.resolve("out"))
            val taken = Files.createDirectory(out.resolve("taken"))
            Files.createFile(taken.resolve("child"))

            subject.write(AppearanceSettings(), taken) shouldBe Outcome.Err(ThemeFileError.WriteFailed)

            out.listDirectoryEntries().map { it.fileName.toString() } shouldBe listOf("taken")
        }

    @Test
    fun `write into a directory that does not exist is WriteFailed`() =
        runTest {
            val target = tempDir.resolve("no-such-dir").resolve("theme.json")

            subject.write(AppearanceSettings(), target) shouldBe Outcome.Err(ThemeFileError.WriteFailed)
        }

    private fun padded(size: Int): String {
        val core = envelope("{}")
        return core + " ".repeat(size - core.length)
    }

    companion object {
        private const val SIZE_LIMIT = 64 * 1024
        private const val HUGE = 200L * 1024 * 1024

        @JvmStatic
        fun notThemeFiles(): Stream<Arguments> =
            Stream.of(
                Arguments.of("not json", "this is not json"),
                Arguments.of("empty file", ""),
                Arguments.of("json array", "[]"),
                Arguments.of("no schemaVersion", """{"data": {}}"""),
                Arguments.of("schemaVersion as a string", """{"schemaVersion": "1", "data": {}}"""),
                Arguments.of("schemaVersion below 1", """{"schemaVersion": 0, "data": {}}"""),
                Arguments.of("no data", """{"schemaVersion": 1}"""),
                Arguments.of("data is an array", """{"schemaVersion": 1, "data": []}"""),
                Arguments.of("data is a string", """{"schemaVersion": 1, "data": "x"}"""),
            )

        @JvmStatic
        fun invalidValues(): Stream<Arguments> =
            Stream.of(
                Arguments.of("scheme of a wrong type", """{"scheme": 5}"""),
                Arguments.of("scheme outside the catalog", """{"scheme": "no-such-theme"}"""),
                Arguments.of("scheme in a bad format", """{"scheme": "Plex!"}"""),
                Arguments.of("typography outside the catalog", """{"typography": "nope"}"""),
                Arguments.of("unknown uiScale", """{"uiScale": "XL"}"""),
                Arguments.of("density as a number", """{"density": 1}"""),
                Arguments.of("followSystem as a string", """{"followSystem": "true"}"""),
                Arguments.of("dayScheme outside the catalog", """{"dayScheme": "ghost"}"""),
                Arguments.of("nightScheme outside the catalog", """{"nightScheme": "ghost"}"""),
                Arguments.of("one valid and one invalid field", """{"scheme": "graphite", "uiScale": "XL"}"""),
            )

        @JvmStatic
        fun lenientFiles(): Stream<Arguments> =
            Stream.of(
                Arguments.of("empty data", "{}", AppearanceSettings()),
                Arguments.of(
                    "one field",
                    """{"scheme": "graphite"}""",
                    AppearanceSettings(scheme = SchemeId("graphite")),
                ),
                Arguments.of(
                    "unknown keys",
                    """{"terminal": {"size": 14}, "customSchemes": [], "density": "COMPACT"}""",
                    AppearanceSettings(density = ListDensity.COMPACT),
                ),
            )
    }
}
