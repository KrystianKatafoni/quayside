package dev.agentmanager.projects.ui

import dev.agentmanager.projects.api.NameProblem
import dev.agentmanager.projects.api.ProjectError
import dev.agentmanager.projects.api.ProjectInputRules
import dev.agentmanager.projects.api.StorageIssue
import dev.agentmanager.projects.api.StorageProblem

/** Fixed texts for errors and storage issues; never contain a path or a name (ENG-C8). */
internal object ProjectMessages {
    private val maxNameLength = ProjectInputRules.MAX_DISPLAY_NAME_LENGTH.toString()

    fun of(error: ProjectError): UiText =
        when (error) {
            is ProjectError.NotAbsolute -> UiText("error.notAbsolute")
            is ProjectError.NotFound -> UiText("error.notFound")
            is ProjectError.NotADirectory -> UiText("error.notADirectory")
            is ProjectError.AlreadyAdded -> UiText("error.alreadyAdded")
            is ProjectError.UnknownProject -> UiText("error.unknownProject")
            is ProjectError.InvalidDisplayName -> of(error.reason)
            is ProjectError.StorageFailed -> of(error.problem)
        }

    fun of(issue: StorageIssue): UiText =
        when (issue) {
            StorageIssue.RECOVERED_FROM_BROKEN_FILE -> UiText("storageIssue.recovered")
            StorageIssue.READ_FAILED -> UiText("storageIssue.readFailed")
            StorageIssue.NEWER_SCHEMA -> UiText("storageIssue.newerSchema")
        }

    private fun of(problem: NameProblem): UiText =
        when (problem) {
            NameProblem.BLANK -> UiText("error.name.blank")
            NameProblem.TOO_LONG -> UiText("error.name.tooLong", listOf(maxNameLength))
            NameProblem.CONTROL_CHARACTERS -> UiText("error.name.controlCharacters")
        }

    private fun of(problem: StorageProblem): UiText =
        when (problem) {
            StorageProblem.READ_FAILED -> UiText("error.storage.readFailed")
            StorageProblem.WRITE_FAILED -> UiText("error.storage.writeFailed")
            StorageProblem.NEWER_SCHEMA -> UiText("error.storage.newerSchema")
        }
}

/** A fixed log category per variant, never the path or name it may hold (ENG-C8). */
internal fun ProjectError.category(): String =
    when (this) {
        is ProjectError.NotAbsolute -> "notAbsolute"
        is ProjectError.NotFound -> "notFound"
        is ProjectError.NotADirectory -> "notADirectory"
        is ProjectError.AlreadyAdded -> "alreadyAdded"
        is ProjectError.UnknownProject -> "unknownProject"
        is ProjectError.InvalidDisplayName -> "invalidDisplayName"
        is ProjectError.StorageFailed -> "storageFailed"
    }
