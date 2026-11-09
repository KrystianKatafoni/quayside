package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome

/**
 * A macOS system sound by the file name without extension (`Glass` for `/System/Library/Sounds/Glass.aiff`). Not
 * checked against the installed sounds; the name cannot hold a path separator, so it cannot leave the sounds
 * directory.
 *
 * @throws IllegalArgumentException when [name] is not valid; text typed or stored by the user goes through [parse].
 */
@JvmInline
public value class SoundId(
    public val name: String,
) {
    init {
        require(isValid(name)) { "bad sound name" }
    }

    public companion object {
        /** Longest accepted name. */
        public const val MAX_LENGTH: Int = 64

        private val VALID = Regex("""[\p{L}\p{N}][\p{L}\p{N} _.-]*""")

        /** True for a name that starts with a letter or digit and holds only letters, digits, space, `_`, `.`, `-`. */
        public fun isValid(name: String): Boolean = name.length <= MAX_LENGTH && VALID.matches(name)

        /** The sound named [name] (trimmed), or null when it is not valid. */
        public fun parse(name: String): SoundId? = name.trim().takeIf(::isValid)?.let(::SoundId)
    }
}

/**
 * Which sound the sound channel plays and how loud (FR-N1). A kind missing from [sounds] uses [defaultSound], so
 * "restore default" is a removal.
 *
 * @property volume percent of the system volume, [MIN_VOLUME]..[MAX_VOLUME].
 * @property sounds the sound chosen per kind; see [soundFor].
 */
public data class SoundSettings(
    val volume: Int = DEFAULT_VOLUME,
    val sounds: Map<NotificationKind, SoundId> = emptyMap(),
) {
    init {
        require(volume in MIN_VOLUME..MAX_VOLUME) { "volume out of range" }
    }

    /** The sound of [kind]: the entry in [sounds] or [defaultSound]. */
    public fun soundFor(kind: NotificationKind): SoundId = sounds[kind] ?: defaultSound(kind)

    public companion object {
        public const val DEFAULT_VOLUME: Int = 100
        public const val MIN_VOLUME: Int = 0
        public const val MAX_VOLUME: Int = 100

        /** The default sound of [kind]. */
        public fun defaultSound(kind: NotificationKind): SoundId =
            SoundId(
                when (kind) {
                    NotificationKind.NEEDS_INPUT -> "Glass"
                    NotificationKind.PERMISSION -> "Ping"
                    NotificationKind.FAILED -> "Basso"
                    NotificationKind.TURN_ENDED -> "Hero"
                    NotificationKind.PULL_REQUEST -> "Pop"
                    NotificationKind.RUNNING_LONG -> "Submarine"
                    NotificationKind.TERMINAL_BELL -> "Tink"
                },
            )
    }
}

/** Why [SoundLibrary] did nothing: [UNAVAILABLE] the folder or player cannot be used; [FAILED] it ran and failed. */
public enum class SoundLibraryError { UNAVAILABLE, FAILED }

/**
 * The system sounds and their preview in the settings dialog (FR-N1). Safe on any dispatcher, never throws, no
 * cache; errors carry no process output or paths.
 */
public interface SoundLibrary {
    /** The installed sounds sorted by name ignoring case. */
    public suspend fun sounds(): Outcome<List<SoundId>, SoundLibraryError>

    /**
     * Plays [sound] at [volume] percent and suspends until it ends; cancelling the caller stops it. A sound that is
     * not installed is [SoundLibraryError.FAILED] (no fallback sound).
     *
     * @throws IllegalArgumentException when [volume] is outside the range of [SoundSettings].
     */
    public suspend fun play(
        sound: SoundId,
        volume: Int,
    ): Outcome<Unit, SoundLibraryError>
}
