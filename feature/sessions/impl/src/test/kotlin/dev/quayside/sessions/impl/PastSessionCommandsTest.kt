@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.storage.JsonStorage
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionOrigin
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

/**
 * Commands and reads on disk sessions of `sessionsModule` (FR-P9, ADR 0032): the supervisor is never called, the
 * session files are never touched, "Usuń" hides for good. Fakes only, except the settings file in a temp directory.
 */
class PastSessionCommandsTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val id = SessionId(uuidOf(1))

    private fun FakeAgentRuntime.commandCalls() = calls.filter { it != "listSessions" }

    @Test
    fun `respawn of a disk session never calls the runtime`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))

            world.api.commands.respawn(id) shouldBe Outcome.Err(SessionError.UnknownSession(id))

            world.runtime.commandCalls() shouldBe emptyList()
        }

    @Test
    fun `remove of a disk session hides it and never calls the runtime`() =
        runTest {
            val world = pastWorld(listOf(conversation(1), conversation(2)))

            world.api.commands.remove(id) shouldBe Outcome.Ok(Unit)

            world.api.monitor.snapshot.value.sessions
                .map { it.id.value } shouldBe listOf(uuidOf(2))
            world.settings.settings.value.hidden shouldBe setOf(id)
            world.runtime.commandCalls() shouldBe emptyList()
        }

    @Test
    fun `remove of a disk session is visible in the snapshot before it returns`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))

            world.api.commands.remove(id)

            // no runCurrent, no waiting for the next scan: commands publish before they return
            world.api.monitor.snapshot.value.sessions
                .count { it.origin == SessionOrigin.DISK } shouldBe 0
        }

    @Test
    fun `remove of a disk session survives a restart`() =
        runTest {
            // Given the real settings file in a temp directory
            val first = sessionHistorySettingsModule(JsonStorage(dir, io), NoOpLogger)
            pastWorld(listOf(conversation(1), conversation(2)), settings = first).api.commands.remove(id)

            // When the app starts again over the same file
            val second = sessionHistorySettingsModule(JsonStorage(dir, io), NoOpLogger)
            val world = pastWorld(listOf(conversation(1), conversation(2)), settings = second)

            // Then the removed session is still hidden
            world.api.monitor.snapshot.value.sessions
                .map { it.id.value } shouldBe listOf(uuidOf(2))
        }

    @Test
    fun `stop of a disk session is ok and the runtime is not called`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))

            world.api.commands.stop(id) shouldBe Outcome.Ok(Unit)

            world.runtime.commandCalls() shouldBe emptyList()
        }

    @Test
    fun `recentOutput of a disk session is UnknownSession without calling the runtime`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))

            world.api.monitor.recentOutput(id) shouldBe Outcome.Err(SessionError.UnknownSession(id))

            world.runtime.calls.none { it.startsWith("recentOutput") } shouldBe true
        }

    @Test
    fun `disk sessions emit no events`() =
        runTest {
            val world = pastWorld()
            val events = record(world.api.monitor.events)

            world.history.put(conversation(1))
            advance(60.seconds)
            world.api.commands.remove(id)
            runCurrent()

            events shouldBe emptyList()
        }

    @Test
    fun `a settings file that cannot be written gives an error from remove`() =
        runTest {
            // Given a settings directory that is a file
            val blocked = dir.resolve("blocked").also { it.toFile().writeText("x") }
            val repo = sessionHistorySettingsModule(JsonStorage(blocked, io), NoOpLogger)
            val world = pastWorld(listOf(conversation(1)), settings = repo)

            val result = world.api.commands.remove(id)

            // a command that returns Ok is visible in the snapshot, so a hide that was not saved must be an error
            (result is Outcome.Err) shouldBe true
            world.api.monitor.snapshot.value.sessions
                .count { it.origin == SessionOrigin.DISK } shouldBe 1
        }
}
