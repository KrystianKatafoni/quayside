package dev.quayside.agentruntime.impl

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

/** Shared checks of fixture directories: every file is in the README and no file holds personal data. */
internal object FixtureAnonymity {
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val shortId = Regex("(?:backgrounded · |session )([0-9a-f]{8})\\b")
    private val foreignUserDir = Regex("/Users/(?!test/)")

    /** Every regular file under [root] except `README.md`. */
    fun fixtureFiles(root: Path): List<Path> =
        Files.walk(root).use { paths ->
            paths.asSequence().filter { it.isRegularFile() && it.name != "README.md" }.toList()
        }

    /** Fails unless the README in [root] names every fixture file as `` `relative/path` ``. */
    fun assertAllListedInReadme(root: Path) {
        val readme = root.resolve("README.md").readText()
        val files = fixtureFiles(root)

        files.shouldNotBeEmpty()
        files.forEach { file ->
            readme shouldContain "`${root.relativize(file).invariantSeparatorsPathString}`"
        }
    }

    /**
     * Fails when a file in [files] holds a home path other than `/Users/test/`, a UUID outside the test range or a
     * short id (`backgrounded · <id>`, `session <id>`) outside the test range.
     */
    fun assertAnonymous(
        root: Path,
        files: List<Path>,
    ) {
        files.shouldNotBeEmpty()
        files.forEach { file ->
            val text = file.readText()
            withClue(root.relativize(file).toString()) {
                text shouldNotContain "/home/"
                text shouldNotContain "/root"
                foreignUserDir.containsMatchIn(text) shouldBe false
                uuid.findAll(text).forEach { it.value shouldStartWith "00000000-0000-4000-8000-" }
                shortId.findAll(text).forEach { it.groupValues[1] shouldStartWith "0000000" }
            }
        }
    }
}
