package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.ConversationRole
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant

/** `ConversationEntries.messageOf`: the table of the AM-315 plan (one JSON line to one message). Pure. */
internal class ConversationEntriesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    fun `a line gives the expected message or none`(
        @Suppress("UNUSED_PARAMETER") name: String,
        line: String,
        expected: ConversationMessage?,
    ) {
        ConversationEntries.messageOf(line) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("timestamps")
    fun `a missing or malformed timestamp gives no time, the message stays`(
        @Suppress("UNUSED_PARAMETER") name: String,
        stamp: String,
        expected: Instant?,
    ) {
        val line = """{"type":"user",$stamp"message":{"role":"user","content":"hi"}}"""

        ConversationEntries.messageOf(line) shouldBe ConversationMessage(ConversationRole.USER, "hi", expected, false)
    }

    @Test
    fun `text of exactly 4000 code points is whole, one more is cut to 4000 and marked`() {
        // emoji: 4000 of them are 8000 UTF-16 units, so cutting by units would mark the first as cut
        val whole = "😀".repeat(ConversationEntries.MAX_TEXT_CODE_POINTS)
        val over = whole + "😁"

        val a = ConversationEntries.messageOf(userLine(whole))
        val b = ConversationEntries.messageOf(userLine(over))

        a?.text shouldBe whole
        a?.truncated shouldBe false
        b?.text shouldBe whole
        b?.truncated shouldBe true
    }

    @Test
    fun `the limit applies to the joined text of several parts`() {
        val half = "a".repeat(ConversationEntries.MAX_TEXT_CODE_POINTS / 2 + 1)
        val part = """{"type":"text","text":"$half"}"""
        val line = """{"type":"user","message":{"role":"user","content":[$part,$part]}}"""

        val message = ConversationEntries.messageOf(line)

        message?.truncated shouldBe true
        message?.text?.length shouldBe ConversationEntries.MAX_TEXT_CODE_POINTS
    }

    private fun userLine(text: String): String = """{"type":"user","message":{"role":"user","content":"$text"}}"""

    companion object {
        private val T = Instant.parse("2026-01-01T10:00:00Z")
        private const val STAMP = """"timestamp":"2026-01-01T10:00:00Z","""

        private fun user(
            content: String,
            extra: String = "",
        ) = """{"type":"user",$extra$STAMP"message":{"role":"user","content":$content}}"""

        private fun assistant(content: String) =
            """{"type":"assistant",$STAMP"message":{"role":"assistant","content":$content}}"""

        private fun text(value: String) = """{"type":"text","text":"$value"}"""

        private fun msg(
            role: ConversationRole,
            text: String,
        ) = ConversationMessage(role, text, T, false)

        @JvmStatic
        @Suppress("LongMethod")
        fun rows(): List<Arguments> =
            listOf(
                arguments("user string", user("\"hello\""), msg(ConversationRole.USER, "hello")),
                arguments(
                    "user text parts joined with a newline",
                    user("[${text("one")},${text("two")}]"),
                    msg(ConversationRole.USER, "one\ntwo"),
                ),
                arguments(
                    "user text next to a tool_result keeps the text",
                    user("""[{"type":"tool_result","content":"x"},${text("kept")}]"""),
                    msg(ConversationRole.USER, "kept"),
                ),
                arguments("user only tool_result", user("""[{"type":"tool_result","content":"x"}]"""), null),
                arguments("user isMeta", user("\"meta\"", extra = "\"isMeta\":true,"), null),
                arguments("user command", user("\"<command-name>/model</command-name>\""), null),
                arguments(
                    "user local command output",
                    user("\"<local-command-stdout>x</local-command-stdout>\""),
                    null,
                ),
                arguments("user sidechain", user("\"side\"", extra = "\"isSidechain\":true,"), null),
                arguments(
                    "assistant text with tool_use keeps only the text",
                    assistant("""[${text("answer")},{"type":"tool_use","name":"Read","input":{}}]"""),
                    msg(ConversationRole.ASSISTANT, "answer"),
                ),
                arguments("assistant only thinking", assistant("""[{"type":"thinking","thinking":"hmm"}]"""), null),
                arguments("assistant only tool_use", assistant("""[{"type":"tool_use","name":"Read"}]"""), null),
                arguments("assistant string", assistant("\"plain\""), msg(ConversationRole.ASSISTANT, "plain")),
                arguments("summary", """{"type":"summary","summary":"s"}""", null),
                arguments("custom-title", """{"type":"custom-title","customTitle":"t"}""", null),
                arguments("last-prompt", """{"type":"last-prompt","leafUuid":"x"}""", null),
                arguments("broken JSON", """{"type":"user","message":""", null),
                arguments("not an object", "[1,2]", null),
                arguments("type as a number", """{"type":5,"message":{"role":"user","content":"x"}}""", null),
                arguments("no message", """{"type":"user"}""", null),
            )

        @JvmStatic
        fun timestamps(): List<Arguments> =
            listOf(
                arguments("valid", "\"timestamp\":\"2026-01-01T10:00:00Z\",", T),
                arguments("missing", "", null),
                arguments("bad format", "\"timestamp\":\"yesterday\",", null),
                arguments("not a string", "\"timestamp\":12345,", null),
            )
    }
}
