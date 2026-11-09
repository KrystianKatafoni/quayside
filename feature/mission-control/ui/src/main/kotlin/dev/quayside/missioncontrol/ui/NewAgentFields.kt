package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmDropdownItems
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.SessionPermissionMode

/**
 * The fields of the dialog in the order of Tab: project, subfolder (a group only), prompt, name, subagent, model,
 * permission mode.
 */
@Composable
internal fun NewAgentFields(
    state: NewAgentState,
    prompt: FocusRequester,
    onEvent: (NewAgentEvent) -> Unit,
) {
    val form = state.form
    val on = !state.busy
    val edit = { changed: NewAgentForm -> onEvent(NewAgentEvent.Edit(changed)) }
    Field("newAgent.project") { ProjectChoice(state, edit) }
    state.projects.firstOrNull { it.id == form.project }?.takeIf { it.subfolders.isNotEmpty() }?.let { group ->
        Field("newAgent.subfolder") { SubfolderChoice(group.subfolders, form, on, edit) }
    }
    PromptField(form, on, prompt, edit)
    TextFields(form, on, edit)
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        Box(Modifier.weight(weight = 1f)) { Field("newAgent.model") { ModelChoiceField(form, on, edit) } }
        Box(Modifier.weight(weight = 1f)) { Field("newAgent.permission") { PermissionField(form, on, edit) } }
    }
}

@Composable
private fun PromptField(
    form: NewAgentForm,
    on: Boolean,
    prompt: FocusRequester,
    edit: (NewAgentForm) -> Unit,
) {
    Field("newAgent.prompt") {
        TextBox(
            form.prompt,
            on,
            minLines = 4,
            Modifier
                .testTag(MissionControlTags.NEW_AGENT_PROMPT)
                .described(MissionControlStrings["newAgent.prompt"])
                .focusRequester(prompt),
        ) {
            edit(form.copy(prompt = it))
        }
    }
}

@Composable
private fun TextFields(
    form: NewAgentForm,
    on: Boolean,
    edit: (NewAgentForm) -> Unit,
) {
    Field("newAgent.name") {
        TextBox(
            form.name,
            on,
            minLines = 1,
            Modifier.testTag(MissionControlTags.NEW_AGENT_NAME).described(MissionControlStrings["newAgent.name"]),
        ) {
            edit(form.copy(name = it))
        }
    }
    Field("newAgent.agent", hint = "newAgent.agent.hint") {
        TextBox(
            form.agent,
            on,
            minLines = 1,
            Modifier.testTag(MissionControlTags.NEW_AGENT_AGENT).described(MissionControlStrings["newAgent.agent"]),
        ) {
            edit(form.copy(agent = it))
        }
    }
}

@Composable
private fun Field(
    labelKey: String,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        AmText(MissionControlStrings[labelKey], role = TextRole.CAPTION, color = mutedColor())
        hint?.let { AmText(MissionControlStrings[it], role = TextRole.CAPTION, color = mutedColor()) }
        content()
    }
}

@Composable
private fun ProjectChoice(
    state: NewAgentState,
    edit: (NewAgentForm) -> Unit,
) {
    val form = state.form
    val ids = state.projects.map { it.id }
    Dropdown(
        MissionControlTags.NEW_AGENT_PROJECT,
        "newAgent.project",
        AmDropdownItems(
            state.projects.map { it.displayName },
            ids.indexOf(form.project),
            MissionControlStrings["newAgent.project.none"],
        ),
        !state.busy,
    ) { index -> edit(NewAgentRules.withProject(form, ids[index])) }
}

/**
 * The project directory first (index 0, no subfolder), then the subfolders of the group in their order.
 * A form subfolder outside the list would make `indexOf + 1` show the project directory; that is unreachable
 * today (`defaultFocus`, `withProject` and `after` keep the subfolder on the list).
 */
@Composable
private fun SubfolderChoice(
    subfolders: List<SubfolderName>,
    form: NewAgentForm,
    enabled: Boolean,
    edit: (NewAgentForm) -> Unit,
) {
    val labels = listOf(MissionControlStrings["newAgent.subfolder.root"]) + subfolders.map { it.value }
    Dropdown(
        MissionControlTags.NEW_AGENT_SUBFOLDER,
        "newAgent.subfolder",
        AmDropdownItems(labels, subfolders.indexOf(form.subfolder) + 1),
        enabled,
    ) { index -> edit(form.copy(subfolder = subfolders.getOrNull(index - 1))) }
}

@Composable
private fun ModelChoiceField(
    form: NewAgentForm,
    enabled: Boolean,
    edit: (NewAgentForm) -> Unit,
) {
    val models = ModelChoice.entries
    val labels = models.map { it.alias ?: MissionControlStrings["newAgent.model.default"] }
    Dropdown(
        MissionControlTags.NEW_AGENT_MODEL,
        "newAgent.model",
        AmDropdownItems(labels, models.indexOf(form.model)),
        enabled,
    ) { index -> edit(form.copy(model = models[index])) }
}

/**
 * `null` is Claude Code's default: no `--permission-mode` flag. The list opens on the current mode and its arrows
 * stop at the ends, so from the default "Pomiń zgody" takes the list, three steps down and Enter.
 */
private val permissions: List<SessionPermissionMode?> =
    listOf(
        null,
        SessionPermissionMode.ACCEPT_EDITS,
        SessionPermissionMode.PLAN,
        SessionPermissionMode.BYPASS_PERMISSIONS,
    )

/** `DEFAULT` from outside (not set by this UI, whose list has `null`) is shown as the default mode. */
@Composable
private fun PermissionField(
    form: NewAgentForm,
    enabled: Boolean,
    edit: (NewAgentForm) -> Unit,
) {
    val current = form.permission.takeUnless { it == SessionPermissionMode.DEFAULT }
    Dropdown(
        MissionControlTags.NEW_AGENT_PERMISSION,
        "newAgent.permission",
        AmDropdownItems(permissions.map { MissionControlStrings[permissionKey(it)] }, permissions.indexOf(current)),
        enabled,
    ) { index -> edit(form.copy(permission = permissions[index])) }
}

private fun permissionKey(mode: SessionPermissionMode?): String =
    when (mode) {
        null, SessionPermissionMode.DEFAULT -> "newAgent.permission.default"
        SessionPermissionMode.ACCEPT_EDITS -> "newAgent.permission.acceptEdits"
        SessionPermissionMode.PLAN -> "newAgent.permission.plan"
        SessionPermissionMode.BYPASS_PERMISSIONS -> "newAgent.permission.bypass"
    }
