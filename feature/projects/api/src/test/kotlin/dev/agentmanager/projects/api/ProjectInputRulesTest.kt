package dev.agentmanager.projects.api

import dev.agentmanager.core.domain.Outcome
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class ProjectInputRulesTest {
    private val dir = Path.of("/a/agent-manager")
    private val maxLength = ProjectInputRules.MAX_DISPLAY_NAME_LENGTH

    private fun invalid(problem: NameProblem) = Outcome.Err(ProjectError.InvalidDisplayName(problem))

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "/a/./b/../c, /a/c",
        "/Users/test/projects/agent-manager, /Users/test/projects/agent-manager",
        "/a/b/, /a/b",
        "/, /",
    )
    fun `absolute directory is normalized`(
        input: String,
        expected: String,
    ) {
        ProjectInputRules.normalizeDir(Path.of(input)) shouldBe Outcome.Ok(Path.of(expected))
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["projects/a", "./a", "../a", ""])
    fun `relative directory returns NotAbsolute`(input: String) {
        val relative = Path.of(input)

        ProjectInputRules.normalizeDir(relative) shouldBe Outcome.Err(ProjectError.NotAbsolute(relative))
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "/a/agent-manager, agent-manager",
        "/Users/test/My Project, My Project",
        "/, /",
    )
    fun `missing name falls back to the directory name`(
        directory: String,
        expected: String,
    ) {
        ProjectInputRules.displayName(null, Path.of(directory)) shouldBe Outcome.Ok(expected)
    }

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource(
        "'  Plant  ',Plant",
        "Plant,Plant",
        "'Agent Manager ',Agent Manager",
        ignoreLeadingAndTrailingWhitespace = false,
    )
    fun `name is trimmed`(
        raw: String,
        expected: String,
    ) {
        ProjectInputRules.displayName(raw, dir) shouldBe Outcome.Ok(expected)
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["", " ", "   "])
    fun `blank name returns BLANK`(raw: String) {
        ProjectInputRules.displayName(raw, dir) shouldBe invalid(NameProblem.BLANK)
    }

    @Test
    fun `name of the maximum length is accepted`() {
        val name = "x".repeat(maxLength)

        ProjectInputRules.displayName(name, dir) shouldBe Outcome.Ok(name)
    }

    @Test
    fun `name one character over the maximum returns TOO_LONG`() {
        ProjectInputRules.displayName("x".repeat(maxLength + 1), dir) shouldBe invalid(NameProblem.TOO_LONG)
    }

    @Test
    fun `length is counted after trimming`() {
        val name = "x".repeat(maxLength)

        ProjectInputRules.displayName("  $name  ", dir) shouldBe Outcome.Ok(name)
    }

    @ParameterizedTest(name = "{index}")
    @ValueSource(strings = ["a\nb", "a\u0000b", "a\tb", "a\rb", "a\u001Bb"])
    fun `control characters return CONTROL_CHARACTERS`(raw: String) {
        ProjectInputRules.displayName(raw, dir) shouldBe invalid(NameProblem.CONTROL_CHARACTERS)
    }

    @ParameterizedTest(name = "U+{0}")
    @ValueSource(strings = ["202E", "200F", "FEFF", "2028", "2029", "D800", "DC00"])
    fun `invisible format characters, separators and lone surrogates return CONTROL_CHARACTERS`(hex: String) {
        val raw = "a" + hex.toInt(HEX).toChar() + "b"

        ProjectInputRules.displayName(raw, dir) shouldBe invalid(NameProblem.CONTROL_CHARACTERS)
    }

    @Test
    fun `emoji written as a surrogate pair is not a control character`() {
        ProjectInputRules.displayName("Plant \uD83C\uDF31", dir) shouldBe Outcome.Ok("Plant \uD83C\uDF31")
    }

    @Test
    fun `emoji joined with a zero-width joiner is not a control character`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"

        ProjectInputRules.displayName("Dom $family", dir) shouldBe Outcome.Ok("Dom $family")
    }

    @Test
    fun `invisible characters in the directory name become spaces`() {
        // Built from strings: a path with them may not be representable under a non-UTF-8 sun.jnu.encoding.
        val cases =
            mapOf("a\u202Eb" to "a b", "a\u2028b" to "a b", "a\u2029b" to "a b", "\uFEFFa" to "a", "a\uD800b" to "a b")

        cases.forEach { (name, expected) ->
            ProjectInputRules.fallbackName(name, "/x/$name") shouldBe expected
        }
    }

    @Test
    fun `length is counted in code points`() {
        val emoji = "\uD83D\uDE00"

        ProjectInputRules.displayName(emoji.repeat(maxLength), dir) shouldBe Outcome.Ok(emoji.repeat(maxLength))
        ProjectInputRules.displayName(emoji.repeat(maxLength + 1), dir) shouldBe invalid(NameProblem.TOO_LONG)
    }

    @ParameterizedTest(name = "{0} characters")
    @ValueSource(ints = [81, 255])
    fun `long directory name is cut with an ellipsis within the limit`(length: Int) {
        val fallback = ProjectInputRules.displayName(null, Path.of("/a/" + "x".repeat(length)))

        fallback shouldBe Outcome.Ok("x".repeat(maxLength - 1) + ELLIPSIS)
    }

    @Test
    fun `directory name of the maximum length is kept`() {
        val name = "x".repeat(maxLength)

        ProjectInputRules.displayName(null, Path.of("/a/$name")) shouldBe Outcome.Ok(name)
    }

    @Test
    fun `cut never splits a surrogate pair`() {
        val emoji = "\uD83D\uDE00"
        val name = "x".repeat(maxLength - 2) + emoji + "yy"

        ProjectInputRules.fallbackName(name, "/a/$name") shouldBe "x".repeat(maxLength - 2) + emoji + ELLIPSIS
        ProjectInputRules.fallbackName(emoji.repeat(maxLength + 1), "/a") shouldBe
            emoji.repeat(maxLength - 1) + ELLIPSIS
    }

    @Test
    fun `control characters in the directory name become spaces`() {
        // NUL cannot be part of a path on Unix, so it is covered only for explicit names.
        val cases = mapOf("/a/a\nb" to "a b", "/a/\ta\rb" to "a b", "/a/x\u001B" to "x")

        cases.forEach { (directory, expected) ->
            ProjectInputRules.displayName(null, Path.of(directory)) shouldBe Outcome.Ok(expected)
        }
    }

    @Test
    fun `blank directory name falls back to the full path`() {
        val cases = mapOf("/a/   " to "/a/", "/   " to "/", "/a/\n" to "/a/")

        cases.forEach { (directory, expected) ->
            ProjectInputRules.displayName(null, Path.of(directory)) shouldBe Outcome.Ok(expected)
        }
    }

    @Test
    fun `long full path fallback is cut like a directory name`() {
        val parent = "/" + "p".repeat(maxLength)

        ProjectInputRules.displayName(null, Path.of("$parent/   ")) shouldBe
            Outcome.Ok(parent.take(maxLength - 1) + ELLIPSIS)
    }

    @Test
    fun `max display name length is 80`() {
        maxLength shouldBe 80
    }

    private companion object {
        const val ELLIPSIS = "\u2026"
        const val HEX = 16
    }
}
