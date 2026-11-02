package dev.quayside.notifications.ui

import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsError
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.SpeechSettings
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The logic of the notification settings dialog over a fake repo (FR-N1, FR-N2, plan AM-704 part A). */
class NotificationSettingsControllerTest {
    private val saved =
        NotificationSettings(
            popups = false,
            voice = true,
            runningLongMinutes = 45,
            popup = PopupSettings(position = PopupPosition.TOP_LEFT),
            speech = SpeechSettings(rate = 250, readQuestion = false),
        ).withChannel(NotificationKind.TURN_ENDED, NotificationChannel.SOUND, true)

    private fun TestScope.controllerOver(repo: ScriptedRepo) =
        NotificationSettingsController(repo, FakeVoiceCatalog(emptyList()), backgroundScope)

    private fun scripted(
        settings: NotificationSettings = saved,
        hold: Boolean = false,
    ) = ScriptedRepo(FakeNotificationSettingsRepo(settings), hold)

    @Test
    fun `closed until opened`() =
        runTest {
            val controller = controllerOver(scripted())

            controller.state.value.shouldBeNull()
        }

    @Test
    fun `draft starts from saved settings`() =
        runTest {
            val controller = controllerOver(scripted())

            act { controller.open() }

            val state = controller.state.value.shouldNotBeNull()
            state.draft shouldBe draftOf(saved)
            state.draft.popups shouldBe false
            state.draft.runningLongMinutes shouldBe "45"
            state.invalid.shouldBeNull()
            state.saveError.shouldBeNull()
        }

    @Test
    fun `save stores the draft and closes the dialog`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }

            act {
                controller.edit {
                    it.copy(
                        popups = true,
                        matrix = it.matrix + (NotificationKind.FAILED to setOf(NotificationChannel.POPUP)),
                        runningLongMinutes = "90",
                    )
                }
            }
            act { controller.save() }

            controller.state.value.shouldBeNull()
            repo.updates shouldBe 1
            repo.settings.value.popups shouldBe true
            repo.settings.value.runningLongMinutes shouldBe 90
            repo.settings.value.channelsFor(NotificationKind.FAILED) shouldBe setOf(NotificationChannel.POPUP)
        }

    @Test
    fun `save writes back the saved settings with only the edited field changed`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(voice = false) } }

            act { controller.save() }

            repo.settings.value shouldBe saved.copy(voice = false)
        }

    @Test
    fun `save shows the invalid field`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(runningLongMinutes = "0") } }

            act { controller.save() }

            val state = controller.state.value.shouldNotBeNull()
            state.invalid shouldBe InvalidField(NotificationField.RUNNING_LONG_MINUTES)
            state.saveError.shouldBeNull()
            state.draft.runningLongMinutes shouldBe "0"
            repo.updates shouldBe 0
            repo.settings.value shouldBe saved
        }

    @Test
    fun `max visible out of range shows its field and writes nothing`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(popup = it.popup.copy(maxVisible = "11")) } }

            act { controller.save() }

            val state = controller.state.value.shouldNotBeNull()
            state.invalid shouldBe InvalidField(NotificationField.MAX_VISIBLE)
            state.draft.popup.maxVisible shouldBe "11"
            repo.updates shouldBe 0
            repo.settings.value shouldBe saved
        }

    @Test
    fun `editing after a refused save clears the invalid field and keeps the draft`() =
        runTest {
            val controller = controllerOver(scripted())
            act { controller.open() }
            act { controller.edit { it.copy(runningLongMinutes = "abc") } }
            act { controller.save() }

            act { controller.edit { it.copy(runningLongMinutes = "12") } }

            val state = controller.state.value.shouldNotBeNull()
            state.invalid.shouldBeNull()
            state.draft.runningLongMinutes shouldBe "12"
        }

    @Test
    fun `write failure keeps the dialog open`() =
        runTest {
            val repo = scripted().apply { failure = NotificationSettingsError.WRITE_FAILED }
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(runningLongMinutes = "20") } }

            act { controller.save() }

            val state = controller.state.value.shouldNotBeNull()
            state.saveError shouldBe NotificationSettingsError.WRITE_FAILED
            state.invalid.shouldBeNull()
            state.draft.runningLongMinutes shouldBe "20"
            repo.settings.value shouldBe saved
        }

    @Test
    fun `newer schema shows its own message`() =
        runTest {
            val repo = scripted().apply { failure = NotificationSettingsError.NEWER_SCHEMA }
            val controller = controllerOver(repo)
            act { controller.open() }

            act { controller.save() }

            controller.state.value
                .shouldNotBeNull()
                .saveError shouldBe NotificationSettingsError.NEWER_SCHEMA
        }

    @Test
    fun `editing after a write failure clears the error`() =
        runTest {
            val repo = scripted().apply { failure = NotificationSettingsError.WRITE_FAILED }
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.save() }

            act { controller.edit { it.copy(voice = false) } }

            controller.state.value
                .shouldNotBeNull()
                .saveError
                .shouldBeNull()
        }

    @Test
    fun `save after a write failure tries again`() =
        runTest {
            val repo = scripted().apply { failure = NotificationSettingsError.WRITE_FAILED }
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(runningLongMinutes = "20") } }
            act { controller.save() }
            repo.failure = null

            act { controller.save() }

            controller.state.value.shouldBeNull()
            repo.settings.value.runningLongMinutes shouldBe 20
            repo.updates shouldBe 2
        }

    @Test
    fun `cancel discards the draft`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.edit { it.copy(runningLongMinutes = "20") } }

            act { controller.cancel() }

            controller.state.value.shouldBeNull()
            repo.updates shouldBe 0
            repo.settings.value shouldBe saved
            act { controller.open() }
            controller.state.value
                .shouldNotBeNull()
                .draft.runningLongMinutes shouldBe "45"
        }

    @Test
    fun `edit and save do nothing while closed`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)

            act { controller.edit { it.copy(voice = false) } }
            act { controller.save() }

            controller.state.value.shouldBeNull()
            repo.updates shouldBe 0
        }

    @Test
    fun `a second save while the write is pending does not write again`() =
        runTest {
            val repo = scripted(hold = true)
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
            val repo = scripted(hold = true)
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.save() }
            act { controller.cancel() }
            act { controller.open() }
            act { controller.edit { it.copy(runningLongMinutes = "20") } }

            repo.release.complete(Unit)
            act { }

            controller.state.value
                .shouldNotBeNull()
                .draft.runningLongMinutes shouldBe "20"
        }

    @Test
    fun `save works again after a write that was dropped by cancel`() =
        runTest {
            val repo = scripted(hold = true)
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
}
