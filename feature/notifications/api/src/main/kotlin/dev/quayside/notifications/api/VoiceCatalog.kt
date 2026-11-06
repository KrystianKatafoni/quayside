package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome

/**
 * An installed macOS voice as `say -v ?` lists it; [locale] such as `pl_PL` or `es_419`; [sample] the text after `#`
 * (trimmed, may be empty), in the voice's language. Immutable.
 */
public data class VoiceOption(
    val id: VoiceId,
    val locale: String,
    val sample: String = "",
)

/** Why [VoiceCatalog.voices] gave no list: [UNAVAILABLE] the program cannot start, [FAILED] it ran and failed. */
public enum class VoiceCatalogError { UNAVAILABLE, FAILED }

/**
 * The voices installed on this Mac (FR-N4). Safe on any dispatcher, never throws, no cache: asks the system each
 * call.
 */
public interface VoiceCatalog {
    /** Voices sorted by name ignoring case, one per name (the first listed wins); errors carry no process output. */
    public suspend fun voices(): Outcome<List<VoiceOption>, VoiceCatalogError>
}
