package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.HookKind
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Pure edits of settings JSON. The recognition table repeats the one of `OurHooksTest` (ADR 0010). */
class HookSettingsJsonTest {
    private fun root(text: String): JsonObject = (HookSettingsJson.parse(text) as Outcome.Ok).value

    @ParameterizedTest(name = "{0}")
    @MethodSource("recognition")
    @Suppress("UnusedParameter") // label only names the case in the report.
    fun `recognizes our handlers as ADR 0010 says`(
        label: String,
        settings: String,
        expected: Set<HookKind>,
    ) {
        HookSettingsJson.kinds(root(settings)) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unusable")
    @Suppress("UnusedParameter") // label only names the case in the report.
    fun `names a fixed category for a file it cannot use`(
        label: String,
        settings: String,
        category: String,
    ) {
        HookSettingsJson.parse(settings) shouldBe Outcome.Err(category)
    }

    @Test
    fun `adds a handler of the documented shape for every kind`() {
        val updated = HookSettingsJson.withOurHooks(root("{}"), port = 50000)

        HookSettingsJson.kinds(updated) shouldBe HookKind.entries.toSet()
        val text = HookSettingsJson.render(updated)
        text.contains("\"url\": \"http://127.0.0.1:50000/hooks\"") shouldBe true
        text.contains("\"X-AM-Token\": \"\$AM_HOOK_TOKEN\"") shouldBe true
        text.contains("\"allowedEnvVars\"") shouldBe true
    }

    @Test
    fun `keeps foreign settings, hooks and key order`() {
        val before = root(FOREIGN)

        val updated = HookSettingsJson.withOurHooks(before, port = 47321)

        updated.keys.toList() shouldBe listOf("model", "hooks", "permissions")
        HookSettingsJson.render(HookSettingsJson.withoutOurHooks(updated)) shouldBe HookSettingsJson.render(before)
    }

    @Test
    fun `adding twice changes nothing`() {
        val once = HookSettingsJson.withOurHooks(root(FOREIGN), port = 47321)

        (HookSettingsJson.withOurHooks(once, port = 47321) === once) shouldBe true
    }

    @Test
    fun `completes a partial install without doubling the present kinds`() {
        val partial = root(settings(OURS_STOP))

        val updated = HookSettingsJson.withOurHooks(partial, port = 47321)

        HookSettingsJson.kinds(updated) shouldBe HookKind.entries.toSet()
        HookSettingsJson.render(updated).split("\"X-AM-Token\"").size - 1 shouldBe HookKind.entries.size
    }

    @Test
    fun `removing ours drops groups, events and hooks that become empty`() {
        val installed = HookSettingsJson.withOurHooks(root("""{"model": "x"}"""), port = 47321)

        HookSettingsJson.render(HookSettingsJson.withoutOurHooks(installed)) shouldBe
            HookSettingsJson.render(root("""{"model": "x"}"""))
    }

    @Test
    fun `removing ours leaves a foreign handler in the same group`() {
        val mixed = root(settings("""{"hooks": [$FOREIGN_HANDLER, $OURS_HANDLER]}"""))

        val cleaned = HookSettingsJson.withoutOurHooks(mixed)

        HookSettingsJson.kinds(cleaned) shouldBe emptySet()
        HookSettingsJson.render(cleaned).contains("notify.sh") shouldBe true
    }

    @Test
    fun `removing ours from a file without them returns the same object`() {
        val plain = root(FOREIGN)

        (HookSettingsJson.withoutOurHooks(plain) === plain) shouldBe true
    }

    companion object {
        private const val OURS_HANDLER =
            """{"type": "http", "url": "http://127.0.0.1:47321/hooks", "headers": {"X-AM-Token": "${'$'}AM_HOOK_TOKEN"}}"""
        private const val FOREIGN_HANDLER = """{"type": "command", "command": "notify.sh"}"""
        private const val OURS_STOP = """{"hooks": [$OURS_HANDLER]}"""
        private const val FOREIGN =
            """{"model": "opus", "hooks": {"Stop": [{"hooks": [$FOREIGN_HANDLER]}], "PreToolUse": []}, "permissions": {"allow": ["Bash"]}}"""

        private fun settings(stopGroup: String) = """{"hooks": {"Stop": [$stopGroup]}}"""

        private fun case(
            label: String,
            text: String,
            vararg kinds: HookKind,
        ) = arguments(label, text, kinds.toSet())

        @JvmStatic
        fun recognition(): List<Arguments> =
            listOf(
                case("our handler in Stop", settings(OURS_STOP), HookKind.STOP),
                case("another port", settings(OURS_STOP.replace("47321", "50000")), HookKind.STOP),
                case("localhost", settings(OURS_STOP.replace("127.0.0.1", "localhost")), HookKind.STOP),
                case("lower-case header", settings(OURS_STOP.replace("X-AM-Token", "x-am-token")), HookKind.STOP),
                case(
                    "next to a foreign handler",
                    settings("""{"hooks": [$FOREIGN_HANDLER, $OURS_HANDLER]}"""),
                    HookKind.STOP,
                ),
                case(
                    "without the token header",
                    settings("""{"hooks": [{"type": "http", "url": "http://127.0.0.1:47321/hooks"}]}"""),
                ),
                case("other header", settings(OURS_STOP.replace("X-AM-Token", "Authorization"))),
                case("type command", settings(OURS_STOP.replace("\"http\"", "\"command\""))),
                case("host example.com", settings(OURS_STOP.replace("127.0.0.1", "example.com"))),
                case("https scheme", settings(OURS_STOP.replace("http://", "https://"))),
                case("path /other", settings(OURS_STOP.replace("/hooks\"", "/other\""))),
                case("path with trailing slash", settings(OURS_STOP.replace("/hooks\"", "/hooks/\""))),
                case("ipv6 host", settings(OURS_STOP.replace("127.0.0.1", "[::1]"))),
                case("no hooks key", """{"permissions": {}}"""),
                case("handler is not an object", """{"hooks": {"Stop": [{"hooks": ["x"]}]}}"""),
            )

        @JvmStatic
        fun unusable(): List<Arguments> =
            listOf(
                arguments("invalid JSON", "{oops secret-user", SettingsProblem.INVALID_JSON),
                arguments("empty", "", SettingsProblem.INVALID_JSON),
                arguments("root is an array", """["secret-user"]""", SettingsProblem.ROOT_NOT_OBJECT),
                arguments("hooks is an array", """{"hooks": ["secret-user"]}""", SettingsProblem.HOOKS_NOT_OBJECT),
                arguments("hooks is null", """{"hooks": null}""", SettingsProblem.HOOKS_NOT_OBJECT),
                arguments("event is not an array", """{"hooks": {"Stop": 1}}""", SettingsProblem.EVENT_NOT_ARRAY),
            )
    }
}
