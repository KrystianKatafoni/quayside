package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.commandpalette.api.Shortcut
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.info
import dev.quayside.missioncontrol.ui.AttachTarget
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.SessionActionsState
import dev.quayside.projects.api.Project
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsMenuState
import dev.quayside.sessions.api.Session
import dev.quayside.terminal.ui.TerminalController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Attach (`⏎`) and reply (`r`) commands of the palette; each by its flag, none while a dialog is open. */
internal fun attachCommands(
    dialogOpen: Boolean,
    actions: SessionActionsState,
    attach: () -> Unit,
    reply: () -> Unit,
): List<Command> {
    if (dialogOpen || actions.dialogOpen) return emptyList()
    val attachCommand =
        sessionCommand(
            "sessions.attach",
            "command.attachSession",
            listOf("attach", "terminal", "podłącz"),
            AppShortcuts.ATTACH_SESSION,
            attach,
        )
    val replyCommand =
        sessionCommand(
            "sessions.reply",
            "command.replySession",
            listOf("reply", "odpowiedz", "answer", "zgoda"),
            AppShortcuts.REPLY_SESSION,
            reply,
        )
    return listOfNotNull(
        attachCommand.takeIf { actions.canAttach },
        replyCommand.takeIf { actions.canReply },
    )
}

private fun sessionCommand(
    id: String,
    title: String,
    keywords: List<String>,
    shortcut: Shortcut,
    run: () -> Unit,
): Command = Command(CommandId(id), Strings[title], CommandGroup.SESSION, keywords, shortcut) { run() }

/** Opens `claude attach` of the selected session in its project's terminal tab, or shows the tab it already has
 * (`⏎` AM-505, `r` AM-506, one tab per session AM-510). */
internal class SessionAttacher(
    private val missionControl: MissionControlController,
    private val terminal: TerminalController,
    private val attachCommand: (SessionId) -> ShellCommand,
    private val logger: Logger,
) {
    /** Shows the attach tab of [MissionControlController.attachTarget], opening it when missing; no-op without. */
    fun attachSelected() {
        val target = missionControl.attachTarget() ?: return
        open(target, "session attach")
    }

    /** Like [attachSelected], but only for a target that is [AttachTarget.waiting] (the same tab); no-op otherwise. */
    fun replySelected() {
        val target = missionControl.attachTarget()?.takeIf { it.waiting } ?: return
        open(target, "session reply")
    }

    /**
     * Shows the attach tab of [id] chosen on a popup (AM-702): looks the session up in [sessions] and its project in
     * [projects]; no-op for a session that is gone or outside the registered projects.
     */
    fun attachSession(
        id: SessionId,
        sessions: List<Session>,
        projects: List<Project>,
    ) {
        val session = sessions.firstOrNull { it.id == id } ?: return
        val project = projects.firstOrNull { it.id == session.project } ?: return
        open(AttachTarget(id, session.name, project, session.state.isWaiting), "popup attach")
    }

    private fun open(
        target: AttachTarget,
        event: String,
    ) {
        logger.info(event, "sessionId" to target.session.value)
        terminal.focusOrOpen(
            terminalProjectOf(target.project),
            attachTabKey(target.session),
            Strings.format("terminal.attachTab", target.name),
            attachCommand(target.session),
        )
    }
}

/**
 * Offers [attachCommands] (`⏎` and `r`) for the project and Mission Control dialogs and the selected session.
 * Availability follows `actions.canAttach` and `canReply`, which come after `stateIn` and may lag one frame behind on
 * the first selection. The attach itself reads its target afresh through `attachTarget()`, so the opposite case
 * (a dialog and `⏎` or `r` in the same frame) is a safe no-op.
 */
internal class AttachCommands(
    controller: ProjectsController,
    missionControl: MissionControlController,
    attacher: SessionAttacher,
    scope: CoroutineScope,
) : CommandProvider {
    private val offered =
        { menu: ProjectsMenuState, actions: SessionActionsState ->
            attachCommands(menu.dialogOpen, actions, attacher::attachSelected, attacher::replySelected)
        }

    override val commands: StateFlow<List<Command>> =
        combine(controller.menu, missionControl.actions, offered)
            .stateIn(scope, SharingStarted.Eagerly, offered(controller.menu.value, missionControl.actions.value))
}
