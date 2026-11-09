@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.missioncontrol.ui.NewAgentWorld.ANNA
import dev.quayside.missioncontrol.ui.NewAgentWorld.BOB
import dev.quayside.missioncontrol.ui.NewAgentWorld.GROUP
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The consent step of a start in a subfolder of a group (AM-309 with AM-308): it asks about the directory `claude`
 * runs in, trusts exactly that one and starts again in the same subfolder. Logs never carry the subfolder name.
 */
internal class NewAgentTrustSubfolderTest : NewAgentControllerFixture() {
    private fun refusing() = RefusingCommands(fake, 1, trusting)

    /** The dialog on the group with [subfolder] chosen and a filled form; the first start is refused. */
    private fun TestScope.askedInGroup(
        commands: RefusingCommands,
        subfolder: SubfolderName?,
    ): NewAgentController {
        val controller = controller(commands, NewAgentWorld.projectsWithGroup())
        controller.open(ProjectFocus(GROUP.id, subfolder))
        controller.edit()
        submit(controller)
        return controller
    }

    @Test
    fun `a start in a subfolder asks to trust the directory of the subfolder`() =
        runTest {
            val controller = askedInGroup(refusing(), ANNA)

            controller.shown().trust shouldBe TrustStep(GROUP.dir.resolve("Anna"))
            controller.shown().form.subfolder shouldBe ANNA
            controller.shown().problem.shouldBeNull()
        }

    @Test
    fun `a start in a group without a subfolder asks about the project directory`() =
        runTest {
            val controller = askedInGroup(refusing(), null)

            controller.shown().trust shouldBe TrustStep(GROUP.dir)
        }

    @Test
    fun `accept in a subfolder trusts that directory and starts again in the same subfolder`() =
        runTest {
            val commands = refusing()
            val controller = askedInGroup(commands, BOB)

            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            trust.trusted.map(Path::toString) shouldBe listOf(GROUP.dir.resolve("bob").toString())
            commands.attempts.size shouldBe 2
            commands.attempts[1] shouldBe commands.attempts[0]
            commands.attempts[1].second.subfolder shouldBe "bob"
            focuses shouldBe listOf(ProjectFocus(GROUP.id, BOB))
            controller.state.value.shouldBeNull()
            log.dump() shouldNotContain "bob"
        }

    @Test
    fun `decline in a subfolder names the directory of the subfolder and keeps it in the form`() =
        runTest {
            val controller = askedInGroup(refusing(), ANNA)

            controller.onEvent(NewAgentEvent.TrustDecline)

            controller.shown().problem shouldBe NewAgentProblem.NotTrusted(GROUP.dir.resolve("Anna"))
            controller.shown().form.subfolder shouldBe ANNA
        }
}
