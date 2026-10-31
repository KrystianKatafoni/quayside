package dev.quayside.projects.impl

import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.HooksPresence
import dev.quayside.projects.api.InspectionWorld
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Turns an [InspectionWorld] into a real directory: `git init` (and a detached HEAD), `CLAUDE.md` and
 * `.claude/settings*.json` with our hooks, plus the [RuntimeWorld] of the fake runtime.
 */
internal object DiskWorld {
    /** Builds [world] in the existing empty [projectDir]; returns what the fake runtime should report. */
    fun build(
        projectDir: Path,
        world: InspectionWorld,
        git: TestGit,
    ): RuntimeWorld {
        world.git?.let { info ->
            git.init(projectDir, info.branch ?: "main")
            if (info.branch == null) {
                git.commitAll(projectDir)
                git.run(projectDir, "checkout", "--quiet", "--detach")
            }
        }
        if (world.claudeMd) Files.writeString(projectDir.resolve("CLAUDE.md"), "# Project\n")
        writeHooks(projectDir, world.hooks)
        val sessions =
            (1..world.sessions).map {
                aRuntimeSession(
                    id = aSessionId("s%07d".format(Locale.ROOT, it)),
                    cwd = projectDir,
                )
            }
        return RuntimeWorld(sessions = sessions, installed = world.runtimeAvailable)
    }

    private fun writeHooks(
        projectDir: Path,
        hooks: HooksPresence,
    ) {
        val (shared, local) =
            when (hooks) {
                HooksPresence.NONE -> {
                    return
                }

                // Two kinds in the shared file and one in the local file: not all five.
                HooksPresence.PARTIAL -> {
                    HookSettings.settings(listOf(HookKind.STOP, HookKind.NOTIFICATION)) to
                        HookSettings.settings(listOf(HookKind.SESSION_START))
                }

                // Three plus two: only the union of both files is complete.
                HooksPresence.INSTALLED -> {
                    HookSettings.settings(listOf(HookKind.NOTIFICATION, HookKind.STOP, HookKind.STOP_FAILURE)) to
                        HookSettings.settings(listOf(HookKind.SESSION_START, HookKind.SESSION_END))
                }

                // A broken local file next to a complete shared file.
                HooksPresence.UNREADABLE -> {
                    HookSettings.settings(HookKind.entries) to "{oops"
                }
            }
        val claude = projectDir.resolve(".claude").createDirectories()
        claude.resolve(SHARED).writeText(shared)
        claude.resolve(LOCAL).writeText(local)
    }

    const val SHARED: String = "settings.json"
    const val LOCAL: String = "settings.local.json"
}
