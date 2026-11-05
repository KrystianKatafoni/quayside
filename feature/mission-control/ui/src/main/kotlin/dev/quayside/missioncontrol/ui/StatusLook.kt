package dev.quayside.missioncontrol.ui

import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.sessions.api.SessionState

/** Icon, tone and text key of a state (docs/05 table); the icons differ in shape (NFR-6). */
internal object StatusLook {
    fun of(state: SessionState): Glyph =
        when (state) {
            is SessionState.AwaitingInput -> {
                Glyph(IconKind.WAITING, StatusTone.WARN, "state.awaitingInput")
            }

            is SessionState.AwaitingPermission -> {
                Glyph(
                    IconKind.PERMISSION,
                    StatusTone.WARN,
                    "state.awaitingPermission",
                )
            }

            SessionState.Working -> {
                Glyph(IconKind.WORKING, StatusTone.WORK, "state.working")
            }

            SessionState.Done -> {
                Glyph(IconKind.DONE, StatusTone.OK, "state.done")
            }

            SessionState.Failed -> {
                Glyph(IconKind.FAILED, StatusTone.ERR, "state.failed")
            }

            SessionState.Stopped -> {
                Glyph(IconKind.STOPPED, StatusTone.NEUTRAL, "state.stopped")
            }
        }
}
