package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.A_PROJECT_DIR
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.agentruntime.impl.ParseNotice.EntrySkipped
import dev.quayside.agentruntime.impl.ParseNotice.StartedAtMissing
import dev.quayside.agentruntime.impl.ParseNotice.UnknownPhase
import dev.quayside.core.domain.SessionId
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.time.Instant
import kotlin.random.Random

class AgentsJsonParserTest {
    private val sessionId = SessionId(ID)

    /** The session [entry] with no overrides must turn into. */
    private val defaultSession: RuntimeSession =
        aRuntimeSession(
            id = sessionId,
            conversationId = ConversationId(CONVERSATION),
            name = "fix-login",
            cwd = A_PROJECT_DIR,
            phase = RuntimePhase.WORKING,
            waitingFor = null,
            startedAt = Instant.ofEpochMilli(STARTED_AT_MILLIS),
            pid = 4242L,
        )

    // --- Document and entries ---

    @ParameterizedTest(name = "[{0}] -> {1}")
    @MethodSource("unusableDocuments")
    fun `unusable document is an error with a fixed reason`(
        stdout: String,
        reason: String,
    ) {
        parseErr(stdout).detail shouldBe reason
    }

    @Test
    fun `empty array yields no sessions and no notices`() {
        parseOk("[]") shouldBe AgentsListing(sessions = emptyList(), notices = emptyList())
    }

    @Test
    fun `valid entry maps every field`() {
        parseOk(document(entry())) shouldBe AgentsListing(listOf(defaultSession), emptyList())
    }

    @ParameterizedTest(name = "element {0}")
    @ValueSource(strings = ["42", "\"text\"", "null", "true", "[]", "[{\"id\":\"x\"}]"])
    fun `element that is not an object is skipped`(element: String) {
        val listing = parseOk("[$element, ${entry()}]")

        listing.sessions shouldContainExactly listOf(defaultSession)
        listing.notices shouldContainExactly listOf(EntrySkipped(0, SkipReason.NOT_AN_OBJECT))
    }

