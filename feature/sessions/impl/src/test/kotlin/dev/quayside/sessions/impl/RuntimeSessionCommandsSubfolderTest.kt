@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.getOrNull
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SubfolderProblem
import dev.quayside.sessions.impl.GroupFixtures.GROUP
import dev.quayside.sessions.impl.GroupFixtures.GROUP_DIR
import dev.quayside.sessions.impl.GroupFixtures.MARKETING
import dev.quayside.sessions.impl.GroupFixtures.SECRET
import dev.quayside.sessions.impl.GroupFixtures.SOFT
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Starts in a subfolder of a group (AM-308 plan, rules 3 and 4; FR-S3, FR-P7). */
class RuntimeSessionCommandsSubfolderTest {
    private fun spec(subfolder: String?) =
        NewSessionSpec(prompt = "Fix the login bug", name = "fix-login", subfolder = subfolder)

    @Test
    fun `start in a subfolder passes its directory to the runtime`() =
        runTest {
            val world = commandsWorld()

            world.commands
                .start(GROUP, spec(SOFT))
                .getOrNull()
                .shouldNotBeNull()

            world.runtime.commandCalls shouldBe listOf("start")
            (world.fake.listSessions(null) as Outcome.Ok).value.map { it.cwd } shouldBe listOf(GROUP_DIR.resolve(SOFT))
        }

    @Test
    fun `start without a subfolder passes the project directory to the runtime`() =
        runTest {
            val world = commandsWorld()

            world.commands
                .start(GROUP, spec(null))
                .getOrNull()
                .shouldNotBeNull()

            (world.fake.listSessions(null) as Outcome.Ok).value.map { it.cwd } shouldBe listOf(GROUP_DIR)
            world.missingDirs.shouldBeEmpty()
        }

    @Test
    fun `start in a subfolder shows the session in that subfolder before the supervisor lists it`() =
        runTest {
            val world = commandsWorld()
            world.listOnce()
            world.runtime.lag = 2

            val id =
                world.commands
                    .start(GROUP, spec(SOFT))
                    .getOrNull()
                    .shouldNotBeNull()

            val session = world.session(id)
            listOf(session.project, session.cwd, session.state) shouldBe
                listOf(GROUP, GROUP_DIR.resolve(SOFT), SessionState.Working)
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(SubfolderProblem::class)
    fun `rejected subfolder does not call the runtime`(problem: SubfolderProblem) =
        runTest {
            val world = commandsWorld()
            val name =
                when (problem) {
                    SubfolderProblem.NOT_IN_PROJECT -> "docs"
                    SubfolderProblem.MISSING -> MARKETING.also { world.missingDirs.add(GROUP_DIR.resolve(it)) }
                }

            world.commands.start(GROUP, spec(name)) shouldBe
                Outcome.Err(SessionError.SubfolderUnavailable(GROUP, problem))

            world.runtime.commandCalls.shouldBeEmpty()
            world.listedIds().shouldBeEmpty()
        }

    @Test
    fun `start logs never carry the subfolder name`() =
        runTest {
            val world = commandsWorld()
            world.commands
                .start(GROUP, spec(SECRET))
                .getOrNull()
                .shouldNotBeNull()
            world.missingDirs.add(GROUP_DIR.resolve(SECRET))

            world.commands.start(GROUP, spec(SECRET)) shouldBe
                Outcome.Err(SessionError.SubfolderUnavailable(GROUP, SubfolderProblem.MISSING))
            world.commands.start(GROUP, spec("secret-other")) shouldBe
                Outcome.Err(SessionError.SubfolderUnavailable(GROUP, SubfolderProblem.NOT_IN_PROJECT))

            world.logger.entries.shouldNotBeEmpty()
            world.logger.entries.forEach { it.toString() shouldNotContain "secret" }
        }
}
