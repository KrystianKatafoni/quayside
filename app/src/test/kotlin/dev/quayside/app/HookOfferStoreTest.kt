package dev.quayside.app

import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.StorageError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.io.path.writeText

/** `hook-offer.json` on a real temp directory (ADR 0020). */
class HookOfferStoreTest {
    @TempDir
    lateinit var dir: Path

    private val file get() = dir.resolve("hook-offer.json")

    @Test
    fun `missing file means not dismissed`() =
        runTest {
            hookOfferStore(dir).dismissed() shouldBe false
        }

    @Test
    fun `dismiss survives a new store`() =
        runTest {
            hookOfferStore(dir).dismiss() shouldBe Outcome.Ok(Unit)

            file.exists() shouldBe true
            hookOfferStore(dir).dismissed() shouldBe true
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stored")
    fun `file content decides whether the offer is dismissed`(
        @Suppress("UNUSED_PARAMETER") name: String,
        content: String,
        expected: Boolean,
    ) = runTest {
        file.writeText(content)

        hookOfferStore(dir).dismissed() shouldBe expected
    }

    @Test
    fun `broken file means not dismissed and can be overwritten by dismiss`() =
        runTest {
            file.writeText("{ this is not json")
            val store = hookOfferStore(dir)
            store.dismissed() shouldBe false

            store.dismiss() shouldBe Outcome.Ok(Unit)

            hookOfferStore(dir).dismissed() shouldBe true
        }

    @Test
    fun `dismiss reports a storage error when the file cannot be written`(
        @TempDir other: Path,
    ) = runTest {
        val result = unwritableHookOfferStore(other).dismiss()

        result.shouldBeInstanceOf<Outcome.Err<StorageError>>()
    }

    companion object {
        @JvmStatic
        fun stored(): Stream<Arguments> =
            Stream.of(
                arguments("dismissed true", """{"schemaVersion":1,"data":{"dismissed":true}}""", true),
                arguments("dismissed false", """{"schemaVersion":1,"data":{"dismissed":false}}""", false),
                arguments("missing key", """{"schemaVersion":1,"data":{}}""", false),
                arguments("wrong type", """{"schemaVersion":1,"data":{"dismissed":"yes"}}""", false),
                arguments("unknown key is skipped", """{"schemaVersion":1,"data":{"dismissed":true,"x":1}}""", true),
                arguments("data is not an object", """{"schemaVersion":1,"data":true}""", false),
            )
    }
}
