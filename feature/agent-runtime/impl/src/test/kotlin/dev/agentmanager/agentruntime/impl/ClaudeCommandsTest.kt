package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.PermissionMode
import dev.agentmanager.agentruntime.api.StartSpec
import dev.agentmanager.core.domain.SessionId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class ClaudeCommandsTest {
    private val exe = "/opt/test/bin/claude"
    private val cwd = Path.of("/Users/test/projects/agent-manager")

    @Test
    fun `list is agents json all without cwd`() {
        ClaudeCommands.list(exe) shouldBe listOf(exe, "agents", "--json", "--all")
    }

    @Test
    fun `start with every field passes each flag before the prompt`() {
        val spec =
            StartSpec(
                cwd = cwd,
                prompt = "Fix the login bug",
                name = "fix-login",
                agent = "reviewer",
                model = "opus",
                permissionMode = PermissionMode.ACCEPT_EDITS,
            )

        ClaudeCommands.start(exe, spec) shouldBe
            listOf(
                exe,
                "--bg",
                "--name",
                "fix-login",
                "--agent",
                "reviewer",
                "--model",
                "opus",
                "--permission-mode",
                "acceptEdits",
                "--",
                "Fix the login bug",
            )
    }

    @Test
    fun `start with only the prompt passes bg, double dash and the prompt`() {
        ClaudeCommands.start(exe, StartSpec(cwd = cwd, prompt = "Fix it")) shouldBe listOf(exe, "--bg", "--", "Fix it")
    }

    @Test
    fun `start keeps the prompt as one argument after the double dash`() {
        val prompt = "-p --help \"quoted\" 'single' \$HOME `id`\nsecond line"

        ClaudeCommands.start(exe, StartSpec(cwd = cwd, prompt = prompt)).takeLast(2) shouldBe listOf("--", prompt)
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "ACCEPT_EDITS, acceptEdits",
        "PLAN, plan",
        "BYPASS_PERMISSIONS, bypassPermissions",
    )
    fun `permission mode maps to the CLI value`(
        mode: PermissionMode,
        cliValue: String,
    ) {
        val spec = StartSpec(cwd = cwd, prompt = "Fix it", permissionMode = mode)

        ClaudeCommands.start(exe, spec) shouldBe listOf(exe, "--bg", "--permission-mode", cliValue, "--", "Fix it")
    }

    @Test
    fun `default permission mode passes no flag`() {
        // claude 2.1.302 has no `default` value (acceptEdits, auto, bypassPermissions, manual, dontAsk, plan).
        val spec = StartSpec(cwd = cwd, prompt = "Fix it", permissionMode = PermissionMode.DEFAULT)

        ClaudeCommands.start(exe, spec) shouldBe listOf(exe, "--bg", "--", "Fix it")
    }

    @Test
    fun `version is claude double dash version`() {
        ClaudeCommands.version(exe) shouldBe listOf(exe, "--version")
    }

    @Test
    fun `daemonStatus is daemon status without json`() {
        ClaudeCommands.daemonStatus(exe) shouldBe listOf(exe, "daemon", "status")
    }

    @Test
    fun `agentViewProbe is agents json without all`() {
        ClaudeCommands.agentViewProbe(exe) shouldBe listOf(exe, "agents", "--json")
    }

    @ParameterizedTest
    @ValueSource(strings = ["attach", "logs", "stop", "respawn", "rm"])
    fun `byId puts the verb and the id after the executable`(verb: String) {
        ClaudeCommands.byId(exe, verb, SessionId("7c5dcf5d")) shouldBe listOf(exe, verb, "7c5dcf5d")
    }
}
