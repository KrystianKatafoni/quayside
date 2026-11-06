package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Scripted [VoicePreview] for tests of code that plays samples (ENG-I7). Like the real one it refuses a text with
 * `[[` or `]]` without playing it. [played] lists the samples that started, [cancelled] counts the plays stopped by
 * cancelling the caller. While [gate] is set, [play] waits for it (a sample that is still playing).
 */
public class FakeVoicePreview(
    /** The answer of every play that is not refused. */
    public var result: Outcome<Unit, VoicePreviewError> = Outcome.Ok(Unit),
) : VoicePreview {
    private val samples = mutableListOf<VoiceSample>()

    /** The samples that started, in order; a refused one is not among them. */
    public val played: List<VoiceSample> get() = synchronized(samples) { samples.toList() }

    /** How many plays were cancelled while they waited for [gate]. */
    public var cancelled: Int = 0
        private set

    /** When set, [play] suspends until it completes. */
    public var gate: CompletableDeferred<Unit>? = null

    override suspend fun play(sample: VoiceSample): Outcome<Unit, VoicePreviewError> {
        if (sample.text.contains("[[") || sample.text.contains("]]")) return Outcome.Err(VoicePreviewError.FAILED)
        synchronized(samples) { samples += sample }
        try {
            gate?.await()
        } catch (e: CancellationException) {
            cancelled++
            throw e
        }
        return result
    }

    public companion object {
        /** A preview whose every play fails with [error]. */
        public fun failing(error: VoicePreviewError = VoicePreviewError.FAILED): FakeVoicePreview =
            FakeVoicePreview(Outcome.Err(error))
    }
}
