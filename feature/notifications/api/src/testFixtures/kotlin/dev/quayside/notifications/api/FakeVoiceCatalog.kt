package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome

/**
 * Scripted [VoiceCatalog] for tests of code that lists voices (ENG-I7). Like the real one it sorts by name ignoring
 * case and keeps the first voice of a name. [calls] counts the asks.
 */
public class FakeVoiceCatalog(
    private val result: Outcome<List<VoiceOption>, VoiceCatalogError>,
) : VoiceCatalog {
    /** A catalog that lists [voices]. */
    public constructor(voices: List<VoiceOption>) : this(Outcome.Ok(voices))

    /** How many times [voices] was called. */
    public var calls: Int = 0
        private set

    override suspend fun voices(): Outcome<List<VoiceOption>, VoiceCatalogError> {
        calls++
        return when (result) {
            is Outcome.Ok -> Outcome.Ok(result.value.distinctBy { it.id.name }.sortedBy { it.id.name.lowercase() })
            is Outcome.Err -> result
        }
    }

    public companion object {
        /** A catalog whose every ask fails with [error]. */
        public fun failing(error: VoiceCatalogError = VoiceCatalogError.FAILED): FakeVoiceCatalog =
            FakeVoiceCatalog(Outcome.Err(error))
    }
}
