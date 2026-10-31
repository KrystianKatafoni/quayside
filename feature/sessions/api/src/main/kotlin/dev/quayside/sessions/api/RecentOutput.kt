package dev.quayside.sessions.api

/** Output tail of a session. [text] is user data (ENG-C8): never log it; `toString` shows it as `***`. */
public data class RecentOutput(
    val text: String,
) {
    /** True when the session printed nothing yet (blank text). */
    val isEmpty: Boolean get() = text.isBlank()

    override fun toString(): String = "RecentOutput(text=$HIDDEN)"
}
