package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import java.nio.file.Path

/** Pure rules shared by every sessions implementation and its fakes (ENG-C9). No IO. */
public object SessionRules {
    /**
     * The project whose directory in [projects] is the deepest one that equals or contains [cwd],
     * compared by path components (`/p/ab` is not inside `/p/a`); null when none contains it.
     *
     * [cwd] is normalized purely, without IO (`/p/a/../b` is `/p/b`); a relative [cwd] belongs to
     * no project. The directories in [projects] must be absolute and canonical, like `Project.dir`.
     */
    public fun projectFor(
        cwd: Path,
        projects: Map<ProjectId, Path>,
    ): ProjectId? {
        if (!cwd.isAbsolute) return null
        val normalized = cwd.normalize()
        return projects.entries
            .filter { (_, dir) -> normalized.startsWith(dir) }
            .maxByOrNull { (_, dir) -> dir.nameCount }
            ?.key
    }
}
