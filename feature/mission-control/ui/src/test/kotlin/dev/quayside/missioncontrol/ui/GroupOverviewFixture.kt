package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import java.nio.file.Path

/**
 * The group of the AM-307 tests: `/p/plant-puls` with subfolders given in an order that is neither ASCII nor
 * [dev.quayside.projects.api.ProjectGroups.order] (`bob, Carl, Anna`, so that "no sorting" and "ASCII sorting"
 * both fail). No name equals a fixed UI text ("Pozostałe", "Podfoldery", "brak agentów").
 */
internal val OVERVIEW_DIR: Path = Path.of("/p/plant-puls")
internal val OVERVIEW_GROUP =
    aProject(
        id = "plant",
        dir = OVERVIEW_DIR,
        displayName = "plant-puls",
        subfolders = subfolderNames("bob", "Carl", "Anna"),
    )

/** A nested project inside the subfolder `bob`: its sessions are not those of the group. */
internal val OVERVIEW_NESTED = aProject(id = "nested", dir = OVERVIEW_DIR.resolve("bob"), displayName = "nested")

/** The three counters of a row, in the order of the tiers; `done` is not shown in the overview and is left out. */
internal data class Counts(
    val waiting: Int = 0,
    val failed: Int = 0,
    val working: Int = 0,
)

internal fun SessionStats.counts(): Counts = Counts(waiting, failed, working)

/** A session of the group in [cwd] (relative to the group directory unless absolute), started at T0 + [index]. */
internal fun groupSession(
    id: String,
    cwd: String,
    state: SessionState,
    index: Long = 0,
    project: ProjectId? = OVERVIEW_GROUP.id,
): Session =
    aSession(
        id = id,
        project = project,
        cwd = OVERVIEW_DIR.resolve(cwd),
        state = state,
        startedAt = T0.plusSeconds(index),
    )

internal fun sub(name: String): SubfolderName = SubfolderName(name)
