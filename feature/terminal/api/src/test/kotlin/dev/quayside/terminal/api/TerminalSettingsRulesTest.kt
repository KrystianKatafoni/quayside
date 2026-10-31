package dev.quayside.terminal.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

/** Tables 1 to 3 of docs/plans/AM-508.md (FR-T7). */
class TerminalSettingsRulesTest {
    private val project = Path.of("/projects/quayside")
    private val home = Path.of("/Users/test")

    @Test
    fun `defaults are the docs 06 values with option as meta off`() {
        TerminalSettings() shouldBe
            TerminalSettings(
                shell = ShellProfile.LOGIN_DEFAULT,
                startDirectory = StartDirectory.SESSION_WORKTREE,
                fontFamily = null,
                fontSize = 13,
                scrollbackLines = 100_000,
                ligatures = true,
                optionAsMeta = false,
            )
    }

    @Test
    fun `default settings are valid and unchanged`() {
        TerminalSettingsRules.validate(TerminalSettings()) shouldBe Outcome.Ok(TerminalSettings())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validations")
    fun `validate follows table 1`(
        @Suppress("UNUSED_PARAMETER") name: String,
        input: TerminalSettings,
        expected: Outcome<TerminalSettings, TerminalSettingsError.Invalid>,
    ) {
        TerminalSettingsRules.validate(input) shouldBe expected
    }

    @Test
    fun `first bad field wins`() {
        val both = TerminalSettings(fontFamily = "x".repeat(81), fontSize = 7, scrollbackLines = 5)

        TerminalSettingsRules.validate(both) shouldBe
            Outcome.Err(TerminalSettingsError.Invalid(TerminalSettingsField.FONT_FAMILY))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("startDirs")
    fun `startDirOf follows table 2`(
        @Suppress("UNUSED_PARAMETER") name: String,
        choice: StartDirectory,
        sessionDir: Path?,
        expected: Path,
    ) {
        TerminalSettingsRules.startDirOf(choice, project, sessionDir, home) shouldBe expected
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("labels")
    fun `shellLabelOf follows table 3`(
        profile: ShellProfile,
        loginShell: Path,
        expected: String,
    ) {
        TerminalSettingsRules.shellLabelOf(profile, loginShell) shouldBe expected
    }

    @Test
    fun `explicit profiles ignore the login shell and login default uses its file name`() {
        TerminalSettingsRules.shellLabelOf(ShellProfile.ZSH, Path.of("/opt/homebrew/bin/fish")) shouldBe "zsh"
        TerminalSettingsRules.shellLabelOf(ShellProfile.LOGIN_DEFAULT, Path.of("/opt/homebrew/bin/fish")) shouldBe
            "fish"
    }

    companion object {
        private fun invalid(field: TerminalSettingsField) = Outcome.Err(TerminalSettingsError.Invalid(field))

        @JvmStatic
        @Suppress("LongMethod")
        fun validations(): Stream<Arguments> {
            val base = TerminalSettings()
            return Stream.of(
                arguments("defaults", base, Outcome.Ok(base)),
                arguments("font size 7", base.copy(fontSize = 7), invalid(TerminalSettingsField.FONT_SIZE)),
                arguments("font size 8", base.copy(fontSize = 8), Outcome.Ok(base.copy(fontSize = 8))),
                arguments("font size 32", base.copy(fontSize = 32), Outcome.Ok(base.copy(fontSize = 32))),
                arguments("font size 33", base.copy(fontSize = 33), invalid(TerminalSettingsField.FONT_SIZE)),
                arguments(
                    "scrollback 999",
                    base.copy(scrollbackLines = 999),
                    invalid(TerminalSettingsField.SCROLLBACK_LINES),
                ),
                arguments(
                    "scrollback 1000",
                    base.copy(scrollbackLines = 1_000),
                    Outcome.Ok(base.copy(scrollbackLines = 1_000)),
                ),
                arguments(
                    "scrollback 100000",
                    base.copy(scrollbackLines = 100_000),
                    Outcome.Ok(base.copy(scrollbackLines = 100_000)),
                ),
                arguments(
                    "scrollback 100001",
                    base.copy(scrollbackLines = 100_001),
                    invalid(TerminalSettingsField.SCROLLBACK_LINES),
                ),
                arguments("blank font family", base.copy(fontFamily = "  "), Outcome.Ok(base.copy(fontFamily = null))),
                arguments(
                    "padded font family",
                    base.copy(fontFamily = " Menlo "),
                    Outcome.Ok(base.copy(fontFamily = "Menlo")),
                ),
                arguments(
                    "font family of 81 characters",
                    base.copy(fontFamily = "x".repeat(81)),
                    invalid(TerminalSettingsField.FONT_FAMILY),
                ),
            )
        }

        @JvmStatic
        fun startDirs(): Stream<Arguments> =
            Stream.of(
                arguments(
                    "session worktree with a session dir",
                    StartDirectory.SESSION_WORKTREE,
                    Path.of("/w"),
                    Path.of("/w"),
                ),
                arguments(
                    "session worktree without a session dir",
                    StartDirectory.SESSION_WORKTREE,
                    null,
                    Path.of("/projects/quayside"),
                ),
                arguments(
                    "project ignores the session dir",
                    StartDirectory.PROJECT,
                    Path.of("/w"),
                    Path.of("/projects/quayside"),
                ),
                arguments("home ignores the session dir", StartDirectory.HOME, Path.of("/w"), Path.of("/Users/test")),
            )

        @JvmStatic
        fun labels(): Stream<Arguments> =
            Stream.of(
                arguments(ShellProfile.LOGIN_DEFAULT, Path.of("/bin/zsh"), "zsh"),
                arguments(ShellProfile.BASH, Path.of("/bin/zsh"), "bash"),
                arguments(ShellProfile.FISH, Path.of("/bin/zsh"), "fish"),
            )
    }
}
