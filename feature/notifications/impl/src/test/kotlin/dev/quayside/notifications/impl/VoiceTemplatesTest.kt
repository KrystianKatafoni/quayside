package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** [VoiceTemplates.render]: template, session and project → the text to speak (FR-N4). */
class VoiceTemplatesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("rendering")
    @Suppress("UNUSED_PARAMETER")
    fun `renders the template`(
        label: String,
        template: String,
        session: Session?,
        expected: String?,
    ) {
        VoiceTemplates.render(template, NotificationKind.TURN_ENDED, session, PROJECT) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readingTheQuestion")
    fun `readQuestion appends the question`(
        template: String,
        kind: NotificationKind,
        readQuestion: Boolean,
        question: String?,
        expected: String?,
    ) {
        val session = aSession(name = "opcua-connector", state = SessionState.AwaitingInput(question))

        VoiceTemplates.render(template, kind, session, PROJECT, readQuestion) shouldBe expected
    }

    @Test
    fun `an embedded say command in the question is removed`() {
        val session = aSession(state = SessionState.AwaitingInput("[[volm 0]] ok [[rate 900]]"))

        val text = VoiceTemplates.render("{pytanie}", NotificationKind.NEEDS_INPUT, session, PROJECT).orEmpty()

        text shouldNotContain "[["
        text shouldNotContain "]]"
        text shouldBe text.trim()
        text.contains("ok") shouldBe true
    }

    @Test
    fun `an embedded say command in the appended question is removed`() {
        val session = aSession(state = SessionState.AwaitingInput("hi [[volm 0]]"))

        val text = VoiceTemplates.render("Czekam", NotificationKind.NEEDS_INPUT, session, PROJECT, true).orEmpty()

        text shouldNotContain "[["
        text shouldNotContain "]]"
        text.startsWith("Czekam. hi") shouldBe true
    }

    @Test
    fun `an embedded say command in the project and the session name is removed`() {
        val session = aSession(name = "[[volm 0]]x")

        val text = VoiceTemplates.render("{sesja} {projekt}", NotificationKind.TURN_ENDED, session, "[[slnc 5000]]p")

        text.orEmpty() shouldNotContain "[["
        text.orEmpty() shouldNotContain "]]"
    }

    @Test
    fun `a long question is cut to 200 characters`() {
        val session = aSession(state = SessionState.AwaitingInput("a".repeat(500)))

        VoiceTemplates.render("{pytanie}", NotificationKind.NEEDS_INPUT, session, PROJECT) shouldBe "a".repeat(200)
    }

    @Test
    fun `the default templates render for every kind`() {
        val session = aSession(name = "opcua-connector", state = SessionState.AwaitingPermission("Bash"))

        NotificationKind.entries.forEach {
            val text = VoiceTemplates.render(SpeechSettings.defaultTemplate(it), it, session, PROJECT)
            text.isNullOrBlank() shouldBe false
            text.orEmpty() shouldNotContain "{"
        }
    }

    companion object {
        private const val PROJECT = "plantpulse-core"

        private fun named(name: String) = aSession(name = name)

        private fun asking(question: String?) = aSession(state = SessionState.AwaitingInput(question))

        private fun using(tool: String?) = aSession(state = SessionState.AwaitingPermission(tool))

        @JvmStatic
        @Suppress("LongMethod")
        fun rendering(): List<Arguments> =
            listOf(
                arguments(
                    "session with a dash",
                    "{sesja} skończył",
                    named("opcua-connector"),
                    "opcua connector skończył",
                ),
                arguments("session with underscores", "{sesja}", named("fix_login-now"), "fix login now"),
                arguments("project is kept as is", "w {projekt}", named("a"), "w plantpulse-core"),
                arguments("question", "Pytanie: {pytanie}", asking("Which branch?"), "Pytanie: Which branch?"),
                arguments("no question", "Pytanie: {pytanie}", named("a"), "Pytanie:"),
                arguments("question not given", "Pytanie: {pytanie}", asking(null), "Pytanie:"),
                arguments("tool", "Zgoda na {narzędzie}", using("Bash"), "Zgoda na Bash"),
                arguments("no tool", "Zgoda na {narzędzie}", using(null), "Zgoda na"),
                arguments(
                    "summary",
                    "Zrobione: {podsumowanie}",
                    aSession(summary = "Fixed the login"),
                    "Zrobione: Fixed the login",
                ),
                arguments("no summary", "Zrobione: {podsumowanie}", aSession(summary = null), "Zrobione:"),
                arguments("unknown variable stays", "{x} {sesja}", named("a"), "{x} a"),
                arguments("newlines become spaces", "{pytanie}", asking("a\nb\r\nc"), "a b c"),
                arguments("spaces are collapsed", "  {sesja}    {projekt}  ", named("a"), "a plantpulse-core"),
                arguments("a variable used twice", "{sesja} {sesja}", named("a-b"), "a b a b"),
                arguments("nothing left is null", "{pytanie}", named("a"), null),
                arguments("blank template is null", "   ", named("a"), null),
                arguments("bell without a session", "Dzwonek w terminalu", null, "Dzwonek w terminalu"),
                arguments("session variable without a session", "{sesja}", null, null),
            )

        @JvmStatic
        @Suppress("LongMethod")
        fun readingTheQuestion(): List<Arguments> {
            val needsInput = SpeechSettings.defaultTemplate(NotificationKind.NEEDS_INPUT)
            val base = "Agent opcua connector w projekcie plantpulse-core czeka na odpowiedź"
            return listOf(
                arguments(
                    needsInput,
                    NotificationKind.NEEDS_INPUT,
                    true,
                    "Which branch?",
                    "$base. Which branch?",
                ),
                arguments(needsInput, NotificationKind.NEEDS_INPUT, false, "Which branch?", base),
                arguments(
                    "{pytanie}",
                    NotificationKind.NEEDS_INPUT,
                    true,
                    "Which?",
                    "Which?",
                ),
                arguments(needsInput, NotificationKind.NEEDS_INPUT, true, null, base),
                arguments(needsInput, NotificationKind.NEEDS_INPUT, true, "   ", base),
                arguments("Koniec", NotificationKind.TURN_ENDED, true, "Which?", "Koniec"),
            )
        }
    }
}
