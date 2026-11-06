package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmDropdownItems
import dev.quayside.core.designsystem.AmText
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.VoiceOption

/** The "Wyjątki projektów" section (FR-N7): a profile and a voice choice for every registered project. */
@Composable
internal fun ProjectsSection(
    state: NotificationDialogState,
    controller: NotificationSettingsController,
) {
    val rows = state.draft.projects
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        AmText(SettingsStrings["settings.projects"])
        caption(SettingsTags.PROJECTS_HINT, SettingsStrings["settings.projectsHint"], mutedColor())
        if (rows.isEmpty()) {
            caption(SettingsTags.PROJECTS_EMPTY, SettingsStrings["settings.projectsEmpty"], mutedColor())
        }
        rows.forEach { ProjectBlock(it, state.voices, controller) }
    }
}

/** The name of the project and, in one row, its profile choice and its voice choice. */
@Composable
private fun ProjectBlock(
    row: ProjectDraft,
    voices: List<VoiceOption>?,
    controller: NotificationSettingsController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
        AmText(row.name)
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            val profiles = ProjectProfile.entries
            val profileItems = AmDropdownItems(profiles.map(SettingsStrings::profile), row.profile.ordinal)
            val profileLabel = SettingsStrings["settings.projectProfile"].replace("{name}", row.name)
            Dropdown(SettingsTags.projectProfile(row.id), profileLabel, profileItems) { index ->
                controller.editProject(row) { it.copy(profile = profiles[index]) }
            }
            VoiceChoiceBox(row, voices, controller)
        }
    }
}

/** The voice choice of a project: the global voice first; the saved voice stays in the list while the user steps. */
@Composable
private fun VoiceChoiceBox(
    row: ProjectDraft,
    voices: List<VoiceOption>?,
    controller: NotificationSettingsController,
) {
    val saved = remember(row.id) { row.voice }
    val choices = voiceChoices(voices, saved)
    val selected = choices.indexOfFirst { it.id == row.voice }.coerceAtLeast(minimumValue = 0)
    val items = AmDropdownItems(choices.map(SettingsStrings::projectVoice), selected)
    val label = SettingsStrings["settings.projectVoice"].replace("{name}", row.name)
    Dropdown(SettingsTags.projectVoice(row.id), label, items) { index ->
        controller.editProject(row) { it.copy(voice = choices[index].id) }
    }
}

private fun NotificationSettingsController.editProject(
    row: ProjectDraft,
    change: (ProjectDraft) -> ProjectDraft,
) {
    edit { draft -> draft.copy(projects = draft.projects.map { if (it.id == row.id) change(it) else it }) }
}
