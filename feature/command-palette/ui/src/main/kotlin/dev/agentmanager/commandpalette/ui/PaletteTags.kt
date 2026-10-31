package dev.agentmanager.commandpalette.ui

import dev.agentmanager.commandpalette.api.CommandId

/** Semantics tags of the palette for UI tests. */
internal object PaletteTags {
    const val ROOT = "palette"
    const val FIELD = "palette.field"
    const val EMPTY = "palette.empty"

    /** The row of command [id]; selected = highlighted, clicking it runs the command. */
    fun row(id: CommandId): String = "palette.row.${id.value}"
}
