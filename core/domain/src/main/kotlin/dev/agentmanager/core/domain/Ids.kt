package dev.agentmanager.core.domain

/** Stable identifier of a project registered in Agent Manager. */
@JvmInline
public value class ProjectId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "ProjectId must not be blank" }
    }

    override fun toString(): String = value
}

/** Short session identifier assigned by the Claude Code supervisor, e.g. `7c5dcf5d`. */
@JvmInline
public value class SessionId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "SessionId must not be blank" }
    }

    override fun toString(): String = value
}
