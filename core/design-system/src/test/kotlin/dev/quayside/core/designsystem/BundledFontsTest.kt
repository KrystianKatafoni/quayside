package dev.quayside.core.designsystem

import androidx.compose.ui.text.font.FontListFontFamily
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Font
import java.awt.font.FontRenderContext
import java.net.URI
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Locale
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * The OFL fonts of the 22 families of the 22 typographies (ADR 0021, AM-802 and AM-805), read from the classpath
 * without rendering anything. The font files are listed from `fonts/` itself, so a file that is added without a
 * licence, sources entry or glyph coverage fails here. Layout: `fonts/<directory>/<file>.ttf`,
 * `fonts/<directory>/OFL.txt`, `fonts/SOURCES.md`.
 */
class BundledFontsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("expectedDirectories")
    fun `font file exists next to its OFL licence`(directory: String) {
        val dir = fontsRoot().resolve(directory)

        dir.isDirectory() shouldBe true
        dir.resolve("OFL.txt").let { Files.isRegularFile(it) && it.readText().isNotBlank() } shouldBe true
        Files.list(dir).use { stream -> stream.anyMatch { it.extension == "ttf" } } shouldBe true
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fontFiles")
    fun `covers polish letters`(path: Path) {
        load(path).canDisplayUpTo("ąćęłńóśźżĄĆĘŁŃÓŚŹŻ") shouldBe -1
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fontFiles")
    fun `covers code characters`(path: Path) {
        load(path).canDisplayUpTo("0O1lI=>!=") shouldBe -1
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fontFiles")
    fun `0 O 1 l I have distinct glyphs`(path: Path) {
        val text = "0O1lI"
        val glyphs = load(path).createGlyphVector(FontRenderContext(null, false, false), text)

        val codes = (0 until text.length).map { glyphs.getGlyphCode(it) }

        codes.distinct().size shouldBe text.length
    }

    @Test
    fun `families match the typographies of docs 08`() {
        AmFonts.families shouldBe ALL_FAMILIES
        AmFonts.families.size shouldBe 22
        ALL_FAMILIES.forEach { (AmFonts.family(it) != null) shouldBe true }
        AmFonts.family("SF Pro") shouldBe null
        AmFonts.family("Menlo") shouldBe null
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "Plus Jakarta Sans | 400 600",
        "Outfit | 400 600",
        "Courier Prime | 400 700",
        "Inter | 400 600",
        "DM Sans | 400 600",
        "Roboto Mono | 400 600",
        "Source Code Pro | 400 600",
        "PT Mono | 400",
        delimiter = '|',
    )
    fun `new families offer the planned weights`(
        name: String,
        weights: String,
    ) {
        val family = AmFonts.family(name).shouldBeInstanceOf<FontListFontFamily>()

        family.fonts.map { it.weight.weight }.sorted() shouldBe weights.split(' ').map { it.toInt() }
    }

    @Test
    fun `sources header names 22 typographies`() {
        sourcesPath().readText() shouldContain "22 typografii"
    }

    @Test
    fun `every family has a font directory`() {
        val directories = Files.list(fontsRoot()).use { s -> s.filter { it.isDirectory() }.map { it.name }.toList() }

        directories shouldContainAll EXPECTED_DIRECTORIES
        directories.size shouldBe EXPECTED_DIRECTORIES.size
    }

    /**
     * Each row of SOURCES.md for a bundled file (font or OFL.txt) names `directory/file` in backticks, then the SHA-256
     * and the size in their own columns; one commit pins all URLs.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("bundledFiles")
    fun `sources list every bundled file with sha256 and size`(path: Path) {
        val bytes = path.readBytes()
        val sha =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(Locale.ROOT, it) }
        val relative = fontsRoot().relativize(path).invariantSeparatorsPathString

        val row = sourcesLines().firstOrNull { it.startsWith("| `$relative` |") }.shouldNotBeNull()

        val columns =
            row
                .trim()
                .trim('|')
                .split('|')
                .map { it.trim() }
        columns[1] shouldBe sha
        columns[2] shouldBe bytes.size.toString()
    }

    @Test
    fun `sources have one row per bundled file and nothing else`() {
        val bundled = bundledFiles().map { fontsRoot().relativize(it).invariantSeparatorsPathString }.toList()
        val rows =
            sourcesLines()
                .filter { it.startsWith("| `") }
                .map { it.substringAfter("| `").substringBefore("`") }

        rows.sorted() shouldBe bundled.sorted()
        bundled.count { it.endsWith("OFL.txt") } shouldBe EXPECTED_DIRECTORIES.size
    }

    @Test
    fun `every resource path of AmFonts exists on the classpath`() {
        val loader = BundledFontsTest::class.java.classLoader

        AmFonts.resourcePaths.isNotEmpty() shouldBe true
        AmFonts.resourcePaths.forEach { path ->
            val exists = loader.getResourceAsStream(path)?.use { true } ?: false
            withClue(path) { exists shouldBe true }
        }
    }

    @Test
    fun `resource paths of AmFonts are exactly the bundled ttf files`() {
        val files =
            fontFiles()
                .map { "fonts/" + fontsRoot().relativize(it).invariantSeparatorsPathString }
                .toList()

        AmFonts.resourcePaths.sorted() shouldBe files.sorted()
        AmFonts.resourcePaths.distinct().size shouldBe AmFonts.resourcePaths.size
    }

    @Test
    fun `sources pin one commit of google fonts`() {
        val text = sourcesPath().readText()

        Regex("raw\\.githubusercontent\\.com/google/fonts/([0-9a-f]{40})/")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()
            .size shouldBe 1
    }

    private fun sourcesLines(): List<String> = sourcesPath().readText().lines()

    companion object {
        private val EXPECTED_DIRECTORIES =
            listOf(
                "ibmplexsans",
                "ibmplexmono",
                "geist",
                "geistmono",
                "instrumentsans",
                "dmmono",
                "jetbrainsmono",
                "chakrapetch",
                "firacode",
                "spacegrotesk",
                "spacemono",
                "atkinsonhyperlegiblenext",
                "atkinsonhyperlegiblemono",
                "martianmono",
                "plusjakartasans",
                "outfit",
                "courierprime",
                "inter",
                "dmsans",
                "robotomono",
                "sourcecodepro",
                "ptmono",
            )

        private val ALL_FAMILIES =
            setOf(
                "IBM Plex Sans",
                "IBM Plex Mono",
                "Geist",
                "Geist Mono",
                "Instrument Sans",
                "DM Mono",
                "JetBrains Mono",
                "Chakra Petch",
                "Fira Code",
                "Space Grotesk",
                "Space Mono",
                "Atkinson Hyperlegible Next",
                "Atkinson Hyperlegible Mono",
                "Martian Mono",
                "Plus Jakarta Sans",
                "Outfit",
                "Courier Prime",
                "Inter",
                "DM Sans",
                "Roboto Mono",
                "Source Code Pro",
                "PT Mono",
            )

        private fun fontsRoot(): Path {
            val url =
                checkNotNull(BundledFontsTest::class.java.classLoader.getResource("fonts")) { "no fonts/ resource" }
            return pathOf(url.toURI())
        }

        /**
         * With `java-test-fixtures` the test classpath carries the main output as a jar (AM-806), so the folder may
         * live in a zip file system; open it once and reuse it.
         */
        private fun pathOf(uri: URI): Path {
            if (uri.scheme != "jar") return Paths.get(uri)
            val zip =
                try {
                    FileSystems.getFileSystem(uri)
                } catch (_: FileSystemNotFoundException) {
                    FileSystems.newFileSystem(uri, emptyMap<String, Any>())
                }
            return zip.getPath(uri.schemeSpecificPart.substringAfter("!"))
        }

        private fun sourcesPath(): Path = fontsRoot().resolve("SOURCES.md")

        private fun load(path: Path): Font = Files.newInputStream(path).use { Font.createFont(Font.TRUETYPE_FONT, it) }

        @JvmStatic
        fun expectedDirectories(): Stream<String> = EXPECTED_DIRECTORIES.stream()

        @JvmStatic
        fun bundledFiles(): Stream<Path> {
            val files =
                Files.walk(fontsRoot()).use { s ->
                    s.filter { it.extension == "ttf" || it.name == "OFL.txt" }.sorted().toList()
                }
            return files.stream()
        }

        @JvmStatic
        fun fontFiles(): Stream<Path> {
            val root =
                BundledFontsTest::class.java.classLoader
                    .getResource("fonts")
                    ?.let { pathOf(it.toURI()) }
            val files =
                root
                    ?.let { r -> Files.walk(r).use { s -> s.filter { it.extension == "ttf" }.sorted().toList() } }
                    .orEmpty()
            check(files.isNotEmpty()) { "no .ttf files under resources/fonts (implementer downloads them, ADR 0021)" }
            return files.stream()
        }
    }
}
