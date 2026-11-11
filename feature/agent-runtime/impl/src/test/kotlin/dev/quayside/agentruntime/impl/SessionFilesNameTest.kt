package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.aPastConversation
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

/** Name (`custom-title`) and last prompt of a session file (AM-315, ADR 0034, decision 1). Pure, no IO. */
internal class SessionFilesNameTest {
    private fun user(
        text: String,
        time: String = FIRST_TIME,
    ) = sessionEntry("user", content = JsonPrimitive(text), timestamp = time)

    private val assistant = sessionEntry("assistant", content = parts("answer"), timestamp = SECOND_TIME)

    private fun customTitle(title: String): String =
        buildJsonObject {
            put("type", "custom-title")
            put("customTitle", title)
            put("sessionId", SESSION_ID)
        }.toString()

    private fun summary(
        headLines: List<String>,
        tailLines: List<String> = emptyList(),
    ): SessionFileSummary {
        val whole = tailLines.isEmpty()
        val head = lines(*headLines.toTypedArray())
        return summaryOf(SessionFiles.parse(SESSION_ID, head, lines(*tailLines.toTypedArray()), whole))
    }

    @Test
    fun `custom title from the head is the name`() {
        summary(listOf(user("hello"), customTitle("Named in head"), assistant)).customTitle shouldBe "Named in head"
    }

    @Test
    fun `later custom title in the tail wins`() {
        val result = summary(listOf(user("hello"), customTitle("Old")), listOf(customTitle("New"), assistant))

        result.customTitle shouldBe "New"
    }

    @Test
    fun `blank custom title is ignored`() {
        summary(listOf(user("hello"), customTitle("   "))).customTitle shouldBe null
        val blankInTail = summary(listOf(user("hello"), customTitle("Real")), listOf(customTitle(""), assistant))

        blankInTail.customTitle shouldBe "Real"
    }

    @Test
    fun `summary and agent-name entries are not a name`() {
        val summaryEntry = """{"type":"summary","summary":"A summary","leafUuid":"$SESSION_ID"}"""
        val agentName = """{"type":"agent-name","agentName":"An agent","sessionId":"$SESSION_ID"}"""

        summary(listOf(summaryEntry, agentName, user("hello"), assistant)).customTitle shouldBe null
    }

    @Test
    fun `last prompt comes from the tail`() {
        val result = summary(listOf(user("first")), listOf(user("second"), assistant))

        result.lastPrompt shouldBe "second"
    }

    @Test
    fun `last prompt skips meta commands and tool results`() {
        val tail =
            listOf(
                user("Real one"),
                withFlag(user("meta text"), "isMeta", true),
                user("<command-name>/model</command-name>"),
                user("<local-command-stdout>out</local-command-stdout>"),
                sessionEntry("user", content = parts(TOOL_RESULT)),
                assistant,
            )

        summary(listOf(user("first")), tail).lastPrompt shouldBe "Real one"
    }

    @Test
    fun `tail without a user message gives no last prompt`() {
        summary(listOf(user("first")), listOf(assistant)).lastPrompt shouldBe null
    }

    @Test
    fun `whole file uses the head for the last prompt`() {
        summary(listOf(user("first"), assistant, user("second"), assistant)).lastPrompt shouldBe "second"
    }

    @Test
    fun `custom title and last prompt are cut at 80 code points`() {
        // 81 emoji are 162 UTF-16 units: cutting by units or by 81 points would give another length
        val long = "😀".repeat(81)

        val result = summary(listOf(user("first"), customTitle(long)), listOf(user(long), assistant))

        result.customTitle?.let { it.codePointCount(0, it.length) } shouldBe 80
        result.lastPrompt?.let { it.codePointCount(0, it.length) } shouldBe 80
    }

    @Test
    fun `PastConversation toString hides name and last prompt`() {
        val conversation = aPastConversation(name = "secret-name", lastPrompt = "secret-prompt", title = "secret-title")
        val text = conversation.toString()

        text shouldNotContain "secret"
    }
}
