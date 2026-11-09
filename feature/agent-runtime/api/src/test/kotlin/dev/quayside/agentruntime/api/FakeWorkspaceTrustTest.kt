package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

class FakeWorkspaceTrustTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `trusted lists each directory once in order`() =
        runTest {
            val a = dir.resolve("a").createDirectories()
            val b = dir.resolve("b").createDirectories()
            val trust = FakeWorkspaceTrust()

            trust.trust(a)
            trust.trust(b)
            trust.trust(a)

            trust.trusted.map(Path::toString) shouldBe listOf(a, b).map(Path::toString)
            trust.calls.map(Path::toString) shouldBe listOf(a, b, a).map(Path::toString)
        }

    @Test
    fun `the default accepts a path that is not on disk`() =
        runTest {
            val path = Path.of("/no/such/dir")
            val trust = FakeWorkspaceTrust()

            trust.trust(path) shouldBe Outcome.Ok(Unit)
            trust.trusted.map(Path::toString) shouldBe listOf(path).map(Path::toString)
        }

    @Test
    fun `directoryExists false gives DIRECTORY_MISSING and records nothing`() =
        runTest {
            val trust = FakeWorkspaceTrust { false }

            trust.trust(dir) shouldBe Outcome.Err(TrustError.DIRECTORY_MISSING)
            trust.trusted.shouldBeEmpty()
        }

    @Test
    fun `failNext fails once, records nothing and then works again`() =
        runTest {
            val a = dir.resolve("a").createDirectories()
            val trust = FakeWorkspaceTrust()
            trust.failNext(TrustError.CONFIG_MISSING)

            trust.trust(a) shouldBe Outcome.Err(TrustError.CONFIG_MISSING)
            trust.trusted.shouldBeEmpty()

            trust.trust(a) shouldBe Outcome.Ok(Unit)
            trust.trusted.map(Path::toString) shouldBe listOf(a).map(Path::toString)
        }
}
