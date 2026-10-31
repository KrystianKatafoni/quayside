package dev.quayside.core.storage

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

class InMemoryJsonStoreTest : JsonStoreContract() {
    override fun create(
        dir: Path,
        default: String,
    ): JsonStore<String> = InMemoryJsonStore(default)

    @Test
    fun `counts saves`() =
        runTest {
            val store = InMemoryJsonStore("a")

            store.save("b")
            store.update { "c" }

            store.saves shouldBe 2
        }

    @Test
    fun `simulates failures and a recovered broken file`() =
        runTest {
            val store = InMemoryJsonStore("a")
            val broken = BrokenFile(Path.of("/tmp/a.json.broken"), "invalid JSON")

            store.recoverNextLoad = broken
            store.load() shouldBe Outcome.Ok(Loaded("a", broken))
            store.load() shouldBe Outcome.Ok(Loaded("a"))

            store.failure = StorageError.IoFailed(Path.of("/tmp/a.json"), "disk full")
            store.load() shouldBe Outcome.Err(store.failure)
            store.save("b") shouldBe Outcome.Err(store.failure)
            store.saves shouldBe 0
        }
}
