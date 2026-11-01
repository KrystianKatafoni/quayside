package dev.quayside.notifications.ui

import dev.quayside.notifications.api.PopupId

/** Test tags of the popup stack. */
internal object PopupTags {
    const val STACK = "popup.stack"

    fun card(id: PopupId): String = "popup.card.${id.value}"

    fun attach(id: PopupId): String = "popup.attach.${id.value}"

    fun mute(id: PopupId): String = "popup.mute.${id.value}"

    fun dismiss(id: PopupId): String = "popup.dismiss.${id.value}"
}