    @Test
    fun `unknown fields are ignored`() {
        val withExtras =
            entry(
                "model" to "opus",
                "worktree" to JsonObject(mapOf("path" to JsonPrimitive("/Users/test/wt"))),
                "tags" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive("a"))),
                "futureFlag" to true,
            )

        parseOk(document(withExtras)) shouldBe AgentsListing(listOf(defaultSession), emptyList())
    }

    @Test
    fun `input above the limit is rejected before parsing`() {
        val padding = " ".repeat(AgentsJsonParser.MAX_INPUT_CHARS - 1)
        // A valid document one character over the limit, so only the size check can reject it.
        val tooLarge = "[$padding]"
        val atLimit = "[${padding.drop(1)}]"

        tooLarge.length shouldBe AgentsJsonParser.MAX_INPUT_CHARS + 1
        parseErr(tooLarge).detail shouldBe UnparseableReason.TOO_LARGE
        parseErr("{$padding ").detail shouldBe UnparseableReason.TOO_LARGE
        parseOk(atLimit).sessions.shouldBeEmpty()
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(
        strings = [
            "{\"token\":\"secret-token\",\"cwd\":\"/Users/alice/secret-project\"",
            "{\"secret-token\":\"/Users/alice/secret-project\"}",
            "secret-token /Users/alice/secret-project",
            "[{\"id\":\"secret-token\",\"cwd\":\"/Users/alice/secret-project\"},]",
            "   \n",
        ],
    )
    fun `unparseable detail never contains input text`(stdout: String) {
        val detail = parseErr(stdout).detail

        detail shouldBeIn ALL_UNPARSEABLE_REASONS
        detail shouldNotContain "secret"
        detail shouldNotContain "alice"
    }

    @Test
    fun `parse never throws`() {
        val random = Random(seed = 202)
        val valid =
            document(
                entry(),
                entry("id" to "b2c3d4e5", "state" to "blocked", "waitingFor" to "permission"),
                entry("id" to "c3d4e5f6", "kind" to "interactive"),
                entry("id" to Absent, "cwd" to "rel"),
            )
        val inputs =
            List(20) { valid.take(random.nextInt(valid.length + 1)) } +
                List(15) { randomGarbage(random) } +
                List(15) { mutate(valid, random) }

        inputs.forEach { input -> shouldNotThrowAny { AgentsJsonParser.parse(input, OBSERVED_AT) } }
    }

    // --- Interactive sessions, ids, duplicates ---

    @Test
    fun `interactive session is skipped`() {
        val listing = parseOk(document(entry("kind" to "interactive"), entry("id" to "b2c3d4e5")))

        listing.sessions shouldContainExactly listOf(defaultSession.copy(id = SessionId("b2c3d4e5")))
        listing.notices shouldContainExactly listOf(EntrySkipped(0, SkipReason.INTERACTIVE))
    }

    @ParameterizedTest(name = "kind = {0}")
    @MethodSource("nonInteractiveKinds")
    fun `entry that is not interactive is kept`(kind: Any) {
        parseOk(document(entry("kind" to kind))).sessions shouldContainExactly listOf(defaultSession)
    }

    @ParameterizedTest(name = "id = {0}")
    @MethodSource("missingIds")
    fun `entry without id is skipped and the rest is kept`(id: Any?) {
        val listing = parseOk(document(entry("id" to id), entry("id" to "b2c3d4e5")))

        listing.sessions shouldContainExactly listOf(defaultSession.copy(id = SessionId("b2c3d4e5")))
        listing.notices shouldContainExactly listOf(EntrySkipped(0, SkipReason.MISSING_ID))
    }

    @Test
    fun `duplicate id keeps the first entry`() {
        val listing = parseOk(document(entry("name" to "first"), entry("name" to "second", "state" to "done")))

        listing.sessions shouldContainExactly listOf(defaultSession.copy(name = "first"))
        listing.notices shouldContainExactly listOf(EntrySkipped(1, SkipReason.DUPLICATE_ID))
    }

    @Test
    fun `sessions keep input order`() {
        val ids = listOf("c3d4e5f6", "a1b2c3d4", "b2c3d4e5")

        parseOk(document(*ids.map { entry("id" to it) }.toTypedArray())).sessions.map { it.id.value } shouldBe ids
    }

    // --- cwd ---

    @ParameterizedTest(name = "cwd = {0}")
    @MethodSource("invalidCwds")
    fun `entry with an unusable cwd is skipped`(cwd: Any?) {
        val listing = parseOk(document(entry("cwd" to cwd)))

        listing.sessions.shouldBeEmpty()
        listing.notices shouldContainExactly listOf(EntrySkipped(0, SkipReason.INVALID_CWD))
    }

    @ParameterizedTest(name = "cwd = {0}")
    @ValueSource(
        strings = [
            "/Users/test/projects/agent-manager",
            "/Users/test/projects/other/../agent-manager",
            "/Users/test/./projects/agent-manager",
            "/Users/test/projects//agent-manager",
        ],
    )
    fun `absolute cwd is normalized`(cwd: String) {
        parseSingle(entry("cwd" to cwd)).sessions.single().cwd shouldBe Path.of("/Users/test/projects/agent-manager")
    }

    // --- Phases ---

    @ParameterizedTest(name = "state = [{0}] -> {1}")
    @MethodSource("knownPhases")
    fun `known state maps to its phase`(
        token: String,
        phase: RuntimePhase,
    ) {
        val listing = parseSingle(entry("state" to token))

        listing.sessions.single().phase shouldBe phase
        listing.notices.shouldBeEmpty()
    }

    @ParameterizedTest(name = "status = [{0}] -> {1}")
    @MethodSource("knownPhases")
    fun `status is used when state is absent`(
        token: String,
        phase: RuntimePhase,
    ) {
        val listing = parseSingle(entry("state" to Absent, "status" to token))

        listing.sessions.single().phase shouldBe phase
        listing.notices.shouldBeEmpty()
    }

    @Test
    fun `state wins over status`() {
        parseSingle(entry("state" to "blocked", "status" to "busy")).sessions.single().phase shouldBe
            RuntimePhase.BLOCKED
        parseSingle(entry("state" to "done", "status" to "failed")).sessions.single().phase shouldBe
            RuntimePhase.DONE
    }

    @Test
    fun `unknown state maps to working and is reported`() {
        val listing = parseSingle(entry("state" to "hibernating"))

        listing.sessions.single() shouldBe defaultSession.copy(phase = RuntimePhase.WORKING)
        listing.notices shouldContainExactly listOf(UnknownPhase(sessionId, "hibernating"))
    }

    @Test
    fun `missing state and status map to working and are reported with no token`() {
        val listing = parseSingle(entry("state" to Absent))

        listing.sessions.single().phase shouldBe RuntimePhase.WORKING
        listing.notices shouldContainExactly listOf(UnknownPhase(sessionId, null))
    }

    @Test
    fun `unknown state with waitingFor maps to blocked`() {
        val listing = parseSingle(entry("state" to "hibernating", "waitingFor" to "permission"))

        listing.sessions.single() shouldBe
            defaultSession.copy(phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Permission)
        listing.notices shouldContainExactly listOf(UnknownPhase(sessionId, "hibernating"))
    }

    @Test
    fun `missing state with waitingFor maps to blocked`() {
        val listing = parseSingle(entry("state" to Absent, "waitingFor" to "input"))

        listing.sessions.single().phase shouldBe RuntimePhase.BLOCKED
        listing.sessions.single().waitingFor shouldBe WaitingFor.Input
        listing.notices shouldContainExactly listOf(UnknownPhase(sessionId, null))
    }

    @ParameterizedTest(name = "state = [{0}]")
    @MethodSource("unsafeTokens")
    fun `unknown phase token that is not a safe token is redacted`(token: String) {
        val listing = parseSingle(entry("state" to token))

        listing.sessions.single().phase shouldBe RuntimePhase.WORKING
        listing.notices shouldContainExactly listOf(UnknownPhase(sessionId, "<redacted>"))
    }

    @ParameterizedTest(name = "state = [{0}]")
    @ValueSource(strings = ["hibernating", "Paused", "queued_2", "x", "sleeping deeply", "a-b-c"])
    fun `unknown phase token that is a safe token is reported as is`(token: String) {
        parseSingle(entry("state" to token)).notices shouldContainExactly listOf(UnknownPhase(sessionId, token))
    }

    // --- waitingFor ---

    @ParameterizedTest(name = "waitingFor = {0} -> {1}")
    @MethodSource("waitingForValues")
    fun `waitingFor of a blocked session maps to its reason`(
        raw: Any?,
        expected: WaitingFor?,
    ) {
        val listing = parseSingle(entry("state" to "blocked", "waitingFor" to raw))

        listing.sessions.single().phase shouldBe RuntimePhase.BLOCKED
        listing.sessions.single().waitingFor shouldBe expected
        listing.notices.shouldBeEmpty()
    }

    @ParameterizedTest(name = "state = {0}")
    @ValueSource(strings = ["working", "done", "failed", "stopped"])
    fun `waitingFor is dropped outside blocked`(state: String) {
        parseSingle(entry("state" to state, "waitingFor" to "permission")).sessions.single().waitingFor shouldBe null
    }

    // --- startedAt ---

    @ParameterizedTest(name = "startedAt = {0}")
    @MethodSource("readableStartedAt")
    fun `startedAt is read from millis, seconds or ISO-8601`(
        raw: Any,
        expected: Instant,
    ) {
        val listing = parseSingle(entry("startedAt" to raw))

        listing.sessions.single().startedAt shouldBe expected
        listing.notices.shouldBeEmpty()
    }

    @ParameterizedTest(name = "startedAt = {0}")
    @MethodSource("unreadableStartedAt")
    fun `unreadable startedAt falls back to observedAt and is reported`(raw: Any?) {
        val listing = parseSingle(entry("startedAt" to raw))

        listing.sessions.single().startedAt shouldBe OBSERVED_AT
        listing.notices shouldContainExactly listOf(StartedAtMissing(sessionId))
    }

    @Test
    fun `every notice about one entry is reported`() {
        val listing = parseSingle(entry("state" to "hibernating", "startedAt" to Absent))

        listing.notices shouldContainExactlyInAnyOrder
            listOf(UnknownPhase(sessionId, "hibernating"), StartedAtMissing(sessionId))
    }

    // --- pid, name, sessionId ---

    @ParameterizedTest(name = "pid = {0} -> {1}")
    @MethodSource("pids")
    fun `pid is a positive whole number or null`(
        raw: Any?,
        expected: Long?,
    ) {
        parseSingle(entry("pid" to raw)).sessions.single().pid shouldBe expected
    }

    @ParameterizedTest(name = "state = {0}")
    @ValueSource(strings = ["stopped", "exited", "killed", "terminated"])
    fun `stopped session has no pid`(state: String) {
        parseSingle(entry("state" to state, "pid" to 4242)).sessions.single().pid shouldBe null
    }

    @ParameterizedTest(name = "name = {0}")
    @MethodSource("missingNames")
    fun `missing name falls back to the id`(name: Any?) {
        parseSingle(entry("name" to name)).sessions.single().name shouldBe ID
    }

    @ParameterizedTest(name = "sessionId = {0}")
    @MethodSource("missingConversationIds")
    fun `missing sessionId leaves conversationId null`(conversationId: Any?) {
        val listing = parseSingle(entry("sessionId" to conversationId))

        listing.sessions.single().conversationId shouldBe null
        listing.notices.shouldBeEmpty()
    }

    companion object {
        @JvmStatic
        fun unusableDocuments(): List<Arguments> =
            listOf(
                arguments("", UnparseableReason.EMPTY),
                arguments("   ", UnparseableReason.EMPTY),
                arguments(" \n\t\r\n", UnparseableReason.EMPTY),
                arguments("[", UnparseableReason.MALFORMED),
                arguments("[{\"id\": \"7c5dcf5d\",", UnparseableReason.MALFORMED),
                arguments("not json at all", UnparseableReason.MALFORMED),
                arguments("[1,]", UnparseableReason.MALFORMED),
                arguments("Error: supervisor not running", UnparseableReason.MALFORMED),
                arguments("{}", UnparseableReason.NOT_AN_ARRAY),
                arguments("{\"agents\": []}", UnparseableReason.NOT_AN_ARRAY),
                arguments("\"text\"", UnparseableReason.NOT_AN_ARRAY),
                arguments("42", UnparseableReason.NOT_AN_ARRAY),
                arguments("true", UnparseableReason.NOT_AN_ARRAY),
                arguments("null", UnparseableReason.NOT_AN_ARRAY),
            )

        @JvmStatic
        fun nonInteractiveKinds(): List<Any> = listOf("background", "bg", "daemon", "", 7, Absent)

        @JvmStatic
        fun missingIds(): List<Any?> = listOf(Absent, "", " ", null, 42, true)

        @JvmStatic
        fun invalidCwds(): List<Any?> =
            listOf(
                Absent,
                null,
                42,
                "",
                "projects/agent-manager",
                "./agent-manager",
                "~/projects/agent-manager",
                "\u0000",
                "/Users/test/\u0000/agent-manager",
            )

        @JvmStatic
        fun knownPhases(): List<Arguments> =
            listOf(
                "working" to RuntimePhase.WORKING,
                "busy" to RuntimePhase.WORKING,
                "running" to RuntimePhase.WORKING,
                "active" to RuntimePhase.WORKING,
                "blocked" to RuntimePhase.BLOCKED,
                "waiting" to RuntimePhase.BLOCKED,
                "needs_input" to RuntimePhase.BLOCKED,
                "done" to RuntimePhase.DONE,
                "idle" to RuntimePhase.DONE,
                "completed" to RuntimePhase.DONE,
                "finished" to RuntimePhase.DONE,
                "failed" to RuntimePhase.FAILED,
                "error" to RuntimePhase.FAILED,
                "errored" to RuntimePhase.FAILED,
                "crashed" to RuntimePhase.FAILED,
                "stopped" to RuntimePhase.STOPPED,
                "exited" to RuntimePhase.STOPPED,
                "killed" to RuntimePhase.STOPPED,
                "terminated" to RuntimePhase.STOPPED,
                // Normalization: trim, lowercase, `-` and space as `_`.
                "Working" to RuntimePhase.WORKING,
                " BUSY " to RuntimePhase.WORKING,
                "needs-input" to RuntimePhase.BLOCKED,
                "needs input" to RuntimePhase.BLOCKED,
                "Needs_Input" to RuntimePhase.BLOCKED,
                "DONE\n" to RuntimePhase.DONE,
            ).map { (token, phase) -> arguments(token, phase) }

        @JvmStatic
        fun unsafeTokens(): List<String> =
            listOf(
                "../../etc/passwd",
                "/Users/alice/secret",
                "1abc",
                "_hidden",
                "a".repeat(33),
                "secret\nline",
                "tab\there",
                "zażółć",
                "{\"x\":1}",
            )

        @JvmStatic
        fun waitingForValues(): List<Arguments> =
            listOf(
                arguments("permission", WaitingFor.Permission),
                arguments("permission_prompt", WaitingFor.Permission),
                arguments("permission prompt", WaitingFor.Permission),
                arguments("Permission-Prompt", WaitingFor.Permission),
                arguments("input", WaitingFor.Input),
                arguments("input_needed", WaitingFor.Input),
                arguments("input needed", WaitingFor.Input),
                arguments("idle_prompt", WaitingFor.Input),
                arguments("agent_needs_input", WaitingFor.Input),
                arguments("oauth", WaitingFor.Other("oauth")),
                arguments("a".repeat(100), WaitingFor.Other("a".repeat(64))),
                arguments(Absent, null),
                arguments("", null),
                arguments(null, null),
                arguments(5, null),
                arguments(JsonArray(listOf(JsonPrimitive("permission"))), null),
            )

        @JvmStatic
        fun readableStartedAt(): List<Arguments> =
            listOf(
                arguments(STARTED_AT_MILLIS, Instant.ofEpochMilli(STARTED_AT_MILLIS)),
                arguments(100_000_000_000L, Instant.ofEpochMilli(100_000_000_000L)),
                arguments(1_792_328_042L, Instant.ofEpochSecond(1_792_328_042L)),
                arguments(99_999_999_999L, Instant.ofEpochSecond(99_999_999_999L)),
                arguments("2026-10-18T09:14:02Z", Instant.parse("2026-10-18T09:14:02Z")),
                arguments("2026-10-18T09:14:02.940Z", Instant.parse("2026-10-18T09:14:02.940Z")),
            )

        @JvmStatic
        fun unreadableStartedAt(): List<Any?> =
            listOf(Absent, null, "last tuesday", "", "2026-13-45T99:00:00Z", true, JsonArray(emptyList()))

        @JvmStatic
        fun pids(): List<Arguments> =
            listOf(
                arguments(4242, 4242L),
                arguments(1, 1L),
                arguments(1L shl 40, 1L shl 40),
                arguments(0, null),
                arguments(-1, null),
                arguments(42.5, null),
                arguments("4242", null),
                arguments(null, null),
                arguments(Absent, null),
                arguments(true, null),
            )

        @JvmStatic
        fun missingNames(): List<Any?> = listOf(Absent, "", null, 5, false)

        @JvmStatic
        fun missingConversationIds(): List<Any?> = listOf(Absent, "", " ", null, 7)

        private fun randomGarbage(random: Random): String {
            val alphabet = "[]{}\",:0123456789.eE+-truefalsnl \n\\u/abcxyz"
            return String(CharArray(random.nextInt(1, 200)) { alphabet[random.nextInt(alphabet.length)] })
        }

        private fun mutate(
            text: String,
            random: Random,
        ): String {
            val chars = text.toCharArray()
            val replacements = "[]{}\",:0x\u0000"
            repeat(random.nextInt(1, 6)) {
                chars[random.nextInt(chars.size)] = replacements[random.nextInt(replacements.length)]
            }
            return String(chars)
        }
    }
}
