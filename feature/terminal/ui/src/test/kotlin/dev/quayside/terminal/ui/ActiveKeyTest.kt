@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.terminal.ui

import dev.quayside.core.domain.ShellCommand
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** `activeKey`: which keyed tab the active pane shows, for the session bar of terminal mode (plan AM-809). */
class ActiveKeyTest {
    @TempDir
    lateinit var root: Path

    private val key = TabKey("attach:s1")

    @Test
    fun `active key follows the active pane`() =
        runTest {
            // Given a keyed tab in the only pane
            val world = world()
            val alpha = projectIn(root, "Alpha")
            act { world.controller.focusOrOpen(alpha, key, "attach · s1", ShellCommand(listOf("claude"), alpha.dir)) }
            world.controller.activeKey.value shouldBe key

            // When a split opens a shell in a new, active pane, then the active pane has no key
            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }
            world.controller.activeKey.value shouldBe null

            // When focus returns to the first pane, then the key is back
            act { world.controller.focusPrevious() }
            world.controller.activeKey.value shouldBe key
        }

    @Test
    fun `active key is null without tabs`() =
        runTest {
            val world = world()
            world.controller.activeKey.value shouldBe null

            // and again after the only tab is closed
            val alpha = projectIn(root, "Alpha")
            act { world.controller.focusOrOpen(alpha, key, "attach · s1", ShellCommand(listOf("claude"), alpha.dir)) }
            act { world.controller.closeActive() }

            world.state.tabs.size shouldBe 0
            world.controller.activeKey.value shouldBe null
        }

    @Test
    fun `active key is null for a tab opened without a key`() =
        runTest {
            val world = world()

            act { world.controller.openShell(projectIn(root, "Alpha")) }

            world.controller.activeKey.value shouldBe null
        }
}
