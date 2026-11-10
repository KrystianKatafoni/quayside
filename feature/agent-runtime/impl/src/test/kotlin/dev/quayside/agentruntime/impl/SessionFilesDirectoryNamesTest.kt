package dev.quayside.agentruntime.impl

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Tables 2 and 3 of the AM-311 plan: directory names of Claude Code and the candidates for a project. */
@Suppress("UnusedParameter")
internal class SessionFilesDirectoryNamesTest {
    // --- 2. dirNameOf ---

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("directoryNames")
    fun `every character outside A-Z a-z 0-9 becomes a dash`(
        cwd: String,
        expected: String,
    ) {
        SessionFiles.dirNameOf(Path.of(cwd)) shouldBe expected
    }

    @Test
    fun `encoding is lossy, a dash and a slash give the same name`() {
        SessionFiles.dirNameOf(Path.of("/a/b-c")) shouldBe SessionFiles.dirNameOf(Path.of("/a/b/c"))
    }

    // Measured on Claude Code 2.1.322 (AM-311 M3, test directories): one dash per UTF-16 unit.
    @Test
    fun `non-ASCII letters become one dash each`() {
        SessionFiles.dirNameOf(Path.of("/a/zażółć gęślą")) shouldBe "-a-za-----g--l-"
    }

    @Test
    fun `an emoji is two UTF-16 units and gives two dashes`() {
        SessionFiles.dirNameOf(Path.of("/a/e😀d")) shouldBe "-a-e--d"
    }

    // Measured: a name over 200 characters is cut to 200, then "-" and base 36 of |String.hashCode| of the path.
    @Test
    fun `a path over 200 characters is cut and gets a hash`() {
        val path = "/" + List(100) { "ab" }.joinToString("/")
        val prefix = ("-" + List(100) { "ab" }.joinToString("-")).take(200)

        SessionFiles.dirNameOf(Path.of(path)) shouldBe "$prefix-wjfgg0"
    }

    @Test
    fun `the limit is 200 characters, 200 stays whole and 201 gets a hash`() {
        val at200 = "/Users/test/" + "x".repeat(188)
        val at201 = "/Users/test/" + "x".repeat(189)

        SessionFiles.dirNameOf(Path.of(at200)) shouldBe "-Users-test-" + "x".repeat(188)
        SessionFiles.dirNameOf(Path.of(at201)) shouldBe "-Users-test-" + "x".repeat(188) + "-yvpabz"
    }

    @Test
    fun `candidates of a project with a name over 200 characters match by the first 200 characters`() {
        val project = Path.of("/" + List(100) { "ab" }.joinToString("/"))
        val prefix = ("-" + List(100) { "ab" }.joinToString("-")).take(200)
        val names = listOf("$prefix-wjfgg0", "$prefix-other7", "-Users-test-p")

        SessionFiles.candidates(project, names) shouldBe listOf("$prefix-wjfgg0", "$prefix-other7")
    }

    // --- 3. candidates ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("candidateTables")
    fun `candidates are the equal name or prefix plus dash`(
        name: String,
        project: String,
        dirNames: List<String>,
        expected: List<String>,
    ) {
        SessionFiles.candidates(Path.of(project), dirNames) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun directoryNames(): List<Arguments> =
            listOf(
                arguments("/Users/test/projects/quayside", "-Users-test-projects-quayside"),
                arguments("/a/b.c_d e", "-a-b-c-d-e"),
                arguments("/A1/b2", "-A1-b2"),
                arguments("/a/b-c", "-a-b-c"),
                arguments("/", "-"),
            )

        @JvmStatic
        @Suppress("LongMethod")
        fun candidateTables(): List<Arguments> =
            listOf(
                arguments(
                    "equal name, subfolder and sibling with prefix",
                    "/Users/test/p",
                    listOf("-Users-test-p", "-Users-test-p-soft", "-Users-test-p-old"),
                    listOf("-Users-test-p", "-Users-test-p-soft", "-Users-test-p-old"),
                ),
                arguments(
                    "longer name, parent and other project",
                    "/Users/test/p",
                    listOf("-Users-test-pp", "-Users-test", "-Users-test-q"),
                    emptyList<String>(),
                ),
                arguments(
                    "order of the input is kept and other names are dropped",
                    "/Users/test/p",
                    listOf("-Users-test-q", "-Users-test-p-b", "-Users-test-pp", "-Users-test-p", "-Users-test-p-a"),
                    listOf("-Users-test-p-b", "-Users-test-p", "-Users-test-p-a"),
                ),
                arguments(
                    "special characters in the project path are encoded first",
                    "/Users/test/my.app",
                    listOf("-Users-test-my-app", "-Users-test-my-app-sub", "-Users-test-my.app", "-Users-test-my-appx"),
                    listOf("-Users-test-my-app", "-Users-test-my-app-sub"),
                ),
                arguments(
                    "case matters",
                    "/Users/test/p",
                    listOf("-users-test-p", "-Users-test-P"),
                    emptyList<String>(),
                ),
                arguments("empty list", "/Users/test/p", emptyList<String>(), emptyList<String>()),
            )
    }
}
