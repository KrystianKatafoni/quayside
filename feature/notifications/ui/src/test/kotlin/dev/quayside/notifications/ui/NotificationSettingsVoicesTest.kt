package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.notifications.api.VoiceCatalogError
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The list of voices and "restore default" in the notification settings controller (FR-N4, plan AM-704 part B2b). */
class NotificationSettingsVoicesTest {
    private val alex = VoiceOption(VoiceId("Alex"), "en_US")
    private val zosia = VoiceOption(VoiceId("Zosia"), "pl_PL")

    private fun scripted() = ScriptedRepo(FakeNotificationSettingsRepo())

    private fun TestScope.controllerOver(
        repo: ScriptedRepo,
        voices: VoiceCatalog = FakeVoiceCatalog(emptyList()),
    ) = NotificationSettingsController(repo, voices, backgroundScope)

    @Test
    fun `voices are loaded when the dialog opens`() =
        runTest {
            val controller = controllerOver(scripted(), FakeVoiceCatalog(listOf(zosia, alex)))

            controller.open()

            val loading = controller.state.value.shouldNotBeNull()
            loading.voices.shouldBeNull()
            loading.voicesFailed shouldBe false
            act { }
            val loaded = controller.state.value.shouldNotBeNull()
            loaded.voices shouldBe listOf(alex, zosia)
            loaded.voicesFailed shouldBe false
        }

    @Test
    fun `voices load once per controller`() =
        runTest {
            val voices = FakeVoiceCatalog(listOf(alex, zosia))
            val controller = controllerOver(scripted(), voices)
            act { controller.open() }
            act { controller.cancel() }

            controller.open()

            controller.state.value
                .shouldNotBeNull()
                .voices shouldBe listOf(alex, zosia)
            act { }
            voices.calls shouldBe 1
        }

    @Test
    fun `a second open while the load runs does not ask again`() =
        runTest {
            val voices = ScriptedVoiceCatalog(Outcome.Ok(listOf(alex)), hold = true)
            val controller = controllerOver(scripted(), voices)
            act { controller.open() }
            act { controller.cancel() }
            act { controller.open() }

            voices.release.complete(Unit)
            act { }

            voices.calls shouldBe 1
            controller.state.value
                .shouldNotBeNull()
                .voices shouldBe listOf(alex)
        }

    @Test
    fun `a failed load is shown and tried again at the next open`() =
        runTest {
            val voices = ScriptedVoiceCatalog(Outcome.Err(VoiceCatalogError.FAILED))
            val controller = controllerOver(scripted(), voices)
            act { controller.open() }

            val failed = controller.state.value.shouldNotBeNull()
            failed.voices.shouldBeNull()
            failed.voicesFailed shouldBe true

            voices.result = Outcome.Ok(listOf(alex))
            act { controller.cancel() }
            controller.open()
            controller.state.value
                .shouldNotBeNull()
                .voicesFailed shouldBe false
            act { }

            voices.calls shouldBe 2
            val loaded = controller.state.value.shouldNotBeNull()
            loaded.voices shouldBe listOf(alex)
            loaded.voicesFailed shouldBe false
        }

    @Test
    fun `voices arriving after cancel do not open the dialog and are kept`() =
        runTest {
            val voices = ScriptedVoiceCatalog(Outcome.Ok(listOf(alex)), hold = true)
            val controller = controllerOver(scripted(), voices)
            act { controller.open() }
            act { controller.cancel() }

            voices.release.complete(Unit)
            act { }

            controller.state.value.shouldBeNull()
            act { controller.open() }
            controller.state.value
                .shouldNotBeNull()
                .voices shouldBe listOf(alex)
            voices.calls shouldBe 1
        }

    @Test
    fun `a failed load arriving after cancel does not open the dialog and the next open asks again`() =
        runTest {
            val voices = ScriptedVoiceCatalog(Outcome.Err(VoiceCatalogError.UNAVAILABLE), hold = true)
            val controller = controllerOver(scripted(), voices)
            act { controller.open() }
            act { controller.cancel() }

            voices.release.complete(Unit)
            act { }

            controller.state.value.shouldBeNull()
            act { controller.open() }
            voices.calls shouldBe 2
        }

    @Test
    fun `restore default puts the default text in the draft`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }
            act {
                controller.edit {
                    it.copy(
                        speech =
                            it.speech.copy(
                                templates =
                                    it.speech.templates + (NotificationKind.FAILED to "Ups"),
                            ),
                    )
                }
            }

            act { controller.restoreTemplate(NotificationKind.FAILED) }

            val templates =
                controller.state.value
                    .shouldNotBeNull()
                    .draft.speech.templates
            templates[NotificationKind.FAILED] shouldBe "Uwaga, {sesja} zatrzymał się z błędem"
            templates[NotificationKind.TURN_ENDED] shouldBe "{sesja} skończył turę"
            act { controller.save() }
            repo.settings.value.speech.templates shouldBe emptyMap()
        }

    @Test
    fun `restore default clears the refusal of the template`() =
        runTest {
            val controller = controllerOver(scripted())
            act { controller.open() }
            act {
                controller.edit {
                    it.copy(
                        speech =
                            it.speech.copy(
                                templates =
                                    it.speech.templates + (NotificationKind.FAILED to "[[rate 500]]"),
                            ),
                    )
                }
            }
            act { controller.save() }
            controller.state.value
                .shouldNotBeNull()
                .invalid shouldBe InvalidField(NotificationField.TEMPLATE, NotificationKind.FAILED)

            act { controller.restoreTemplate(NotificationKind.FAILED) }

            controller.state.value
                .shouldNotBeNull()
                .invalid
                .shouldBeNull()
        }

    @Test
    fun `restore default does nothing while closed`() =
        runTest {
            val controller = controllerOver(scripted())

            act { controller.restoreTemplate(NotificationKind.FAILED) }

            controller.state.value.shouldBeNull()
        }
}
