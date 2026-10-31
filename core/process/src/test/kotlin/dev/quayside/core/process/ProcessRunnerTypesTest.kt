package dev.quayside.core.process

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration

class ProcessRunnerTypesTest {
    @Test
    fun `run options reject non-positive limits`() {
        shouldThrow<IllegalArgumentException> { RunOptions(timeout = Duration.ZERO) }
        shouldThrow<IllegalArgumentException> { RunOptions(maxOutputBytes = 0) }
    }

    @Test
    fun `only exit code zero is success`() {
        ProcessResult(0, "", "").isSuccess shouldBe true
        ProcessResult(1, "", "").isSuccess shouldBe false
    }
}
