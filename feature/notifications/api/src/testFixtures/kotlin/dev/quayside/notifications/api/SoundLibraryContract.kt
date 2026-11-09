package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour every [SoundLibrary] must have (ENG-T2). Runs on the fake and on the `afplay` adapter. */
public abstract class SoundLibraryContract {
    /** A library that has exactly the sounds Glass, hero and Basso and plays them well. */
    protected abstract fun working(): SoundLibrary

    /** A library whose list and play fail. */
    protected abstract fun failing(): SoundLibrary

    @Test
    public fun `sounds are sorted by name ignoring case`(): Unit =
        runTest {
            val result = working().sounds()

            result shouldBe Outcome.Ok(listOf(SoundId("Basso"), SoundId("Glass"), SoundId("hero")))
        }

    @Test
    public fun `an installed sound plays Ok`(): Unit =
        runTest {
            working().play(SoundId("Glass"), volume = 50) shouldBe Outcome.Ok(Unit)
        }

    @Test
    public fun `a sound that is not installed fails`(): Unit =
        runTest {
            working().play(SoundId("Missing"), volume = 50) shouldBe Outcome.Err(SoundLibraryError.FAILED)
        }

    @Test
    public fun `a volume out of range is refused`(): Unit =
        runTest {
            val library = working()

            shouldThrow<IllegalArgumentException> { library.play(SoundId("Glass"), volume = 101) }
            shouldThrow<IllegalArgumentException> { library.play(SoundId("Glass"), volume = -1) }
        }

    @Test
    public fun `failure is a fixed category`(): Unit =
        runTest {
            val library = failing()

            (library.sounds() as Outcome.Err).error shouldBe SoundLibraryError.UNAVAILABLE
            (library.play(SoundId("Glass"), volume = 50) as Outcome.Err).error shouldBe SoundLibraryError.UNAVAILABLE
        }
}
