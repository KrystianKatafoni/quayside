package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.EventMeta
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

internal class HookPayloadParserTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("accepted")
    @Suppress("UnusedParameter") // label only names the case in the report.
    fun `maps a hook to an event`(
        label: String,
        body: String,
        expected: HookParse,
    ) {
        HookPayloadParser.parse(body, T0) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejected")
    @Suppress("UnusedParameter") // label only names the case in the report.
    fun `rejects a body that is not a usable hook`(
        label: String,
        body: String,
        reason: String,
    ) {
        HookPayloadParser.parse(body, T0) shouldBe HookParse.Invalid(reason)
    }

    @Test
    fun `a rejection never quotes the input`() {
        val secret = "super-secret-prompt"
        val result = HookPayloadParser.parse("{\"cwd\": \"$secret\" oops", T0)

        result.toString().contains(secret) shouldBe false
    }

    @Test
    fun `an unreadable cwd is rejected`() {
        val body = """{"session_id":"c-1","cwd":"/a\u0000b","hook_event_name":"Stop"}"""

        HookPayloadParser.parse(body, T0) shouldBe HookParse.Invalid(HookRejection.MISSING_CWD)
    }

    @Test
    fun `an overlong or unsafe kind becomes unknown`() {
        val long = "x".repeat(41)
        val failed = HookPayloadParser.parse(base("StopFailure", ""","error_type":"$long""""), T0)
        val ended = HookPayloadParser.parse(base("SessionEnd", ""","reason":"a b""""), T0)

        failed shouldBe HookParse.Event(AgentEvent.Failed(meta, "unknown"))
        ended shouldBe HookParse.Event(AgentEvent.SessionEnded(meta, "unknown"))
    }

    companion object {
        private const val PERMISSION_MESSAGE = "Claude needs your permission to use Bash"
        val meta = EventMeta(ConversationId("c-1"), Path.of("/work/app"), T0)

        fun base(
            name: String,
            extra: String = "",
        ) = """{"session_id":"c-1","cwd":"/work/app","hook_event_name":"$name"$extra}"""

        @JvmStatic
        @Suppress("LongMethod")
        fun accepted(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "needs input with question",
                    base("Notification", ""","notification_type":"agent_needs_input","message":"Which file?""""),
                    HookParse.Event(AgentEvent.NeedsInput(meta, "Which file?")),
                ),
                Arguments.of(
                    "idle prompt without message",
                    base("Notification", ""","notification_type":"idle_prompt""""),
                    HookParse.Event(AgentEvent.NeedsInput(meta, null)),
                ),
                Arguments.of(
                    "elicitation dialog",
                    base("Notification", ""","notification_type":"elicitation_dialog","message":"Pick one""""),
                    HookParse.Event(AgentEvent.NeedsInput(meta, "Pick one")),
                ),
                Arguments.of(
                    "permission prompt",
                    base("Notification", ""","notification_type":"permission_prompt","message":"Use Bash?""""),
                    HookParse.Event(AgentEvent.PermissionRequested(meta, null)),
                ),
                Arguments.of(
                    "permission prompt names the tool",
                    base(
                        "Notification",
                        ""","notification_type":"permission_prompt","message":"$PERMISSION_MESSAGE"""",
                    ),
                    HookParse.Event(AgentEvent.PermissionRequested(meta, "Bash")),
                ),
                Arguments.of(
                    "other notification type",
                    base("Notification", ""","notification_type":"agent_completed""""),
                    HookParse.Ignored,
                ),
                Arguments.of(
                    "notification without type",
                    base("Notification"),
                    HookParse.Ignored,
                ),
                Arguments.of(
                    "stop with last message",
                    base("Stop", ""","last_assistant_message":"Done.""""),
                    HookParse.Event(AgentEvent.TurnEnded(meta, "Done.")),
                ),
                Arguments.of("stop without message", base("Stop"), HookParse.Event(AgentEvent.TurnEnded(meta, null))),
                Arguments.of(
                    "stop failure by error_type",
                    base("StopFailure", ""","error_type":"rate_limit""""),
                    HookParse.Event(AgentEvent.Failed(meta, "rate_limit")),
                ),
                Arguments.of(
                    "stop failure by error",
                    base("StopFailure", ""","error":"authentication_failed""""),
                    HookParse.Event(AgentEvent.Failed(meta, "authentication_failed")),
                ),
                Arguments.of(
                    "stop failure without kind",
                    base("StopFailure"),
                    HookParse.Event(AgentEvent.Failed(meta, "unknown")),
                ),
                Arguments.of(
                    "session end",
                    base("SessionEnd", ""","reason":"clear""""),
                    HookParse.Event(AgentEvent.SessionEnded(meta, "clear")),
                ),
                Arguments.of("session start", base("SessionStart"), HookParse.Ignored),
                Arguments.of("unknown event", base("PreCompact"), HookParse.Ignored),
                Arguments.of(
                    "unknown fields are tolerated",
                    base("Stop", ""","future":{"a":[1,2]},"last_assistant_message":"ok""""),
                    HookParse.Event(AgentEvent.TurnEnded(meta, "ok")),
                ),
                Arguments.of(
                    "non-string message is treated as missing",
                    base("Notification", ""","notification_type":"idle_prompt","message":42"""),
                    HookParse.Event(AgentEvent.NeedsInput(meta, null)),
                ),
            )

        @JvmStatic
        fun rejected(): Stream<Arguments> =
            Stream.of(
                Arguments.of("empty", "", HookRejection.MALFORMED),
                Arguments.of("not json", "hello", HookRejection.MALFORMED),
                Arguments.of("array", "[]", HookRejection.MALFORMED),
                Arguments.of("no event name", """{"session_id":"c","cwd":"/a"}""", HookRejection.MISSING_EVENT_NAME),
                Arguments.of(
                    "no session id",
                    """{"cwd":"/a","hook_event_name":"Stop"}""",
                    HookRejection.MISSING_SESSION_ID,
                ),
                Arguments.of(
                    "blank session id",
                    """{"session_id":" ","cwd":"/a","hook_event_name":"Stop"}""",
                    HookRejection.MISSING_SESSION_ID,
                ),
                Arguments.of(
                    "no cwd",
                    """{"session_id":"c","hook_event_name":"Stop"}""",
                    HookRejection.MISSING_CWD,
                ),
            )
    }
}
