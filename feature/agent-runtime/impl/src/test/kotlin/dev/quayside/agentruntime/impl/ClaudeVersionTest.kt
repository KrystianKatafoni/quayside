package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.RuntimeRequirements.MIN_CLAUDE_VERSION
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** [ClaudeVersion]: reading `claude --version` and comparing with the minimum (plan AM-204). */
class ClaudeVersionTest {
    @ParameterizedTest(name = "[{0}] -> {1}")
    @MethodSource("versions")
    fun `parse reads the version at the start of the first non-blank line`(
        stdout: String,
        expected: String,
    ) {
        ClaudeVersion.parse(stdout)?.toString() shouldBe expected
    }

    @ParameterizedTest(name = "[{0}] -> {1}.{2}.{3}")
    @MethodSource("suffixed")
    fun `parse ignores a prerelease or build suffix`(
        stdout: String,
        major: Int,
        minor: Int,
        patch: Int,
    ) {
        ClaudeVersion.parse(stdout) shouldBe ClaudeVersion(major, minor, patch)
    }

    @ParameterizedTest(name = "[{0}] -> null")
    @MethodSource("notVersions")
    fun `parse returns null when there is no dotted numeric version`(stdout: String) {
        ClaudeVersion.parse(stdout) shouldBe null
    }

    @ParameterizedTest(name = "{0} {1} minimum")
    @MethodSource("comparisons")
    fun `compareTo orders numerically part by part`(
        version: String,
        relation: String,
    ) {
        val parsed = ClaudeVersion.parse(version).shouldNotBeNull()

        val atLeastMinimum = parsed >= ClaudeVersion.MINIMUM

        atLeastMinimum shouldBe (relation == ">=")
    }

    @Test
    fun `MINIMUM parses from the required version`() {
        ClaudeVersion.MINIMUM shouldBe ClaudeVersion(2, 1, 139)
        ClaudeVersion.MINIMUM.toString() shouldBe MIN_CLAUDE_VERSION
        ClaudeVersion.parse(MIN_CLAUDE_VERSION) shouldBe ClaudeVersion.MINIMUM
    }

    @Test
    fun `toString is major dot minor dot patch`() {
        ClaudeVersion(2, 1, 302).toString() shouldBe "2.1.302"
    }

    @Test
    fun `compareTo is zero only for the same version`() {
        ClaudeVersion(2, 1, 139).compareTo(ClaudeVersion(2, 1, 139)) shouldBe 0
        ClaudeVersion(2, 1, 1390) shouldBeGreaterThan ClaudeVersion(2, 1, 139)
        ClaudeVersion(1, 99, 999) shouldBeLessThan ClaudeVersion(2, 0, 0)
        ClaudeVersion(2, 10, 0) shouldBeGreaterThan ClaudeVersion(2, 9, 999)
    }

    companion object {
        @JvmStatic
        fun versions(): List<Arguments> =
            listOf(
                arguments("2.1.302 (Claude Code)", "2.1.302"),
                arguments("2.1.302 (Claude Code)\n", "2.1.302"),
                arguments("2.1.139\n", "2.1.139"),
                arguments("\n  2.1.140 (Claude Code)", "2.1.140"),
            )

        @JvmStatic
        fun suffixed(): List<Arguments> =
            listOf(
                arguments("2.1.139-beta.1", 2, 1, 139),
                arguments("3.0.0+abc", 3, 0, 0),
            )

        @JvmStatic
        fun notVersions(): List<String> =
            listOf(
                "",
                "  \n \n",
                "Claude Code",
                "v2.1.302",
                "2.1",
                "2.1.x",
                "2.1.302.4",
                "99999999999.1.1",
                "this is not json {",
                "Claude Code\n2.1.302",
            )

        // The fourth case would compare as 2.1.139 if parts were compared as text.
        @JvmStatic
        fun comparisons(): List<Arguments> =
            listOf(
                arguments("2.1.139", ">="),
                arguments("2.1.140", ">="),
                arguments("2.1.99", "<"),
                arguments("2.1.1390", ">="),
                arguments("2.2.0", ">="),
                arguments("1.9.999", "<"),
                arguments("3.0.0", ">="),
            )
    }
}
