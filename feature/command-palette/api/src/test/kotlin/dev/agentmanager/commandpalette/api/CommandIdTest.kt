package dev.agentmanager.commandpalette.api

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CommandIdTest {
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["", " ", "   ", "\t"])
    fun `blank id is rejected`(value: String) {
        shouldThrow<IllegalArgumentException> { CommandId(value) }
    }
}
