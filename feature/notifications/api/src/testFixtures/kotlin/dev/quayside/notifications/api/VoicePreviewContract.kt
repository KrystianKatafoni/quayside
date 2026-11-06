package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour every [VoicePreview] must have (ENG-T2). Runs on the fake and on the `say` adapter. */
public abstract class VoicePreviewContract {
    /** A preview whose play ends well; [started] then counts the programs it started. */
    protected abstract fun working(): VoicePreview

    /** A preview whose play fails. */
    protected abstract fun failing(): VoicePreview

    /** How many programs the last preview made by [working] started. */
    protected abstract fun started(): Int

    private fun sample(text: String) = VoiceSample(VoiceId("Zosia"), text, rate = 190, volume = 70)

    @Test
    public fun `a sample that plays ends Ok`(): Unit =
        runTest {
            val result = working().play(sample("Witaj, nazywam się Zosia."))

            result shouldBe Outcome.Ok(Unit)
            started() shouldBe 1
        }

    @Test
    public fun `embedded commands are refused`(): Unit =
        runTest {
            val preview = working()

            listOf("[[rate 500]] x", "x ]] y").forEach { text ->
                preview.play(sample(text)) shouldBe Outcome.Err(VoicePreviewError.FAILED)
            }

            started() shouldBe 0
        }

    @Test
    public fun `failure is a fixed category`(): Unit =
        runTest {
            val result = failing().play(sample("Hello"))

            result.shouldBeInstanceOf<Outcome.Err<VoicePreviewError>>()
            VoicePreviewError.entries shouldContain result.error
        }
}
