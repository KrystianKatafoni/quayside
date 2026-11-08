package dev.quayside.projects.api

import dev.quayside.core.domain.ProjectId
import java.nio.file.Path

/**
 * Name of one direct subdirectory of a project, e.g. `soft`; valid only from [ProjectInputRules.subfolderName]
 * or [ProjectInspector.subfolders]. `toString` hides the value (ENG-C8).
 */
@JvmInline
public value class SubfolderName(
    public val value: String,
) {
    override fun toString(): String = "SubfolderName(***)"
}

/** What the sidebar has selected: [project], and [subfolder] when a subfolder row is selected (FR-P7). */
public data class ProjectFocus(
    val project: ProjectId,
    val subfolder: SubfolderName? = null,
)

/** Pure rules of groups, no IO (ENG-C9). */
public object ProjectGroups {
    /** Order of [Project.subfolders]: ignoring case, then by code point. One place for the store, the UI and fakes. */
    public val order: Comparator<SubfolderName> =
        compareBy<SubfolderName, String>(String.CASE_INSENSITIVE_ORDER) { it.value }.thenBy { it.value }

    /** `project.dir.resolve(name.value)`. */
    public fun dirOf(
        project: Project,
        name: SubfolderName,
    ): Path = project.dir.resolve(name.value)

    /**
     * The subfolder of [project] that equals or contains [cwd] by path components (`soft` does not contain
     * `software`); null for the project directory itself, outside every subfolder, outside the project or a
     * relative [cwd]. [cwd] is normalized purely, like `SessionRules.projectFor`.
     */
    public fun subfolderOf(
        project: Project,
        cwd: Path,
    ): SubfolderName? {
        if (!cwd.isAbsolute) return null
        val normalized = cwd.normalize()
        return project.subfolders.firstOrNull { normalized.startsWith(dirOf(project, it).normalize()) }
    }
}
