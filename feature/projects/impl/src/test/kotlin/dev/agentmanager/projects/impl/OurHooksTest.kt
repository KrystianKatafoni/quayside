package dev.agentmanager.projects.impl

import dev.agentmanager.agentruntime.api.HookKind
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.projects.api.HooksPresence
import dev.agentmanager.projects.impl.HookSettings.FOREIGN_HANDLER
import dev.agentmanager.projects.impl.HookSettings.ourHandler
import dev.agentmanager.projects.impl.HookSettings.settings
import dev.agentmanager.projects.impl.HookSettings.settingsWithEvents
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

/** Recognition of our hooks (ADR 0010) and combining both settings files (AM-303 plan, decision 6). */
internal class OurHooksTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("recognized")
    fun `kinds finds our hooks by type, url and token header`(
        case: String,
        settingsText: String,
        expected: Set<HookKind>,
    ) {
        withClue(case) { OurHooks.kinds(settingsText) shouldBe Outcome.Ok(expected) }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(HookKind::class)
    fun `every hook kind is recognized by its event name`(kind: HookKind) {
        OurHooks.kinds(settings(listOf(kind))) shouldBe Outcome.Ok(setOf(kind))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("broken")
    fun `broken settings give a fixed category without the input`(
        case: String,
        settingsText: String,
    ) {
        withClue(case) {
            val error = OurHooks.kinds(settingsText).shouldBeInstanceOf<Outcome.Err<String>>().error
            error.shouldNotBeBlank()
            error shouldNotContain SECRET
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presences")
    fun `presence combines both files`(
        case: String,
        reads: List<SettingsRead>,
        expected: HooksPresence,
    ) {
        withClue(case) { OurHooks.presence(reads) shouldBe expected }
    }

    companion object {
        private const val SECRET = "secret-user"
        private const val OTHER_HOST = "http://example.com:47321/hooks"
        private val ALL = HookKind.entries.toTypedArray()
        private val NONE = HooksPresence.NONE
        private val PARTIAL = HooksPresence.PARTIAL
        private val INSTALLED = HooksPresence.INSTALLED
        private val UNREADABLE = HooksPresence.UNREADABLE

        private fun stop(handler: String): String = settingsWithEvents(listOf("Stop"), handler)

        private fun case(
            name: String,
            settingsText: String,
            vararg kinds: HookKind,
        ): Arguments = arguments(name, settingsText, kinds.toSet())

        private fun text(vararg kinds: HookKind): SettingsRead = SettingsRead.Text(settings(kinds.toList()))

        private fun reads(
            name: String,
            vararg reads: SettingsRead,
            expected: HooksPresence,
        ): Arguments = arguments(name, reads.toList(), expected)

        @JvmStatic
        fun recognized(): List<Arguments> = ours() + notOurs() + shapes()

        private fun ours(): List<Arguments> =
            listOf(
                case("our handler in Stop", stop(ourHandler()), HookKind.STOP),
                case("another port", stop(ourHandler(url = "http://127.0.0.1:50000/hooks")), HookKind.STOP),
                case("localhost", stop(ourHandler(url = "http://localhost:47321/hooks")), HookKind.STOP),
                case("lower-case token header", stop(ourHandler(tokenHeader = "x-am-token")), HookKind.STOP),
                case("next to a foreign handler", stop("$FOREIGN_HANDLER, ${ourHandler()}"), HookKind.STOP),
                case(
                    "with a matcher",
                    """{"hooks": {"Notification": [{"matcher": "idle_prompt", "hooks": [${ourHandler()}]}]}}""",
                    HookKind.NOTIFICATION,
                ),
                case("all five kinds", settings(HookKind.entries), *HookKind.entries.toTypedArray()),
            )

        private fun notOurs(): List<Arguments> =
            listOf(
                case("without the token header", stop("""{"type": "http", "url": "http://127.0.0.1:47321/hooks"}""")),
                case("other header", stop(ourHandler(tokenHeader = "Authorization"))),
                case("type command", stop(ourHandler(type = "command"))),
                case("foreign command handler", stop(FOREIGN_HANDLER)),
                case("host example.com", stop(ourHandler(url = OTHER_HOST))),
                case("https scheme", stop(ourHandler(url = "https://127.0.0.1:47321/hooks"))),
                case("path /other", stop(ourHandler(url = "http://127.0.0.1:47321/other"))),
                case("event PreToolUse", settingsWithEvents(listOf("PreToolUse"), ourHandler())),
            )

        private fun shapes(): List<Arguments> =
            listOf(
                case("empty object", "{}"),
                case("no hooks key", """{"permissions": {"allow": []}}"""),
                case("event is not an array", """{"hooks": {"Stop": 1}}"""),
                case("handler is not an object", """{"hooks": {"Stop": [{"hooks": ["x"]}]}}"""),
                case(
                    "broken event next to a good one",
                    """{"hooks": {"Stop": 1, "SessionEnd": [{"hooks": [${ourHandler()}]}]}}""",
                    HookKind.SESSION_END,
                ),
            )

        @JvmStatic
        fun broken(): List<Arguments> =
            listOf(
                arguments("invalid JSON", "{oops $SECRET"),
                arguments("root is an array", """["$SECRET"]"""),
                arguments("hooks is an array", """{"hooks": ["$SECRET"]}"""),
            )

        @JvmStatic
        fun presences(): List<Arguments> = withoutProblems() + withProblems()

        private fun withoutProblems(): List<Arguments> =
            listOf(
                reads("both files missing", SettingsRead.Missing, SettingsRead.Missing, expected = NONE),
                reads(
                    "only foreign hooks",
                    SettingsRead.Text(stop(FOREIGN_HANDLER)),
                    SettingsRead.Missing,
                    expected = NONE,
                ),
                reads(
                    "five kinds split three and two",
                    text(HookKind.NOTIFICATION, HookKind.STOP, HookKind.STOP_FAILURE),
                    text(HookKind.SESSION_START, HookKind.SESSION_END),
                    expected = INSTALLED,
                ),
                reads("all five in the local file", SettingsRead.Missing, text(*ALL), expected = INSTALLED),
                reads(
                    "two of five",
                    text(HookKind.STOP, HookKind.NOTIFICATION),
                    SettingsRead.Missing,
                    expected = PARTIAL,
                ),
                reads(
                    "same kinds in both files",
                    text(HookKind.STOP, HookKind.NOTIFICATION),
                    text(HookKind.STOP, HookKind.NOTIFICATION),
                    expected = PARTIAL,
                ),
            )

        private fun withProblems(): List<Arguments> =
            listOf(
                reads("unreadable wins", SettingsRead.Unreadable("io"), text(*ALL), expected = UNREADABLE),
                reads("unparseable wins", text(*ALL), SettingsRead.Text("{oops"), expected = UNREADABLE),
            )
    }
}
