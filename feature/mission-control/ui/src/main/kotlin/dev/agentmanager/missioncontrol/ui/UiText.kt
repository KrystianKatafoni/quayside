package dev.agentmanager.missioncontrol.ui

/** A text of `i18n/missioncontrol.properties`: its [key] and the `{0}`, `{1}`, … arguments. Never holds a path. */
internal data class UiText(
    val key: String,
    val args: List<String> = emptyList(),
)
