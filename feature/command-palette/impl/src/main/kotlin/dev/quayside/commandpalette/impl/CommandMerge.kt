package dev.quayside.commandpalette.impl

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.Shortcut

/** A conflict found while merging providers. [reason] is a fixed category, [commandId] the later command. */
internal data class MergeIssue(
    val reason: String,
    val commandId: CommandId,
)

internal data class Merged(
    val commands: List<Command>,
    val issues: List<MergeIssue>,
)

/** Pure merge: the first [CommandId] wins; the first holder of a shortcut keeps it, later ones lose it. */
internal fun mergeCommands(all: List<Command>): Merged {
    val ids = mutableSetOf<CommandId>()
    val shortcuts = mutableSetOf<Shortcut>()
    val issues = mutableListOf<MergeIssue>()
    val commands =
        all.mapNotNull { command ->
            if (!ids.add(command.id)) {
                issues += MergeIssue("duplicateId", command.id)
                return@mapNotNull null
            }
            val shortcut = command.shortcut
            if (shortcut == null || shortcuts.add(shortcut)) {
                command
            } else {
                issues += MergeIssue("duplicateShortcut", command.id)
                command.copy(shortcut = null)
            }
        }
    return Merged(commands, issues)
}
