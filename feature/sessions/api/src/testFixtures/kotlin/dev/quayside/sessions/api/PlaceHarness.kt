package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId

/**
 * A [ResumeHarness] that can also change the subfolders of a project (FR-P7), the way a place of a session
 * (FR-P9, AM-314) has to survive: a folder leaves the group and comes back. [settle] makes the system see the change.
 */
public interface PlaceHarness : ResumeHarness {
    /** The subfolders of [project] become [names], in the order given (as `Project.subfolders` holds them). */
    public suspend fun setSubfolders(
        project: ProjectId,
        names: List<String>,
    )
}
