package dev.quayside.projects.api

import dev.quayside.core.domain.ProjectId
import java.nio.file.Path

// Test data builders for projects types: sensible defaults, override only what the test is about.

/** Directory of the default [aProject]; does not exist on disk. */
public val A_PROJECT_DIR: Path = Path.of("/Users/test/projects/agent-manager")

public fun aProject(
    id: String = "agent-manager",
    dir: Path = A_PROJECT_DIR,
    displayName: String = "agent-manager",
): Project = Project(ProjectId(id), dir, displayName)
