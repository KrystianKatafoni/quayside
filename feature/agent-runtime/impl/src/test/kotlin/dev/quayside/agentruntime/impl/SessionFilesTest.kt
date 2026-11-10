package dev.quayside.agentruntime.impl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Pure rules of [SessionFiles]: tables 1-3 of the AM-311 plan. The `name` parameters only label table rows. */
@Suppress("UnusedParameter")
internal class SessionFilesTest {
    private val usual =
        SessionFileSummary(
            sessionId = SESSION_ID,
            startCwd = Path.of("/p"),
            startedAt = at(FIRST_TIME),
            lastActivityAt = at(LAST_TIME),
            cwdChanged = false,
            title = "hello",
        )

    // --- 1. parse: the start directory and the timestamps ---

    @Test
    fun `whole file yields all metadata`() {
        val text = lines(sessionEntry(timestamp = FIRST_TIME), sessionEntry(type = "assistant", timestamp = LAST_TIME))

        summaryOf(parseWhole(text)) shouldBe usual
    }

    @Test
    fun `first entry without cwd is skipped when looking for the start directory`() {
        val summary = """{"type":"summary","summary":"An older title","leafUuid":"x"}"""
        val text =
            lines(
                summary,
                sessionEntry(cwd = null, timestamp = FIRST_TIME),
                sessionEntry(cwd = "/p", timestamp = SECOND_TIME),
            )

        val result = summaryOf(parseWhole(text))

        result.startCwd shouldBe Path.of("/p")
        result.startedAt shouldBe at(FIRST_TIME)
    }

