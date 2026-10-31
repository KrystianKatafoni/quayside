package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.A_PROJECT_DIR
import dev.agentmanager.agentruntime.api.ConversationId
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.WaitingFor
import dev.agentmanager.agentruntime.impl.ParseNotice.EntrySkipped
import dev.agentmanager.agentruntime.impl.ParseNotice.StartedAtMissing
import dev.agentmanager.agentruntime.impl.ParseNotice.UnknownPhase
import dev.agentmanager.core.domain.SessionId
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

class AgentsJsonFixturesTest {
    private val root: Path =
        Path.of(checkNotNull(javaClass.getResource("/fixtures/agents-json")) { "fixtures missing" }.toURI())

    private fun fixture(relative: String): String = root.resolve(relative).readText()

    private fun parseFixture(relative: String): AgentsListing = parseOk(fixture(relative))

    private fun fixtureFiles(): List<Path> =
        Files.walk(root).use { paths ->
            paths.asSequence().filter { it.isRegularFile() && it.name != "README.md" }.toList()
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["recorded/2.1.302-interactive-only.json", "recorded/2.1.302-all-interactive-only.json"])
    fun `recorded 2_1_302 output yields no sessions and reports the interactive entry`(file: String) {
        parseFixture(file) shouldBe
            AgentsListing(sessions = emptyList(), notices = listOf(EntrySkipped(0, SkipReason.INTERACTIVE)))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedBackground")
    fun `recorded 2_1_302 background output yields the recorded phases`(
        file: String,
        expected: List<Triple<String, RuntimePhase, WaitingFor?>>,
    ) {
        val listing = parseFixture(file)

        listing.sessions.map { Triple(it.id.value, it.phase, it.waitingFor) } shouldContainExactly expected
        listing.notices.filterIsInstance<UnknownPhase>().shouldBeEmpty()
    }

    @Test
    fun `recorded 2_1_302 sessions keep a pid only while their process lives`() {
        val done = recordedSession("recorded/2.1.302-all-background-done.json", "00000001")
        val stopped = recordedSession("recorded/2.1.302-all-background-done-after-stop.json", "00000001")

        done.pid shouldBe 4242L
        stopped.pid shouldBe null
        stopped.phase shouldBe RuntimePhase.DONE
    }

    private fun recordedSession(
        file: String,
        id: String,
    ): RuntimeSession = parseFixture(file).sessions.single { it.id.value == id }

    @Test
    fun `background-all-phases yields one session per phase`() {
        val listing = parseFixture("synthetic/background-all-phases.json")

        listing.sessions shouldContainExactly
            listOf(
                background("a1b2c3d4", 11, "fix-login", 0),
                background("b2c3d4e5", 12, "add-tests", 1).blocked(WaitingFor.Permission),
                background("c3d4e5f6", 13, "write-docs", 2).blocked(WaitingFor.Input),
                background("d4e5f6a7", 14, "refactor-ui", 3).copy(phase = RuntimePhase.DONE),
                background("e5f6a7b8", 15, "bump-deps", 4).copy(phase = RuntimePhase.FAILED),
                background("f6a7b8c9", 16, "old-spike", 5).copy(phase = RuntimePhase.STOPPED, pid = null),
            )
        listing.sessions.map { it.phase }.toSet() shouldBe RuntimePhase.entries.toSet()
        listing.notices.shouldBeEmpty()
    }

    @Test
    fun `mixed fixture keeps only background sessions`() {
        val listing = parseFixture("synthetic/mixed-interactive-and-background.json")

        listing.sessions.map { it.id.value } shouldContainExactly listOf("a1b2c3d4", "b2c3d4e5")
        listing.sessions[1].cwd shouldBe Path.of("/Users/test/projects/other")
        listing.sessions[1].phase shouldBe RuntimePhase.DONE
        listing.notices shouldContainExactly
            listOf(EntrySkipped(0, SkipReason.INTERACTIVE), EntrySkipped(2, SkipReason.INTERACTIVE))
    }

    @Test
    fun `unknown-fields-and-values parses with notices`() {
        val listing = parseFixture("synthetic/unknown-fields-and-values.json")

        listing.sessions shouldContainExactly
            listOf(
                background("a1b2c3d4", 31, "fix-login", 0),
                background("b2c3d4e5", 32, "add-tests", 1).blocked(WaitingFor.Other("oauth")),
            )
        listing.notices shouldContainExactly listOf(UnknownPhase(SessionId("a1b2c3d4"), "hibernating"))
    }

    @Test
    fun `partial-entries keeps valid entries in input order`() {
        val listing = parseFixture("synthetic/partial-entries.json")

        listing.sessions.map { it.id.value } shouldContainExactly listOf("a1b2c3d4", "c3d4e5f6", "d4e5f6a7")
        listing.sessions.map { it.startedAt } shouldContainExactly
            listOf(Instant.parse("2026-10-18T09:14:02Z"), Instant.ofEpochSecond(1_792_328_042L), OBSERVED_AT)
        listing.sessions[0].name shouldBe "iso-date"
        listing.sessions[2].conversationId shouldBe null
        listing.sessions[2].waitingFor shouldBe WaitingFor.Permission
        listing.notices shouldContainExactlyInAnyOrder
            listOf(
                EntrySkipped(1, SkipReason.MISSING_ID),
                EntrySkipped(2, SkipReason.INVALID_CWD),
                EntrySkipped(3, SkipReason.NOT_AN_OBJECT),
                EntrySkipped(5, SkipReason.DUPLICATE_ID),
                StartedAtMissing(SessionId("d4e5f6a7")),
            )
    }

    @Test
    fun `empty fixture yields an empty listing`() {
        parseFixture("synthetic/empty.json") shouldBe AgentsListing(emptyList(), emptyList())
    }

    @Test
    fun `not-an-array and malformed fixtures are errors with fixed reasons`() {
        parseErr(fixture("synthetic/not-an-array.json")).detail shouldBe UnparseableReason.NOT_AN_ARRAY
        parseErr(fixture("synthetic/malformed.txt")).detail shouldBe UnparseableReason.MALFORMED
    }

    @Test
    fun `every fixture file is listed in the README`() {
        val readme = fixture("README.md")
        val files = fixtureFiles()

        files.shouldNotBeEmpty()
        files.forEach { file ->
            readme shouldContain "`${root.relativize(file).invariantSeparatorsPathString}`"
        }
    }

    @Test
    fun `recorded fixtures are anonymized`() {
        val recorded = fixtureFiles().filter { root.relativize(it).startsWith("recorded") }
        val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val foreignUserDir = Regex("/Users/(?!test/)")

        recorded.shouldNotBeEmpty()
        recorded.forEach { file ->
            val text = file.readText()
            withClue(root.relativize(file).toString()) {
                text shouldNotContain "/home/"
                text shouldNotContain "/root"
                foreignUserDir.containsMatchIn(text) shouldBe false
                uuid.findAll(text).forEach { it.value shouldStartWith "00000000-0000-4000-8000-" }
            }
        }
    }

    /** A working background session from the synthetic fixtures; entry `n` starts `n` seconds after the first. */
    private fun background(
        id: String,
        conversation: Int,
        name: String,
        n: Int,
    ): RuntimeSession =
        RuntimeSession(
            id = SessionId(id),
            conversationId = ConversationId("00000000-0000-4000-8000-0000000000$conversation"),
            name = name,
            cwd = A_PROJECT_DIR,
            phase = RuntimePhase.WORKING,
            waitingFor = null,
            startedAt = Instant.ofEpochMilli(STARTED_AT_MILLIS + n * 1_000L),
            pid = 5001L + n,
        )

    private fun RuntimeSession.blocked(reason: WaitingFor): RuntimeSession =
        copy(phase = RuntimePhase.BLOCKED, waitingFor = reason)

    companion object {
        private val OTHER_DONE = Triple("00000003", RuntimePhase.DONE, null)

        @JvmStatic
        fun recordedBackground(): List<Arguments> =
            listOf(
                arguments(
                    "recorded/2.1.302-all-background-working.json",
                    listOf(OTHER_DONE, Triple("00000002", RuntimePhase.WORKING, null)),
                ),
                arguments(
                    "recorded/2.1.302-all-background-blocked-permission.json",
                    listOf(OTHER_DONE, Triple("00000002", RuntimePhase.BLOCKED, WaitingFor.Permission)),
                ),
                arguments(
                    "recorded/2.1.302-all-background-done.json",
                    listOf(OTHER_DONE, Triple("00000001", RuntimePhase.DONE, null)),
                ),
                arguments(
                    "recorded/2.1.302-all-background-done-after-stop.json",
                    listOf(OTHER_DONE, Triple("00000001", RuntimePhase.DONE, null)),
                ),
                arguments(
                    "recorded/2.1.302-all-background-respawned.json",
                    listOf(OTHER_DONE, Triple("00000001", RuntimePhase.DONE, null)),
                ),
                arguments(
                    "recorded/2.1.302-all-background-stopped.json",
                    listOf(OTHER_DONE, Triple("00000002", RuntimePhase.STOPPED, null)),
                ),
                arguments("recorded/2.1.302-background-stopped-hidden.json", emptyList<Any>()),
                arguments("recorded/2.1.302-all-background-removed.json", listOf(OTHER_DONE)),
            )
    }
}
