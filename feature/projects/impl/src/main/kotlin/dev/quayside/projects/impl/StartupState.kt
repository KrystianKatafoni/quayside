package dev.quayside.projects.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.Loaded
import dev.quayside.core.storage.StorageError
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.StorageIssue
import dev.quayside.projects.api.StorageProblem

/**
 * What the store starts with: the list, the issue shown to the UI and, when commands must be refused,
 * the problem they report.
 */
internal data class StartupState(
    val projects: List<Project>,
    val issue: StorageIssue?,
    val blockedBy: StorageProblem?,
) {
    internal companion object {
        /**
         * Pure mapping of the single `load` at start-up (AM-302 plan, decision 6). Every `IoFailed` is
         * [StorageProblem.READ_FAILED]: the store does not tell a failed read from a failed move aside.
         */
        fun of(loaded: Outcome<Loaded<List<Project>>, StorageError>): StartupState =
            when (loaded) {
                is Outcome.Ok -> {
                    val issue = if (loaded.value.recovered == null) null else StorageIssue.RECOVERED_FROM_BROKEN_FILE
                    StartupState(loaded.value.value, issue, null)
                }

                is Outcome.Err -> {
                    when (loaded.error) {
                        is StorageError.NewerSchema -> blocked(StorageIssue.NEWER_SCHEMA, StorageProblem.NEWER_SCHEMA)
                        is StorageError.IoFailed -> blocked(StorageIssue.READ_FAILED, StorageProblem.READ_FAILED)
                    }
                }
            }

        private fun blocked(
            issue: StorageIssue,
            problem: StorageProblem,
        ): StartupState = StartupState(emptyList(), issue, problem)
    }
}

/**
 * A failed `save` is always [StorageProblem.WRITE_FAILED], whatever the store reported: the file was
 * loadable at start-up, so the save is what failed. The error's path and reason are dropped (ENG-C8).
 */
internal fun StorageError.toWriteProblem(): StorageProblem = StorageProblem.WRITE_FAILED
