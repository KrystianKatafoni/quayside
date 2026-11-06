package dev.quayside.app

/** Semantics tags of the hooks dialog (AM-607). */
internal object HookTags {
    const val DIALOG = "hooks.dialog"
    const val INFO = "hooks.info"
    const val STATUS = "hooks.status"
    const val NOTICE = "hooks.notice"
    const val INSTALL = "hooks.install"
    const val UNINSTALL = "hooks.uninstall"
    const val CLOSE = "hooks.close"

    /** The row [index] of the target list (AM-609). */
    fun row(index: Int): String = "hooks.row.$index"
}
