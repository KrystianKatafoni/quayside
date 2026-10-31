package dev.quayside.terminal.impl

import dev.quayside.terminal.api.ShellProfile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Which executable a profile means (ADR 0016, decision 4; docs/plans/AM-508.md). */
class ShellProfilesTest {
    private val login = Path.of("/usr/local/bin/xonsh")
    private val homebrewFish = Path.of("/opt/homebrew/bin/fish")
    private val usrLocalFish = Path.of("/usr/local/bin/fish")

    @Test
    fun `login default is the module shell`() {
        ShellProfiles.resolve(ShellProfile.LOGIN_DEFAULT, login) { false } shouldBe login
    }

    @Test
    fun `zsh and bash have fixed paths`() {
        ShellProfiles.resolve(ShellProfile.ZSH, login) { true } shouldBe Path.of("/bin/zsh")
        ShellProfiles.resolve(ShellProfile.BASH, login) { true } shouldBe Path.of("/bin/bash")
    }

    @Test
    fun `fish prefers homebrew`() {
        ShellProfiles.resolve(ShellProfile.FISH, login) { true } shouldBe homebrewFish
    }

    @Test
    fun `fish falls back to usr local`() {
        ShellProfiles.resolve(ShellProfile.FISH, login) { it == usrLocalFish } shouldBe usrLocalFish
    }

    @Test
    fun `missing fish resolves to null`() {
        ShellProfiles.resolve(ShellProfile.FISH, login) { false } shouldBe null
    }
}