    @Test
    fun `relative cwd is skipped in favour of the first absolute one`() {
        val text = lines(sessionEntry(cwd = "relative/dir"), sessionEntry(cwd = "/q/work"))

        summaryOf(parseWhole(text)).startCwd shouldBe Path.of("/q/work")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cwdChanges")
    fun `cwd change keeps the start directory and flags the change`(
        name: String,
        head: String,
        tail: String,
        expectedChanged: Boolean,
    ) {
        val result = summaryOf(SessionFiles.parse(SESSION_ID, head, tail, headIsWhole = false))

        result.startCwd shouldBe Path.of("/p")
        result.cwdChanged shouldBe expectedChanged
    }

    @Test
    fun `partial lines at the cut ends are skipped without an error`() {
        val head =
            lines(sessionEntry(cwd = "/p", timestamp = FIRST_TIME)) + """{"type":"user","cwd":"/leaked","timest"""
        val tail =
            """ssage":"x","cwd":"/leaked2"}""" + "\n" + sessionEntry(type = "assistant", timestamp = LAST_TIME) + "\n"

        val result = summaryOf(SessionFiles.parse(SESSION_ID, head, tail, headIsWhole = false))

        result shouldBe usual
    }

    @Test
    fun `a whole file gives the same result with an empty tail and with a tail equal to the head`() {
        val text =
            lines(
                sessionEntry(timestamp = FIRST_TIME),
                sessionEntry(type = "assistant", cwd = "/p/soft", timestamp = LAST_TIME),
            )

        val withEmptyTail = SessionFiles.parse(SESSION_ID, text, tail = "", headIsWhole = true)
        val withCopiedTail = SessionFiles.parse(SESSION_ID, text, tail = text, headIsWhole = true)

        summaryOf(withEmptyTail) shouldBe usual.copy(cwdChanged = true)
        withCopiedTail shouldBe withEmptyTail
    }

    @Test
    fun `unknown entry types, unknown fields and non-object lines are ignored`() {
        val unknown = """{"type":"queue-operation","operation":"enqueue","nested":{"a":[1,2,{"b":null}]}}"""
        val extra = sessionEntry(timestamp = FIRST_TIME).dropLast(1) + ""","futureField":{"x":[true,null]}}"""
        val text =
            lines(
                "[1,2]",
                "42",
                "null",
                "\"text\"",
                unknown,
                extra,
                sessionEntry(type = "assistant", timestamp = LAST_TIME),
            )

        summaryOf(parseWhole(text)) shouldBe usual
    }

    // --- 1. parse: problems ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("problems")
    fun `file that is not a listable session reports a fixed category`(
        name: String,
        text: String,
        expected: SessionFileProblem,
    ) {
        problemOf(parseWhole(text)) shouldBe expected
    }

    @Test
    fun `isSidechain false is a normal session`() {
        val text = lines(withFlag(sessionEntry(), "isSidechain", false))

        summaryOf(parseWhole(text)).startCwd shouldBe Path.of("/p")
    }

    // --- 1. parse: title ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("titles")
    fun `title is the first real user message`(
        name: String,
        text: String,
        expected: String?,
    ) {
        summaryOf(parseWhole(text)).title shouldBe expected
    }

    @ParameterizedTest(name = "{0} emoji")
    @MethodSource("emojiCounts")
    fun `title is cut at 80 code points, not at 80 UTF-16 units`(
        count: Int,
        expectedCount: Int,
    ) {
        val title = summaryOf(parseWhole(lines(sessionEntry(content = JsonPrimitive("😀".repeat(count)))))).title

        title shouldBe "😀".repeat(expectedCount)
    }

    @Test
    fun `multi-line message becomes a one-line title`() {
        val content = JsonPrimitive("first line\nsecond line\r\nthird")

        val title = summaryOf(parseWhole(lines(sessionEntry(content = content)))).title.orEmpty()

        title shouldStartWith "first line"
        title shouldNotContain "\n"
        title shouldNotContain "\r"
    }

    // --- 1. parse: timestamps ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("timestamps")
    fun `bad or missing timestamp gives null for that field only`(
        name: String,
        head: String,
        tail: String,
        expectedStart: String?,
        expectedLast: String?,
    ) {
        val result = summaryOf(SessionFiles.parse(SESSION_ID, head, tail, headIsWhole = false))

        result.startedAt shouldBe expectedStart?.let(::at)
        result.lastActivityAt shouldBe expectedLast?.let(::at)
    }

    companion object {
        private val head = lines(sessionEntry(cwd = "/p", timestamp = FIRST_TIME))

        @JvmStatic
        fun cwdChanges(): List<Arguments> =
            listOf(
                arguments(
                    "tail in a subfolder",
                    head,
                    lines(sessionEntry(cwd = "/p/soft", timestamp = LAST_TIME)),
                    true,
                ),
                arguments(
                    "tail in the same folder",
                    head,
                    lines(sessionEntry(cwd = "/p", timestamp = LAST_TIME)),
                    false,
                ),
                arguments(
                    "change inside the head",
                    head + lines(sessionEntry(cwd = "/p/soft", timestamp = SECOND_TIME)),
                    lines(sessionEntry(cwd = "/p/soft", timestamp = LAST_TIME)),
                    true,
                ),
            )

        @JvmStatic
        fun problems(): List<Arguments> =
            listOf(
                arguments(
                    "sessionId differs from the file name",
                    lines(sessionEntry(sessionId = "other")),
                    SessionFileProblem.ID_MISMATCH,
                ),
                arguments("no sessionId", lines(sessionEntry(sessionId = null)), SessionFileProblem.NO_SESSION_ID),
                arguments("no cwd", lines(sessionEntry(cwd = null)), SessionFileProblem.NO_CWD),
                arguments("relative cwd", lines(sessionEntry(cwd = "relative/dir")), SessionFileProblem.NO_CWD),
                arguments(
                    "sidechain",
                    lines(withFlag(sessionEntry(), "isSidechain", true)),
                    SessionFileProblem.SIDECHAIN,
                ),
                arguments("no JSON line", "plain text\nand more text\n", SessionFileProblem.NOT_JSONL),
                arguments("empty text", "", SessionFileProblem.NOT_JSONL),
            )

        @JvmStatic
        @Suppress("LongMethod")
        fun titles(): List<Arguments> {
            val command = sessionEntry(content = JsonPrimitive("<command-name>/clear</command-name>"))
            val meta = withFlag(sessionEntry(content = JsonPrimitive("Caveat: local commands")), "isMeta", true)
            val real = sessionEntry(content = JsonPrimitive("Fix the login bug"), timestamp = SECOND_TIME)
            return listOf(
                arguments("command first", lines(command, real), "Fix the login bug"),
                arguments("isMeta first", lines(meta, real), "Fix the login bug"),
                arguments("content as string", lines(real), "Fix the login bug"),
                arguments(
                    "array: first text part, tool_result skipped",
                    lines(sessionEntry(content = parts("first part", TOOL_RESULT, "second part"))),
                    "first part",
                ),
                arguments(
                    "array: tool_result before text",
                    lines(sessionEntry(content = parts(TOOL_RESULT, "later text"))),
                    "later text",
                ),
                arguments(
                    "array with tool_result only is not a message",
                    lines(sessionEntry(content = parts(TOOL_RESULT)), real),
                    "Fix the login bug",
                ),
                arguments(
                    "assistant text is not a title",
                    lines(sessionEntry(type = "assistant", content = JsonPrimitive("assistant words")), real),
                    "Fix the login bug",
                ),
                arguments(
                    "no user message",
                    lines(sessionEntry(type = "assistant", content = JsonPrimitive("words"))),
                    null,
                ),
            )
        }

        @JvmStatic
        fun emojiCounts(): List<Arguments> =
            listOf(arguments(79, 79), arguments(80, 80), arguments(81, 80), arguments(200, 80))

        @JvmStatic
        @Suppress("LongMethod")
        fun timestamps(): List<Arguments> {
            val bad = lines(sessionEntry(timestamp = "not a date"))
            val first = lines(sessionEntry(timestamp = FIRST_TIME))
            return listOf(
                arguments("invalid in the head", bad, lines(sessionEntry(timestamp = LAST_TIME)), null, LAST_TIME),
                arguments("invalid in the tail", first, lines(sessionEntry(timestamp = "yesterday")), FIRST_TIME, null),
                arguments("missing in the tail", first, lines(sessionEntry(timestamp = null)), FIRST_TIME, null),
                arguments(
                    "missing in the head",
                    lines(sessionEntry(timestamp = null)),
                    lines(sessionEntry(timestamp = LAST_TIME)),
                    null,
                    LAST_TIME,
                ),
                arguments(
                    "last one in the tail wins",
                    first,
                    lines(sessionEntry(timestamp = SECOND_TIME), sessionEntry(timestamp = LAST_TIME)),
                    FIRST_TIME,
                    LAST_TIME,
                ),
            )
        }
    }
}
