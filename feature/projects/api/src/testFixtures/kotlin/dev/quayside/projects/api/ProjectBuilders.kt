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
    subfolders: List<SubfolderName> = emptyList(),
): Project = Project(ProjectId(id), dir, displayName, subfolders)

/** Subfolder names for tests, in the order given (the caller keeps them sorted when the test needs it). */
public fun subfolderNames(vararg names: String): List<SubfolderName> = names.map(::SubfolderName)
