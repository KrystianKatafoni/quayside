package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Scripted [SoundLibrary] for tests of code that lists and previews sounds (ENG-I7). [list] is the answer of
 * [sounds] (sorted by the fake), [result] the answer of every [play] of an installed sound; a sound outside [list]
 * is [SoundLibraryError.FAILED], and a failing [list] fails [play] with its error. [played] lists the plays that
 * started; [cancelled] counts those stopped by cancelling the caller while they waited for [gate].
 */
public class FakeSoundLibrary(
    public var list: Outcome<List<SoundId>, SoundLibraryError> = Outcome.Ok(DEFAULT_SOUNDS),
    public var result: Outcome<Unit, SoundLibraryError> = Outcome.Ok(Unit),
) : SoundLibrary {
    private val plays = mutableListOf<Pair<SoundId, Int>>()

    /** The plays that started (sound, volume), in order; a refused one is not among them. */
    public val played: List<Pair<SoundId, Int>> get() = synchronized(plays) { plays.toList() }

    /** How many plays were cancelled while they waited for [gate]. */
    public var cancelled: Int = 0
        private set

    /** How many times [sounds] was called. */
    public var listCalls: Int = 0
        private set

    /** When set, [play] suspends until it completes. */
    public var gate: CompletableDeferred<Unit>? = null

    override suspend fun sounds(): Outcome<List<SoundId>, SoundLibraryError> {
        listCalls++
        return when (val current = list) {
            is Outcome.Ok -> Outcome.Ok(current.value.sortedBy { it.name.lowercase() })
            is Outcome.Err -> current
        }
    }

    override suspend fun play(
        sound: SoundId,
        volume: Int,
    ): Outcome<Unit, SoundLibraryError> {
        require(volume in SoundSettings.MIN_VOLUME..SoundSettings.MAX_VOLUME) { "volume out of range" }
        val installed =
            when (val current = list) {
                is Outcome.Ok -> current.value
                is Outcome.Err -> return current
            }
        if (sound !in installed) return Outcome.Err(SoundLibraryError.FAILED)
        synchronized(plays) { plays += sound to volume }
        try {
            gate?.await()
        } catch (e: CancellationException) {
            cancelled++
            throw e
        }
        return result
    }

    public companion object {
        /** The sounds of the default mapping. */
        public val DEFAULT_SOUNDS: List<SoundId> =
            NotificationKind.entries.map { SoundSettings.defaultSound(it) }
    }
}
