package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Outcome
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** `claude -p --output-format json` stdout (ADR 0019): the `result` text, or a fixed-category error. */
class PrintResultParserTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases", "typeCases")
    fun `reads the result of the answer`(
        case: String,
        stdout: String,
        expected: Outcome<String, RuntimeError>,
    ) {
        withClue(case) { PrintResultParser.parse(stdout, exitCode = 1) shouldBe expected }
    }

    @Test
    fun `is_error carries the exit code and no text of the answer`() {
        val stdout = """{"type":"result","is_error":true,"result":"Credit balance too low for $SECRET"}"""

        val parsed = PrintResultParser.parse(stdout, exitCode = 7)

        parsed shouldBe Outcome.Err(RuntimeError.CommandFailed(7, ""))
        parsed.toString() shouldNotContain SECRET
    }

    @Test
    fun `an unparseable answer never quotes its input`() {
        val parsed = PrintResultParser.parse("not json $SECRET {", exitCode = 0)

        parsed shouldBe Outcome.Err(RuntimeError.Unparseable(DETAIL))
        parsed.toString() shouldNotContain SECRET
    }

    companion object {
        private const val SECRET = "secret plan for the merger"
        private const val DETAIL = "print result"

        private fun unparseable(): Outcome<String, RuntimeError> = Outcome.Err(RuntimeError.Unparseable(DETAIL))

        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "success",
                    """{"type":"result","subtype":"success","is_error":false,"result":"Fixed the form."}""",
                    Outcome.Ok("Fixed the form."),
                ),
                Arguments.of(
                    "unknown fields are ignored",
                    """{"extra":{"a":[1,2]},"result":"Done.","total_cost_usd":0.01,"is_error":false,"new":null}""",
                    Outcome.Ok("Done."),
                ),
                Arguments.of(
                    "surrounding whitespace",
                    "\n  {\"result\":\"Done.\"}\n\n",
                    Outcome.Ok("Done."),
                ),
                Arguments.of(
                    "is_error without result",
                    """{"type":"result","is_error":true}""",
                    Outcome.Err(RuntimeError.CommandFailed(1, "")),
                ),
            )

        /** Answers of the wrong type or shape: all are malformed (Unparseable). "true" the string is not true. */
        @JvmStatic
        fun typeCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("is_error as the string true", """{"is_error":"true","result":"Text."}""", unparseable()),
                Arguments.of(
                    "is_error as the string false",
                    """{"is_error":"false","result":"Text."}""",
                    unparseable(),
                ),
                Arguments.of("missing result", """{"type":"result","is_error":false}""", unparseable()),
                Arguments.of("result is not a string", """{"result":42}""", unparseable()),
                Arguments.of("result is null", """{"result":null}""", unparseable()),
                Arguments.of("not json", "this is not json {", unparseable()),
                Arguments.of("json array", """[{"result":"Done."}]""", unparseable()),
                Arguments.of("empty output", "", unparseable()),
                Arguments.of("blank output", "  \n", unparseable()),
            )
    }
}
