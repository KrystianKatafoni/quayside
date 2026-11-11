package dev.quayside.app

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SnapshotStatus
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * The sample data behind the preview of the Appearance dialog (ADR 0022, plan AM-803): every status once, a
 * fixed clock, no real paths of the user, nothing that writes.
 */
class PreviewSamplesTest {
    private val sessions = previewSessions()

    private fun snapshot() = sessions.monitor.snapshot.value

    @Test
    fun `sample sessions show every status`() {
        val states: Set<KClass<out SessionState>> = snapshot().sessions.map { it.state::class }.toSet()

        states shouldBe
            setOf(
                SessionState.Working::class,
                SessionState.AwaitingInput::class,
                SessionState.AwaitingPermission::class,
                SessionState.Done::class,
                SessionState.Failed::class,
                SessionState.Stopped::class,
            )
    }

    @Test
    fun `the snapshot is fresh`() {
        snapshot().status.shouldBeInstanceOf<SnapshotStatus.Fresh>()
    }

    @Test
    fun `sample sessions belong to the preview project and have summaries`() {
        snapshot().sessions.forEach {
            it.project shouldBe PREVIEW_PROJECT.id
            it.cwd.startsWith(PREVIEW_PROJECT.dir) shouldBe true
            // A summary makes the comfortable and the compact row look different in the preview
            (it.summary?.isNotBlank() == true) shouldBe true
        }
    }

    @Test
    fun `the preview project is no path of the user`() {
        val home = Path.of(System.getProperty("user.home"))

        PREVIEW_PROJECT.dir.startsWith(home) shouldBe false
        Files.exists(PREVIEW_PROJECT.dir) shouldBe false
    }

    @Test
    fun `the preview clock stands still and no activity is in its future`() {
        PREVIEW_CLOCK.now() shouldBe PREVIEW_CLOCK.now()
        snapshot().sessions.forEach { (it.lastActivityAt <= PREVIEW_CLOCK.now()) shouldBe true }
    }

    @Test
    fun `sessions commands are refused and change nothing`() =
        runTest {
            val before = snapshot()
            val id = before.sessions.first().id

            val results =
                listOf(
                    sessions.commands.start(PREVIEW_PROJECT.id, NewSessionSpec(prompt = "go")),
                    sessions.commands.stop(id),
                    sessions.commands.stopAllIn(PREVIEW_PROJECT.id),
                    sessions.commands.respawn(id),
                    sessions.commands.remove(id),
                    sessions.commands.assignPlace(id, PlaceAssignment.ProjectRoot),
                    sessions.commands.assignPlace(id, null),
                )

            results.forEach { it.shouldBeInstanceOf<Outcome.Err<*>>() }
            snapshot() shouldBe before
        }

    @Test
    fun `sample sessions have distinct ids`() {
        val ids: List<SessionId> = snapshot().sessions.map { it.id }

        ids.toSet().size shouldBe ids.size
    }

    @Test
    fun `the preview shows only the preview project and refuses changes`() =
        runTest {
            val projects = previewProjects()
            projects.projects.value.shouldContainExactly(PREVIEW_PROJECT)
            projects.storageIssue.value.shouldBeNull()

            listOf(
                projects.add(Path.of("/preview/other")),
                projects.rename(PREVIEW_PROJECT.id, "Renamed"),
                projects.remove(PREVIEW_PROJECT.id),
            ).forEach { it.shouldBeInstanceOf<Outcome.Err<*>>() }

            projects.projects.value.shouldContainExactly(PREVIEW_PROJECT)
        }
}
