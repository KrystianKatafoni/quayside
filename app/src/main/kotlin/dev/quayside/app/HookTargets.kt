package dev.quayside.app

import dev.quayside.agentruntime.api.HookScope
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** A settings file the hooks dialog can change. Names and paths are user data: log only [kind] (ENG-C8). */
internal sealed interface HookTarget {
    val scope: HookScope
    val kind: String

    data object Global : HookTarget {
        override val scope: HookScope get() = HookScope.Global
        override val kind: String get() = "global"
    }

    /** [present] is false when [dir] is no longer a directory: no status read, no install (it would recreate it). */
    data class Project(
        val id: ProjectId,
        val name: String,
        val dir: Path,
        val present: Boolean,
    ) : HookTarget {
        override val scope: HookScope get() = HookScope.Project(dir)
        override val kind: String get() = "project"
    }
}

/** The targets of the dialog: [HookTarget.Global] first, then the projects in the sidebar order. */
internal fun interface HookTargetSource {
    suspend fun targets(): List<HookTarget>
}

/** [HookTargetSource] over the live project list; checks the folders on [io]. */
internal class ProjectHookTargets(
    private val projects: StateFlow<List<Project>>,
    private val io: CoroutineDispatcher,
    private val isDirectory: (Path) -> Boolean = Files::isDirectory,
) : HookTargetSource {
    override suspend fun targets(): List<HookTarget> {
        val list = projects.value
        return withContext(io) {
            listOf(HookTarget.Global) +
                list.map { HookTarget.Project(it.id, it.displayName, it.dir, isDirectory(it.dir)) }
        }
    }
}
