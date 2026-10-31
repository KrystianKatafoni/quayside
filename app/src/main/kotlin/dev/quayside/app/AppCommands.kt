package dev.quayside.app

import dev.quayside.commandpalette.api.ChordKey
import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.commandpalette.api.KeyChord
import dev.quayside.commandpalette.api.KeyModifier
import dev.quayside.commandpalette.api.Shortcut
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.missioncontrol.ui.GlobalView
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsIntent
import dev.quayside.projects.ui.ProjectsMenuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Key bindings shared by the window menu and the palette rows (ADR 0011). */
internal object AppShortcuts {
    val ADD_PROJECT: Shortcut = Shortcut.of(chord('o', KeyModifier.META, KeyModifier.SHIFT))
    val REMOVE_PROJECT: Shortcut = Shortcut.of(KeyChord(ChordKey.Named.BACKSPACE, setOf(KeyModifier.META)))
    val PALETTE: Shortcut = Shortcut.of(chord('k', KeyModifier.META))

    val NEXT_SESSION: Shortcut = Shortcut.of(chord('j'))
    val PREVIOUS_SESSION: Shortcut = Shortcut.of(chord('k'))
    val STOP_SESSION: Shortcut = Shortcut.of(chord('x'))
    val PREVIEW_SESSION: Shortcut = Shortcut.of(KeyChord(ChordKey.Named.SPACE))
    val ATTACH_SESSION: Shortcut = Shortcut.of(KeyChord(ChordKey.Named.ENTER))
    val REPLY_SESSION: Shortcut = Shortcut.of(chord('r'))
    val SWITCH_PROJECT: Shortcut = Shortcut.of(chord('g'), chord('p'))

    // Terminal (AM-503). The menu and the palette rows use these; ⌘⌥←/→ have no chord key, so no palette label.
    val NEW_SHELL: Shortcut = Shortcut.of(chord('t', KeyModifier.META))
    val SPLIT_SIDE_BY_SIDE: Shortcut = Shortcut.of(chord('d', KeyModifier.META))
    val SPLIT_STACKED: Shortcut = Shortcut.of(chord('d', KeyModifier.META, KeyModifier.SHIFT))
    val MAXIMIZE_PANE: Shortcut =
        Shortcut.of(KeyChord(ChordKey.Named.ENTER, setOf(KeyModifier.META, KeyModifier.SHIFT)))

    private fun chord(
        char: Char,
        vararg modifiers: KeyModifier,
    ): KeyChord = KeyChord(ChordKey.Letter(char), modifiers.toSet())
}

/** Which window-menu items are enabled. */
internal data class AppMenuState(
    val add: Boolean,
    val remove: Boolean,
    val rename: Boolean,
    val palette: Boolean,
) {
    companion object {
        /**
         * An open palette or a dialog of Mission Control takes the keys (⌘⌫ reaches the palette field), so all items
         * are off; ⌘K also waits for project dialogs.
         */
        fun of(
            menu: ProjectsMenuState,
            paletteOpen: Boolean,
            sessionDialogOpen: Boolean = false,
        ): AppMenuState {
            val blocked = paletteOpen || sessionDialogOpen
            return AppMenuState(
                add = menu.canAdd && !blocked,
                remove = menu.canRemove && !blocked,
                rename = menu.canRename && !blocked,
                palette = !blocked && !menu.dialogOpen,
            )
        }
    }
}

/** One fixed project command: its text key, intent, optional shortcut and keywords, and when it is offered. */
private class Entry(
    val id: String,
    val titleKey: String,
    val intent: ProjectsIntent,
    val shortcut: Shortcut? = null,
    val keywords: List<String> = emptyList(),
    val offered: (ProjectsMenuState) -> Boolean,
)

private val ENTRIES =
    listOf(
        Entry("projects.add", "menu.addProject", ProjectsIntent.AddRequested, AppShortcuts.ADD_PROJECT) { it.canAdd },
        Entry(
            "projects.rename",
            "menu.renameProject",
            ProjectsIntent.RenameSelectedRequested,
            keywords = listOf("rename", "nazwa"),
        ) { it.canRename },
        Entry(
            "projects.remove",
            "menu.removeProject",
            ProjectsIntent.RemoveSelectedRequested,
            AppShortcuts.REMOVE_PROJECT,
            listOf("remove"),
        ) { it.canRemove },
    )

/** Project commands of the palette (AM-408, decision 1); none while a dialog is open. */
internal fun projectCommands(
    list: List<Project>,
    menu: ProjectsMenuState,
    onIntent: (ProjectsIntent) -> Unit,
): List<Command> {
    if (menu.dialogOpen) return emptyList()
    val fixed =
        ENTRIES.filter { it.offered(menu) }.map {
            Command(CommandId(it.id), Strings[it.titleKey], CommandGroup.PROJECT, it.keywords, it.shortcut) {
                onIntent(it.intent)
            }
        }
    return fixed + list.map { goTo(it, onIntent) }
}

private fun goTo(
    project: Project,
    onIntent: (ProjectsIntent) -> Unit,
): Command =
    Command(
        id = CommandId("projects.goto.${project.id.value}"),
        title = Strings.format("command.goToProject", project.displayName),
        group = CommandGroup.GO_TO_PROJECT,
        run = { onIntent(ProjectsIntent.Select(project.id)) },
    )

/** Offers [projectCommands] and [sessionCommands] for the live project list and menu state. */
internal class AppCommands(
    projects: ProjectsApi,
    controller: ProjectsController,
    missionControl: MissionControlController,
    palette: CommandPaletteController,
    scope: CoroutineScope,
) : CommandProvider {
    private val offered =
        { list: List<Project>, menu: ProjectsMenuState, actions: SessionActionsState, view: GlobalView? ->
            projectCommands(list, menu, controller::onIntent) +
                sessionCommands(list, menu, actions, view, missionControl::onIntent) { palette.open(it) }
        }

    override val commands: StateFlow<List<Command>> =
        combine(projects.projects, controller.menu, missionControl.actions, missionControl.view, offered)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                offered(
                    projects.projects.value,
                    controller.menu.value,
                    missionControl.actions.value,
                    missionControl.view.value,
                ),
            )
}
