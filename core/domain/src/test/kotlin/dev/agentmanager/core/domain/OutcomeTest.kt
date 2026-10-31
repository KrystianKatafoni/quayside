package dev.agentmanager.core.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class OutcomeTest {
    private sealed interface TestError {
        data object NotFound : TestError

        data class Io(
            val reason: String,
        ) : TestError
    }

    private val ok: Outcome<Int, TestError> = Outcome.Ok(2)
    private val err: Outcome<Int, TestError> = Outcome.Err(TestError.NotFound)

    @Test
    fun `accessors expose value or error`() {
        ok.isOk shouldBe true
        ok.getOrNull() shouldBe 2
        ok.errorOrNull() shouldBe null
        err.isOk shouldBe false
        err.getOrNull() shouldBe null
        err.errorOrNull() shouldBe TestError.NotFound
    }

    @Test
    fun `getOrElse and fold handle both branches`() {
        ok.getOrElse { -1 } shouldBe 2
        err.getOrElse { -1 } shouldBe -1
        ok.fold({ "ok $it" }, { "err $it" }) shouldBe "ok 2"
        err.fold({ "ok $it" }, { "err $it" }) shouldBe "err NotFound"
    }

    @Test
    fun `map transforms only the success value`() {
        ok.map { it * 10 } shouldBe Outcome.Ok(20)
        err.map { it * 10 } shouldBe err
    }

    @Test
    fun `mapError transforms only the error`() {
        err.mapError { "mapped" } shouldBe Outcome.Err("mapped")
        ok.mapError { "mapped" } shouldBe ok
    }

    @Test
    fun `flatMap chains and short-circuits on the first error`() {
        val half: (Int) -> Outcome<Int, TestError> = { n ->
            if (n % 2 == 0) Outcome.Ok(n / 2) else Outcome.Err(TestError.Io("odd $n"))
        }

        ok.flatMap(half) shouldBe Outcome.Ok(1)
        ok.flatMap(half).flatMap(half) shouldBe Outcome.Err(TestError.Io("odd 1"))
        err.flatMap(half) shouldBe err
    }

    @Test
    fun `catching maps exceptions to domain errors`() {
        catching<Int, TestError>({ TestError.Io(it.message.orEmpty()) }) { 7 } shouldBe Outcome.Ok(7)
        catching<Int, TestError>({ TestError.Io(it.message.orEmpty()) }) {
            throw IOException("disk full")
        } shouldBe Outcome.Err(TestError.Io("disk full"))
    }

    @Test
    fun `catching rethrows cancellation`() {
        shouldThrow<CancellationException> {
            catching<Int, TestError>({ TestError.NotFound }) { throw CancellationException("cancelled") }
        }
    }
}
