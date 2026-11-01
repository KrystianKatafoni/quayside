package dev.quayside.terminal.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsError
import dev.quayside.terminal.api.TerminalSettingsField
import dev.quayside.terminal.api.TerminalSettingsIssue
import dev.quayside.terminal.api.TerminalSettingsRepo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

/** The logic of the settings dialog over a fake repo (FR-T7, plan AM-508 part B); time is the test scheduler's. */
class TerminalSettingsControllerTest {
    private val saved =
        TerminalSettings(
            shell = ShellProfile.FISH,
            startDirectory = StartDirectory.HOME,
            fontFamily = "Menlo",
            fontSize = 16,
            scrollbackLines = 5_000,
            ligatures = false,
            optionAsMeta = true,
            bellNotification = false,
        )

    private fun TestScope.controllerOver(repo: TerminalSettingsRepo) = TerminalSettingsController(repo, backgroundScope)

    @Test
    fun `closed until opened`() =
        runTest {
            val controller = controllerOver(FakeTerminalSettingsRepo(saved))

            controller.state.value.shouldBeNull()
        }

    @Test
    fun `draft starts from saved settings`() =
        runTest {
            val controller = controllerOver(FakeTerminalSettingsRepo(saved))

            act { controller.open() }

            val state = controller.state.value
            state?.draft shouldBe
                SettingsDraft(
                    shell = ShellProfile.FISH,
                    startDirectory = StartDirectory.HOME,
                    fontFamily = "Menlo",
                    fontSize = "16",
                    scrollbackLines = "5000",
                    ligatures = false,
                    optionAsMeta = true,
                    bellNotification = false,
                )
            state?.invalid.shouldBeNull()
            state?.storageFailed shouldBe false
        }

    @Test
    fun `draft of the default font family has a blank family`() =
        runTest {
            val controller = controllerOver(FakeTerminalSettingsRepo())

            act { controller.open() }

            controller.state.value
                ?.draft
                ?.fontFamily shouldBe ""
        }

    @Test
    fun `save stores the draft and closes the dialog`() =
        runTest {
            val repo = FakeTerminalSettingsRepo(saved)
            val controller = controllerOver(repo)
            act { controller.open() }

            act { controller.edit { it.copy(fontSize = "20", fontFamily = "  ", shell = ShellProfile.ZSH) } }
            act { controller.save() }

            controller.state.value.shouldBeNull()
            repo.saves shouldBe 1
            repo.settings.value shouldBe saved.copy(fontSize = 20, fontFamily = null, shell = ShellProfile.ZSH)
        }

    @Test
    fun `FR-T7 bell notification can be switched off and saved`() =
        runTest {
            val repo = FakeTerminalSettingsRepo(saved.copy(bellNotification = true))
            val controller = controllerOver(repo)
            act { controller.open() }

            act { controller.edit { it.copy(bellNotification = false) } }
            act { controller.save() }

            repo.settings.value.bellNotification shouldBe false
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDrafts")
    fun `save shows the invalid field`(
        @Suppress("UNUSED_PARAMETER") name: String,
        change: (SettingsDraft) -> SettingsDraft,
        field: TerminalSettingsField,
    ) = runTest {
        val repo = FakeTerminalSettingsRepo(saved)
        val controller = controllerOver(repo)
        act { controller.open() }
        act { controller.edit(change) }

        act { controller.save() }

        controller.state.value?.invalid shouldBe field
        controller.state.value?.storageFailed shouldBe false
        repo.saves shouldBe 0
        repo.settings.value shouldBe saved
    }

    @Test
    fun `editing after a refused save clears the invalid field and keeps the draft`() =
        runTest {
            val controller = controllerOver(FakeTerminalSettingsRepo(saved))
            act { controller.open() }
            act { controller.edit { it.copy(fontSize = "7") } }
            act { controller.save() }

            act { controller.edit { it.copy(fontSize = "9") } }

            controller.state.value
                ?.invalid
                .shouldBeNull()
            controller.state.value
                ?.draft
                ?.fontSize shouldBe "9"
        }

    @Test
    fun `storage failure keeps the dialog open`() =
        runTest {
            val repo = FakeTerminalSettingsRepo(saved).apply { failure = true }
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(fontSize = "20") } }

            act { controller.save() }

