package dev.agentmanager.sessions.api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NewSessionSpecTest {
    @ParameterizedTest(name = "{0} = [{1}]")
    @CsvSource(
        "prompt, ''",
        "prompt, ' '",
        "name, ''",
        "name, ' '",
        "agent, ''",
        "agent, ' '",
        "model, ''",
        "model, ' '",
        ignoreLeadingAndTrailingWhitespace = false,
    )
    fun `blank texts are rejected`(
        field: String,
        value: String,
    ) {
        shouldThrow<IllegalArgumentException> {
            when (field) {
                "prompt" -> NewSessionSpec(prompt = value)
                "name" -> NewSessionSpec(prompt = "go", name = value)
                "agent" -> NewSessionSpec(prompt = "go", agent = value)
                "model" -> NewSessionSpec(prompt = "go", model = value)
                else -> error("unknown field $field")
            }
        }
    }

    @Test
    fun `prompt with every optional field absent or filled is accepted`() {
        shouldNotThrowAny {
            NewSessionSpec(prompt = "go")
            NewSessionSpec(
                prompt = "go",
                name = "fix-login",
                agent = "reviewer",
                model = "opus",
                permissionMode = SessionPermissionMode.PLAN,
            )
        }
    }
}
