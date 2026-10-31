package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.PermissionMode
import dev.agentmanager.agentruntime.api.StartSpec
import dev.agentmanager.sessions.api.NewSessionSpec
import dev.agentmanager.sessions.api.SessionPermissionMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** `NewSessionSpec.toStartSpec` (AM-404 plan, rule 1): 1:1, the project dir becomes the cwd. */
class ToStartSpecTest {
    private val dir: Path = Path.of("/Users/test/projects/p1")

    @Test
    fun `start spec carries the project dir and every option`() {
        val spec =
            NewSessionSpec(
                prompt = "Fix the login bug",
                name = "fix-login",
                agent = "reviewer",
                model = "opus",
                permissionMode = SessionPermissionMode.ACCEPT_EDITS,
            )

        spec.toStartSpec(dir) shouldBe
            StartSpec(
                cwd = dir,
                prompt = "Fix the login bug",
                name = "fix-login",
                agent = "reviewer",
                model = "opus",
                permissionMode = PermissionMode.ACCEPT_EDITS,
            )
    }

    @Test
    fun `absent options stay absent`() {
        NewSessionSpec(prompt = "Fix the login bug").toStartSpec(dir) shouldBe
            StartSpec(cwd = dir, prompt = "Fix the login bug")
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("modes")
    fun `permission modes map one to one`(
        mode: SessionPermissionMode?,
        expected: PermissionMode?,
    ) {
        NewSessionSpec(prompt = "Fix the login bug", permissionMode = mode)
            .toStartSpec(dir)
            .permissionMode shouldBe expected
    }

    companion object {
        @JvmStatic
        fun modes(): List<Arguments> =
            listOf(
                arguments(SessionPermissionMode.DEFAULT, PermissionMode.DEFAULT),
                arguments(SessionPermissionMode.ACCEPT_EDITS, PermissionMode.ACCEPT_EDITS),
                arguments(SessionPermissionMode.PLAN, PermissionMode.PLAN),
                arguments(SessionPermissionMode.BYPASS_PERMISSIONS, PermissionMode.BYPASS_PERMISSIONS),
                arguments(null, null),
            )
    }
}
