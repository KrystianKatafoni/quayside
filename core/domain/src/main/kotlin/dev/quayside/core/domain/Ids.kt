package dev.quayside.core.domain

/** Stable identifier of a project registered in Quayside. */
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

/**
 * Stable id of a Quayside account: a slug `[a-z0-9-]{1,32}`; [DEFAULT] is the account in `~/.claude`.
 * The name `auto` is reserved for the automatic choice and is never produced as an account id (ADR 0035).
 *
 * @throws IllegalArgumentException when [value] is not a slug (programmer error; user input goes through
 * the slug rules of `accounts:api`).
 */
@JvmInline
public value class AccountId(
    public val value: String,
) {
    init {
        require(SLUG.matches(value)) { "AccountId must be a slug of 1 to 32 characters [a-z0-9-]" }
    }

    override fun toString(): String = value

    public companion object {
        // Declared before DEFAULT: the companion initializes top to bottom and DEFAULT already validates.
        private val SLUG = Regex("[a-z0-9-]{1,32}")

        /** The account in `~/.claude`; always present and always run without `CLAUDE_CONFIG_DIR`. */
        public val DEFAULT: AccountId = AccountId("default")
    }
}