            val state = controller.state.value
            state?.storageFailed shouldBe true
            state?.invalid.shouldBeNull()
            state?.draft?.fontSize shouldBe "20"
            repo.settings.value shouldBe saved
        }

    @Test
    fun `save after a storage failure tries again`() =
        runTest {
            val repo = FakeTerminalSettingsRepo(saved).apply { failure = true }
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(fontSize = "20") } }
            act { controller.save() }
            repo.failure = false

            act { controller.save() }

            controller.state.value.shouldBeNull()
            repo.settings.value.fontSize shouldBe 20
        }

    @Test
    fun `cancel discards the draft`() =
        runTest {
            val repo = FakeTerminalSettingsRepo(saved)
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(fontSize = "20") } }

            act { controller.cancel() }

            controller.state.value.shouldBeNull()
            repo.saves shouldBe 0
            repo.settings.value shouldBe saved
            act { controller.open() }
            controller.state.value
                ?.draft
                ?.fontSize shouldBe "16"
        }

    @Test
    fun `a second save while the write is pending does not write again`() =
        runTest {
            val repo = HangingRepo(FakeTerminalSettingsRepo(saved))
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.save() }

            act { controller.save() }
            repo.release.complete(Unit)
            act { }

            repo.updates shouldBe 1
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `a write that ends after cancel and open does not touch the new draft`() =
        runTest {
            val repo = HangingRepo(FakeTerminalSettingsRepo(saved))
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.save() }
            act { controller.cancel() }
            act { controller.open() }
            act { controller.edit { it.copy(fontSize = "20") } }

            repo.release.complete(Unit)
            act { }

            controller.state.value
                .shouldNotBeNull()
                .draft.fontSize shouldBe "20"
        }

    @Test
    fun `save works again after a write that was dropped by cancel`() =
        runTest {
            val repo = HangingRepo(FakeTerminalSettingsRepo(saved))
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.save() }
            act { controller.cancel() }
            act { controller.open() }
            repo.release.complete(Unit)

            act { controller.save() }

            repo.updates shouldBe 2
            controller.state.value.shouldBeNull()
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TerminalSettingsIssue::class)
    fun `recovered file shows the notice`(issue: TerminalSettingsIssue) =
        runTest {
            val controller = controllerOver(FakeTerminalSettingsRepo(saved, startIssue = issue))

            act { controller.open() }

            controller.state.value?.notice shouldBe issue
        }

    @Test
    fun `no notice when the file was fine`() =
        runTest {
            val controller = controllerOver(FakeTerminalSettingsRepo(saved))

            act { controller.open() }

            controller.state.value
                ?.notice
                .shouldBeNull()
        }

    companion object {
        @JvmStatic
        fun invalidDrafts(): List<Arguments> =
            listOf(
                draft("font size below the range", TerminalSettingsField.FONT_SIZE) { it.copy(fontSize = "7") },
                draft("font size above the range", TerminalSettingsField.FONT_SIZE) { it.copy(fontSize = "33") },
                draft("font size not a number", TerminalSettingsField.FONT_SIZE) { it.copy(fontSize = "abc") },
                draft("font size empty", TerminalSettingsField.FONT_SIZE) { it.copy(fontSize = "") },
                draft("scrollback below the range", TerminalSettingsField.SCROLLBACK_LINES) {
                    it.copy(scrollbackLines = "999")
                },
                draft("scrollback above the range", TerminalSettingsField.SCROLLBACK_LINES) {
                    it.copy(scrollbackLines = "100001")
                },
                draft("scrollback not a number", TerminalSettingsField.SCROLLBACK_LINES) {
                    it.copy(scrollbackLines = "many")
                },
                draft(
                    "font family too long",
                    TerminalSettingsField.FONT_FAMILY,
                ) { it.copy(fontFamily = "x".repeat(81)) },
                draft("font family is checked before the font size", TerminalSettingsField.FONT_FAMILY) {
                    it.copy(fontFamily = "x".repeat(81), fontSize = "7")
                },
                draft("font size is checked before the scrollback", TerminalSettingsField.FONT_SIZE) {
                    it.copy(fontSize = "7", scrollbackLines = "5")
                },
            )

        private fun draft(
            name: String,
            field: TerminalSettingsField,
            change: (SettingsDraft) -> SettingsDraft,
        ): Arguments = arguments(name, change, field)
    }
}

/** A repo whose writes wait for [release], to test a save that is still running. */
private class HangingRepo(
    private val inner: FakeTerminalSettingsRepo,
) : TerminalSettingsRepo by inner {
    val release = CompletableDeferred<Unit>()
    var updates: Int = 0
        private set

    override suspend fun update(
        change: (TerminalSettings) -> TerminalSettings,
    ): Outcome<TerminalSettings, TerminalSettingsError> {
        updates++
        release.await()
        return inner.update(change)
    }
}
