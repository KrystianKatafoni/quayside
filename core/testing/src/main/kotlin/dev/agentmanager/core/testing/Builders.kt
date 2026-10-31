package dev.agentmanager.core.testing

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import java.nio.file.Path

// Test data builders: sensible defaults, override only what the test is about.

public fun aProjectId(value: String = "agent-manager"): ProjectId = ProjectId(value)

public fun aSessionId(value: String = "7c5dcf5d"): SessionId = SessionId(value)

public fun aShellCommand(
    vararg argv: String = arrayOf("claude", "agents", "--json"),
    workDir: Path = Path.of("/Users/test/projects/agent-manager"),
    env: Map<String, String> = emptyMap(),
): ShellCommand = ShellCommand(argv.toList(), workDir, env)
