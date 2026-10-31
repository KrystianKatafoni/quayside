package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.aRuntimeSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class IsUnderTest {
    @ParameterizedTest(name = "{0} under /p/a -> {1}")
    @CsvSource(
        "/p/a, true",
        "/p/a/sub, true",
        "/p/a/../a/x, true",
        "/p/ab, false",
        "/q, false",
    )
    fun `compares by path components`(
        cwd: String,
        expected: Boolean,
    ) {
        aRuntimeSession(cwd = Path.of(cwd)).isUnder(Path.of("/p/a")) shouldBe expected
    }

    @Test
    fun `normalizes the directory too`() {
        aRuntimeSession(cwd = Path.of("/p/a/sub")).isUnder(Path.of("/p/x/../a/.")) shouldBe true
    }
}
