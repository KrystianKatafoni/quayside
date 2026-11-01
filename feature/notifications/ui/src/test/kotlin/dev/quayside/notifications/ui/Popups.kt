package dev.quayside.notifications.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupModel

/** A popup for tests; [collapsed] and [session] vary the card. */
internal fun aPopup(
    id: String,
    kind: NotificationKind = NotificationKind.NEEDS_INPUT,
    collapsed: Boolean = false,
    session: SessionId? = SessionId("s-$id"),
    detail: String? = "Która migracja?",
): PopupModel =
    PopupModel(
        id = PopupId(id),
        kind = kind,
        session = session,
        project = "alpha",
        sessionName = "refactor",
        detail = detail,
        sticky = true,
        collapsed = collapsed,
    )
