package dev.quayside.core.storage

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.getOrNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Behaviour every [JsonStore] must have (ENG-T2). Subclasses create a store holding a `String`
 * value that starts as [default].
 */
public abstract class JsonStoreContract {
    @TempDir
    public lateinit var dir: Path

    public abstract fun create(
        dir: Path,
        default: String,
    ): JsonStore<String>

    private val default = "0"

    @Test
    public fun `load returns the default before anything is saved`(): Unit =
        runTest {
            create(dir, default).load() shouldBe Outcome.Ok(Loaded(default))
        }

    @Test
    public fun `load returns the last saved value`(): Unit =
        runTest {
            val store = create(dir, default)

            store.save("first") shouldBe Outcome.Ok(Unit)
            store.save("second") shouldBe Outcome.Ok(Unit)

            store.load() shouldBe Outcome.Ok(Loaded("second"))
        }

    @Test
    public fun `update applies the change and returns the saved value`(): Unit =
        runTest {
            val store = create(dir, default)

            store.update { "$it+1" } shouldBe Outcome.Ok("0+1")

            store.load().getOrNull()?.value shouldBe "0+1"
        }

    @Test
    public fun `concurrent updates never lose a change`(): Unit =
        runTest {
            val store = create(dir, default)

            (1..50).map { async { store.update { (it.toInt() + 1).toString() } } }.awaitAll()

            store.load().getOrNull()?.value shouldBe "50"
        }
}
