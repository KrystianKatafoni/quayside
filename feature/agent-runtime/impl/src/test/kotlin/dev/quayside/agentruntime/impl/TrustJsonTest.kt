package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.TrustError
import dev.quayside.core.domain.Outcome
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotEndWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

/** Pure merge of `hasTrustDialogAccepted` into the text of `.claude.json` (plan AM-309, rule 2). */
class TrustJsonTest {
    private fun parse(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun merged(
        text: String,
        key: String = KEY,
    ): String =
        TrustJson.withTrust(text, key).let { it.shouldBeInstanceOf<Outcome.Ok<String?>>().value.shouldNotBeNull() }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mergeCases")
    fun `merges trust into the tree`(
        @Suppress("UNUSED_PARAMETER") case: String,
        input: String,
        expected: String,
    ) {
        parse(merged(input)) shouldBe parse(expected)
    }

    @Test
    fun `an entry that is already true gives null`() {
        TrustJson.withTrust("""{"projects":{"$KEY":{"hasTrustDialogAccepted":true,"x":1}}}""", KEY) shouldBe
            Outcome.Ok(null)
    }

    @Test
    fun `a parent entry is never changed and only the exact key is added`() {
        val output = parse(merged("""{"projects":{"/a":{"hasTrustDialogAccepted":false}}}""", key = "/a/b"))

        output shouldBe
            parse("""{"projects":{"/a":{"hasTrustDialogAccepted":false},"/a/b":{"hasTrustDialogAccepted":true}}}""")
    }

    @Test
    fun `numbers and strings keep their literals`() {
        val text =
            """{"a":1.0,"b":12345678901234567890,"c":1e3,"d":"zażółć","e":[[1,[2.50]],{"f":-0.10}],"projects":{}}"""

        val output = merged(text)

        assertSoftly {
            listOf("1.0", "12345678901234567890", "1e3", "2.50", "-0.10").forEach { output shouldContain it }
            parse(output).let { it as JsonObject }["d"] shouldBe parse("\"zażółć\"")
            parse(output).let { it as JsonObject }["e"] shouldBe parse("""[[1,[2.50]],{"f":-0.10}]""")
        }
    }

    @Test
    fun `the first key of the root stays first`() {
        val output = parse(merged("""{"zzz":1,"aaa":2,"projects":{"/other":{}}}""")) as JsonObject

        output.keys.toList() shouldBe listOf("zzz", "aaa", "projects")
    }

    @Test
    fun `a new projects object goes to the end of the root`() {
        val output = parse(merged("""{"zzz":1,"aaa":2}""")) as JsonObject

        output.keys.toList() shouldBe listOf("zzz", "aaa", "projects")
    }

    @Test
    fun `a new field goes to the end of the entry`() {
        val output = parse(merged("""{"projects":{"$KEY":{"zzz":1,"allowedTools":[]}}}""")) as JsonObject

        ((output["projects"] as JsonObject)[KEY] as JsonObject).keys.toList() shouldBe
            listOf("zzz", "allowedTools", "hasTrustDialogAccepted")
    }

    @Test
    fun `the output is indented with two spaces`() {
        merged("""{"projects":{}}""") shouldContain
            "\n  \"projects\": {\n    \"$KEY\": {\n      \"hasTrustDialogAccepted\": true"
    }

    @Test
    fun `a trailing newline stays and its absence stays`() {
        assertSoftly {
            merged("{\"projects\":{}}\n") shouldEndWith "\n"
            merged("{\"projects\":{}}") shouldNotEndWith "\n"
        }
    }

    @ParameterizedTest(name = "[{0}] is CONFIG_INVALID")
    @MethodSource("invalidInputs")
    fun `a wrong shape gives CONFIG_INVALID`(input: String) {
        TrustJson.withTrust(input, KEY) shouldBe Outcome.Err(TrustError.CONFIG_INVALID)
    }

    @ParameterizedTest(name = "[{0}] is CONFIG_INVALID")
    @ValueSource(strings = ["", "   ", "{", "null", "\"x\"", "12"])
    fun `text that is not a JSON object gives CONFIG_INVALID`(input: String) {
        TrustJson.withTrust(input, KEY) shouldBe Outcome.Err(TrustError.CONFIG_INVALID)
    }

    companion object {
        private const val KEY = "/Users/test/projects/app"

        @JvmStatic
        fun invalidInputs(): List<String> =
            listOf(
                "[]",
                """{"projects":[]}""",
                """{"projects":"x"}""",
                """{"projects":{"$KEY":"x"}}""",
                """{"projects":{"$KEY":[]}}""",
                """{"projects":{"$KEY":{"hasTrustDialogAccepted":"true"}}}""",
                """{"projects":{"$KEY":{"hasTrustDialogAccepted":1}}}""",
                """{"projects":{"$KEY":{"hasTrustDialogAccepted":null}}}""",
                """{"projects":{},"x":NaN}""",
                """{"projects":{},"x":Infinity}""",
                """{"projects":{},"x":abc}""",
                """{"projects":{},"x":01}""",
                """{"projects":{},"x":'x'}""",
                """{"projects":{},"x":1.2.3}""",
                """{"projects":{},"x":[1,tru]}""",
                """{"projects":{},"x":"\ud800x"}""",
            )

        @JvmStatic
        @Suppress("LongMethod")
        fun mergeCases(): List<Arguments> =
            listOf(
                arguments(
                    "false entry with other fields and foreign root fields",
                    """{"numStartups":3,"projects":{"$KEY":{"hasTrustDialogAccepted":false,""" +
                        """"allowedTools":["Bash"]}},"x":{"y":[1,2]}}""",
                    """{"numStartups":3,"projects":{"$KEY":{"hasTrustDialogAccepted":true,""" +
                        """"allowedTools":["Bash"]}},"x":{"y":[1,2]}}""",
                ),
                arguments(
                    "no projects",
                    """{"numStartups":3}""",
                    """{"numStartups":3,"projects":{"$KEY":{"hasTrustDialogAccepted":true}}}""",
                ),
                arguments(
                    "other entries and no ours",
                    """{"projects":{"/other":{"hasTrustDialogAccepted":true,"n":1},"/third":{}}}""",
                    """{"projects":{"/other":{"hasTrustDialogAccepted":true,"n":1},"/third":{},""" +
                        """"$KEY":{"hasTrustDialogAccepted":true}}}""",
                ),
                arguments(
                    "false parent and our child key",
                    """{"projects":{"/Users/test/projects":{"hasTrustDialogAccepted":false}}}""",
                    """{"projects":{"/Users/test/projects":{"hasTrustDialogAccepted":false},""" +
                        """"$KEY":{"hasTrustDialogAccepted":true}}}""",
                ),
                arguments(
                    "entry without the field",
                    """{"projects":{"$KEY":{"allowedTools":[]}}}""",
                    """{"projects":{"$KEY":{"allowedTools":[],"hasTrustDialogAccepted":true}}}""",
                ),
                arguments(
                    "empty entry",
                    """{"projects":{"$KEY":{}}}""",
                    """{"projects":{"$KEY":{"hasTrustDialogAccepted":true}}}""",
                ),
            )
    }
}
