package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aPastConversation
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.ProjectHistory
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.pastSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** `DiskSessions.merge` (FR-P9, ADR 0032): the table of the AM-312 plan. Pure, so no coroutines. */
class DiskSessionsTest {
    private val soft = ProjectId("soft")
    private val root = ProjectId("root")
    private val rootDir = Path.of("/p")
    private val softDir = Path.of("/p/soft")
    private val projects = mapOf(root to rootDir, soft to softDir)

    private fun uuid(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    private fun past(
        n: Int,
        cwd: Path = rootDir,
        modified: Long = n.toLong(),
        started: Long? = 0,
        title: String? = "title $n",
    ): PastConversation =
        aPastConversation(
            conversationId = aConversationId(uuid(n)),
            startCwd = cwd,
            startedAt = started?.let { T0.plusSeconds(it) },
            lastActivityAt = T0.plusSeconds(modified),
            modifiedAt = T0.plusSeconds(modified),
            title = title,
        )

    private fun merge(
        vararg past: PastConversation,
        supervisor: List<RuntimeSession> = emptyList(),
        settings: SessionHistorySettings = SessionHistorySettings(),
    ): DiskMerge = DiskSessions.merge(past.toList(), supervisor, projects, settings)

    private fun ids(merge: DiskMerge): Set<String> = merge.sessions.map { it.id.value }.toSet()

    @Test
    fun `a conversation the supervisor knows by its UUID is left out`() {
        val known = aRuntimeSession(id = SessionId("zzzz0001"), conversationId = aConversationId(uuid(1)))

        ids(merge(past(1), past(2), supervisor = listOf(known))) shouldBe setOf(uuid(2))
    }

    @Test
    fun `a conversation whose UUID starts with the short id of a session without conversation id is left out`() {
        val known = aRuntimeSession(id = SessionId("00000000"), conversationId = null)

        merge(past(1), supervisor = listOf(known)).sessions.shouldBeEmpty()
    }

    @Test
    fun `a short id that is not the start of the UUID leaves the conversation in`() {
        val other = aRuntimeSession(id = SessionId("aaaaaaaa"), conversationId = null)

        ids(merge(past(1), supervisor = listOf(other))) shouldBe setOf(uuid(1))
    }

    @Test
    fun `the deepest project owns the session`() {
        val result = merge(past(1, cwd = softDir.resolve("api")), past(2, cwd = rootDir.resolve("docs")))

        result.sessions.associate { it.id.value to it.project } shouldBe mapOf(uuid(1) to soft, uuid(2) to root)
        result.history shouldBe mapOf(soft to ProjectHistory(1, 0), root to ProjectHistory(1, 0))
    }

    @Test
    fun `a session that started outside every project is left out and not counted`() {
        val result = merge(past(1, cwd = Path.of("/elsewhere")))

        result.sessions.shouldBeEmpty()
        result.history shouldBe mapOf(soft to ProjectHistory(0, 0), root to ProjectHistory(0, 0))
    }

    @Test
    fun `a sibling directory with a common name prefix is not in the project`() {
        merge(past(1, cwd = Path.of("/p-old"))).sessions.shouldBeEmpty()
    }

    @Test
    fun `over the count limit the newest by modifiedAt stay, not the newest by startedAt`() {
        // started is opposite to modified: conversation 1 started last but was modified first
        val all = (1..LIMIT + 1).map { past(it, modified = it.toLong(), started = (LIMIT + 2 - it).toLong()) }

        val result =
            merge(*all.toTypedArray(), settings = SessionHistorySettings(maxPerProject = HistoryLimit.SESSIONS_50))

        ids(result) shouldBe (2..LIMIT + 1).map(::uuid).toSet()
        result.history.getValue(root) shouldBe ProjectHistory(shown = LIMIT, older = 1)
    }

    @Test
    fun `the count limit applies to each project on its own`() {
        val inRoot = (1..LIMIT + 2).map { past(it) }
        val inSoft = (101..101 + LIMIT + 2).map { past(it, cwd = softDir) }
        val settings = SessionHistorySettings(maxPerProject = HistoryLimit.SESSIONS_50)

        val result = merge(*(inRoot + inSoft).toTypedArray(), settings = settings)

        result.history shouldBe mapOf(root to ProjectHistory(LIMIT, 2), soft to ProjectHistory(LIMIT, 3))
    }

    @Test
    fun `a hidden session is left out and not counted as older`() {
        val all = (1..LIMIT + 1).map { past(it) }
        val settings =
            SessionHistorySettings(maxPerProject = HistoryLimit.SESSIONS_50, hidden = setOf(SessionId(uuid(LIMIT + 1))))

        val result = merge(*all.toTypedArray(), settings = settings)

        result.history.getValue(root) shouldBe ProjectHistory(shown = LIMIT, older = 0)
        (uuid(LIMIT + 1) in ids(result)) shouldBe false
    }

    @Test
    fun `without a title the name is 8 characters of the UUID, without startedAt the start is modifiedAt`() {
        val session = merge(past(5, modified = 90, started = null, title = null)).sessions.single()

        session.name shouldBe "00000000"
        session.startedAt shouldBe T0.plusSeconds(90)
    }

    @Test
    fun `a disk session is stopped, from disk, without summary or meta`() {
        val session = merge(past(3, cwd = softDir, modified = 60, started = 10)).sessions.single()

        session shouldBe
            pastSession(
                id = uuid(3),
                project = soft,
                cwd = softDir,
                name = "title 3",
                startedAt = T0.plusSeconds(10),
                lastActivityAt = T0.plusSeconds(60),
            )
    }

    @Test
    fun `without lastActivityAt the last activity is modifiedAt`() {
        val noActivity = past(3, modified = 60).copy(lastActivityAt = null)

        merge(noActivity).sessions.single().lastActivityAt shouldBe T0.plusSeconds(60)
    }

    @Test
    fun `a project without files has a key with zeros`() {
        merge().history shouldBe mapOf(root to ProjectHistory(0, 0), soft to ProjectHistory(0, 0))
    }

    @Test
    fun `the same conversation twice is listed once`() {
        merge(past(1), past(1)).sessions.size shouldBe 1
    }

    private companion object {
        const val LIMIT = 50
    }
}
