package dev.agentmanager.agentruntime.api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RuntimeTypesTest {
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
    fun `StartSpec rejects blank texts`(
        field: String,
        value: String,
    ) {
        shouldThrow<IllegalArgumentException> {
            when (field) {
                "prompt" -> StartSpec(cwd = A_PROJECT_DIR, prompt = value)
                "name" -> StartSpec(cwd = A_PROJECT_DIR, prompt = "go", name = value)
                "agent" -> StartSpec(cwd = A_PROJECT_DIR, prompt = "go", agent = value)
                "model" -> StartSpec(cwd = A_PROJECT_DIR, prompt = "go", model = value)
                else -> error("unknown field $field")
            }
        }
    }

    @Test
    fun `StartSpec accepts a prompt with every optional field absent or filled`() {
        shouldNotThrowAny {
            StartSpec(cwd = A_PROJECT_DIR, prompt = "go")
            StartSpec(
                cwd = A_PROJECT_DIR,
                prompt = "go",
                name = "fix-login",
                agent = "reviewer",
                model = "opus",
                permissionMode = PermissionMode.PLAN,
            )
        }
    }

    @Test
    fun `StartSpec toString hides the prompt`() {
        StartSpec(cwd = A_PROJECT_DIR, prompt = "secret").toString() shouldNotContain "secret"
    }

    @Test
    fun `hook events hide their free texts in toString`() {
        AgentEvent.NeedsInput(anEventMeta(), question = "secret question").toString() shouldNotContain "secret"
        AgentEvent.TurnEnded(anEventMeta(), lastMessage = "secret message").toString() shouldNotContain "secret"
    }

    @ParameterizedTest(name = "ConversationId([{0}])")
    @ValueSource(strings = ["", " "])
    fun `ConversationId rejects a blank value`(value: String) {
        shouldThrow<IllegalArgumentException> { ConversationId(value) }
    }

    @ParameterizedTest(name = "BackupRef([{0}])")
    @ValueSource(strings = ["", " "])
    fun `BackupRef rejects a blank value`(value: String) {
        shouldThrow<IllegalArgumentException> { BackupRef(value) }
    }

    @Test
    fun `non-blank ids keep their value`() {
        ConversationId("0b7e5c1e").value shouldBe "0b7e5c1e"
        BackupRef("hooks-backup/2026-01-01.json").value shouldBe "hooks-backup/2026-01-01.json"
    }

    @Test
    fun `health without problems is ready`() {
        health(problems = emptyList()).isReady shouldBe true
    }

    @Test
    fun `health with a problem is not ready`() {
        health(problems = listOf(RuntimeError.AgentViewDisabled)).isReady shouldBe false
    }

    private fun health(problems: List<RuntimeError>) =
        RuntimeHealth(
            version = RuntimeRequirements.MIN_CLAUDE_VERSION,
            agentViewEnabled = true,
            supervisorRunning = true,
            problems = problems,
        )
}
