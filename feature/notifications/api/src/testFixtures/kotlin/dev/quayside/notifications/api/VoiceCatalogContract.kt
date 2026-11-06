package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour every [VoiceCatalog] must have (ENG-T2). Runs on the fake and on the `say` adapter. */
public abstract class VoiceCatalogContract {
    /** A catalog that lists [voices] in this order. */
    protected abstract fun catalogOf(voices: List<VoiceOption>): VoiceCatalog

    /** A catalog whose ask fails. */
    protected abstract fun failing(): VoiceCatalog

    private fun voice(
        name: String,
        locale: String,
        sample: String = "",
    ) = VoiceOption(VoiceId(name), locale, sample)

    private suspend fun listed(vararg voices: VoiceOption): List<VoiceOption> =
        (catalogOf(voices.toList()).voices() as Outcome.Ok).value

    @Test
    public fun `voices are sorted by name`(): Unit =
        runTest {
            val voices = listed(voice("bob", "pl_PL"), voice("Carl", "en_US"), voice("Anna", "de_DE"))

            voices.map { it.id.name } shouldBe listOf("Anna", "bob", "Carl")
            voices.map { it.locale } shouldBe listOf("de_DE", "pl_PL", "en_US")
        }

    @Test
    public fun `duplicates are dropped`(): Unit =
        runTest {
            val voices = listed(voice("Eddy", "de_DE"), voice("Zosia", "pl_PL"), voice("Eddy", "pl_PL"))

            voices shouldBe listOf(voice("Eddy", "de_DE"), voice("Zosia", "pl_PL"))
        }

    @Test
    public fun `the sample comes with its voice`(): Unit =
        runTest {
            val voices =
                listed(
                    voice("Zosia", "pl_PL", "Witaj, nazywam się Zosia."),
                    voice("Alex", "en_US", "Hello #1 here"),
                    voice("Eddy", "de_DE", "first"),
                    voice("Eddy", "pl_PL", "second"),
                    voice("Ewa", "pl_PL"),
                )

            voices.map { it.id.name to it.sample } shouldBe
                listOf(
                    "Alex" to "Hello #1 here",
                    "Eddy" to "first",
                    "Ewa" to "",
                    "Zosia" to "Witaj, nazywam się Zosia.",
                )
        }

    @Test
    public fun `failure is a fixed category`(): Unit =
        runTest {
            val result = failing().voices()

            result.shouldBeInstanceOf<Outcome.Err<VoiceCatalogError>>()
            VoiceCatalogError.entries shouldContain result.error
        }
}
