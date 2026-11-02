package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour every [VoiceCatalog] must have (ENG-T2). Runs on the fake and on the `say` adapter. */
public abstract class VoiceCatalogContract {
    /** A catalog that lists [lines] (name to locale) in this order. */
    protected abstract fun catalogOf(lines: List<Pair<String, String>>): VoiceCatalog

    /** A catalog whose ask fails. */
    protected abstract fun failing(): VoiceCatalog

    private suspend fun names(lines: List<Pair<String, String>>): List<VoiceOption> =
        (catalogOf(lines).voices() as Outcome.Ok).value

    @Test
    public fun `voices are sorted by name`(): Unit =
        runTest {
            val voices = names(listOf("bob" to "pl_PL", "Carl" to "en_US", "Anna" to "de_DE"))

            voices.map { it.id.name } shouldBe listOf("Anna", "bob", "Carl")
            voices.map { it.locale } shouldBe listOf("de_DE", "pl_PL", "en_US")
        }

    @Test
    public fun `duplicates are dropped`(): Unit =
        runTest {
            val voices = names(listOf("Eddy" to "de_DE", "Zosia" to "pl_PL", "Eddy" to "pl_PL"))

            voices shouldBe
                listOf(
                    VoiceOption(VoiceId("Eddy"), "de_DE"),
                    VoiceOption(VoiceId("Zosia"), "pl_PL"),
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
