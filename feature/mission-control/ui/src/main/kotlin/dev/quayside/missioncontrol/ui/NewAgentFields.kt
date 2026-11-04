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
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole
import dev.quayside.sessions.api.SessionPermissionMode

/** The fields of the dialog in the order of Tab: project, prompt, name, subagent, model, permission mode. */
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
    val shown =
        state.projects.firstOrNull { it.id == form.project }?.displayName
            ?: MissionControlStrings["newAgent.project.none"]
    ChoiceField(
        shown,
        !state.busy,
        Modifier.testTag(MissionControlTags.NEW_AGENT_PROJECT).described(MissionControlStrings["newAgent.project"]),
    ) { by, _ ->
        edit(form.copy(project = ids.stepped(form.project, by)))
    }
}

@Composable
private fun ModelChoiceField(
    form: NewAgentForm,
    enabled: Boolean,
    edit: (NewAgentForm) -> Unit,
) {
    val shown = form.model.alias ?: MissionControlStrings["newAgent.model.default"]
    ChoiceField(
        shown,
        enabled,
        Modifier.testTag(MissionControlTags.NEW_AGENT_MODEL).described(MissionControlStrings["newAgent.model"]),
    ) { by, _ ->
        edit(form.copy(model = ModelChoice.entries.stepped(form.model, by)))
    }
}

/**
 * `null` is Claude Code's default: no `--permission-mode` flag. The arrows stop at the ends, so one key press from
 * the default never lands on "Pomiń zgody"; a click wraps.
 */
private val permissions: List<SessionPermissionMode?> =
    listOf(
        null,
        SessionPermissionMode.ACCEPT_EDITS,
        SessionPermissionMode.PLAN,
        SessionPermissionMode.BYPASS_PERMISSIONS,
    )

@Composable
private fun PermissionField(
    form: NewAgentForm,
    enabled: Boolean,
    edit: (NewAgentForm) -> Unit,
) {
    val shown = MissionControlStrings[permissionKey(form.permission)]
    ChoiceField(
        shown,
        enabled,
        Modifier
            .testTag(
                MissionControlTags.NEW_AGENT_PERMISSION,
            ).described(MissionControlStrings["newAgent.permission"]),
    ) { by, arrow ->
        edit(form.copy(permission = permissions.stepped(form.permission, by, wrap = !arrow)))
    }
}

private fun permissionKey(mode: SessionPermissionMode?): String =
    when (mode) {
        null, SessionPermissionMode.DEFAULT -> "newAgent.permission.default"
        SessionPermissionMode.ACCEPT_EDITS -> "newAgent.permission.acceptEdits"
        SessionPermissionMode.PLAN -> "newAgent.permission.plan"
        SessionPermissionMode.BYPASS_PERMISSIONS -> "newAgent.permission.bypass"
    }
