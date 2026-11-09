package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.missioncontrol.ui.NewAgentWorld.OTHER
import dev.quayside.missioncontrol.ui.NewAgentWorld.PROJECTS
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionPermissionMode
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

/** Pure rules of the "Nowy agent" dialog (FR-S3, AM-608 rules 1, 2 and 5): validation, defaults, failure texts. */
internal class NewAgentRulesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `form becomes a spec or the first problem`(
        @Suppress("UNUSED_PARAMETER") name: String,
        form: NewAgentForm,
        expected: Outcome<Pair<ProjectId, NewSessionSpec>, NewAgentProblem>,
    ) {
        NewAgentRules.specOf(form) shouldBe expected
    }

    @Test
    fun `limits are 100000 code points of prompt and 80 of name`() {
        NewAgentRules.MAX_PROMPT shouldBe 100_000
        NewAgentRules.MAX_NAME shouldBe 80
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ModelChoice::class)
    fun `model reaches the spec as its alias`(model: ModelChoice) {
        val spec = NewAgentRules.specOf(NewAgentForm(QUAYSIDE.id, prompt = "fix", model = model)).spec()

        spec.model shouldBe model.alias
    }

    @Test
    fun `model aliases are the CLI aliases`() {
        ModelChoice.entries.map { it.alias } shouldBe listOf(null, "sonnet", "opus", "haiku")
    }

    @Test
    fun `permission without a choice sends no mode`() {
        NewAgentRules.specOf(NewAgentForm(QUAYSIDE.id, prompt = "fix")).spec().permissionMode shouldBe null
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(SessionPermissionMode::class)
    fun `permission reaches the spec unchanged`(mode: SessionPermissionMode) {
        val form = NewAgentForm(QUAYSIDE.id, prompt = "fix", permission = mode)

        NewAgentRules.specOf(form).spec().permissionMode shouldBe mode
    }

    @Test
    fun `the project of the form is the project of the result`() {
        val result = NewAgentRules.specOf(NewAgentForm(OTHER.id, prompt = "fix"))

        (result as Outcome.Ok).value.first shouldBe OTHER.id
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failureCases")
    fun `start error gives a fixed text`(
        @Suppress("UNUSED_PARAMETER") name: String,
        error: SessionError,
        key: String,
    ) {
        val reason = NewAgentRules.failureOf(error)

        reason shouldBe UiText(key)
        MissionControlStrings.format(reason).shouldNotBeBlank()
    }

    @Test
    fun `command failed mentions trust and the terminal`() {
        val text = failure(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))

        text shouldBe
            "Claude Code odmówił startu. Najczęstsza przyczyna: katalog nie jest zaufany. " +
            "Otwórz terminal w katalogu projektu (⌘T), uruchom `claude`, zaakceptuj zaufanie i spróbuj ponownie. " +
            "Sprawdź też nazwę subagenta."
    }

    @Test
    fun `unknown project says it is gone from the list`() {
        failure(SessionError.UnknownProject(QUAYSIDE.id)) shouldBe "Projektu nie ma już na liście"
    }

    @Test
    fun `timeout and unparseable answer say the result is unknown`() {
        listOf(RuntimeProblem.TIMEOUT, RuntimeProblem.UNPARSEABLE).forEach {
            withClue(it) {
                failure(SessionError.Runtime(it)) shouldBe
                    "Nie wiadomo, czy agent wystartował; sprawdź listę sesji przed ponownym startem."
            }
        }
    }

    @Test
    fun `unsupported version names the required version`() {
        failure(SessionError.Runtime(RuntimeProblem.UNSUPPORTED_VERSION)) shouldContain "2.1.139"
    }

    @Test
    fun `fix hints name the thing to fix`() {
        failure(SessionError.Runtime(RuntimeProblem.NOT_INSTALLED)) shouldContain "Claude Code"
        failure(SessionError.Runtime(RuntimeProblem.AGENT_VIEW_DISABLED)) shouldContain "Agent View"
    }

    @Test
    fun `every start error gives a text without arguments and without ids`() {
        val secret = SessionId("secret-session")
        val errors =
            listOf(
                SessionError.UnknownProject(ProjectId("secret-project")),
                SessionError.UnknownSession(secret),
                SessionError.SessionActive(secret),
                SessionError.StopIncomplete(stopped = 1, failed = listOf(secret)),
            ) + RuntimeProblem.entries.map { SessionError.Runtime(it) }

        errors.forEach { error ->
            withClue(error) {
                val reason = NewAgentRules.failureOf(error)
                reason.args shouldBe emptyList()
                MissionControlStrings.format(reason).shouldNotContain("secret")
            }
        }
    }

    @Test
    fun `form toString hides prompt and name`() {
        val form =
            NewAgentForm(
                QUAYSIDE.id,
                prompt = NewAgentWorld.SECRET_PROMPT,
                name = NewAgentWorld.SECRET_NAME,
                agent = "code-reviewer",
            )

        form.toString().shouldNotContain(NewAgentWorld.SECRET_PROMPT)
        form.toString().shouldNotContain(NewAgentWorld.SECRET_NAME)
        NewAgentState(form, PROJECTS).toString().shouldNotContain(NewAgentWorld.SECRET_PROMPT)
        NewAgentState(form, PROJECTS).toString().shouldNotContain(NewAgentWorld.SECRET_NAME)
    }

    @Test
    fun `form toString still names the other fields`() {
        val form = NewAgentForm(QUAYSIDE.id, prompt = "p", model = ModelChoice.OPUS)

        form.toString() shouldContain "OPUS"
        form.toString() shouldContain QUAYSIDE.id.value
    }

    private fun failure(error: SessionError): String = MissionControlStrings.format(NewAgentRules.failureOf(error))

    private fun Outcome<Pair<ProjectId, NewSessionSpec>, NewAgentProblem>.spec(): NewSessionSpec =
        (this as Outcome.Ok).value.second

    companion object {
        private val P = QUAYSIDE.id
        private const val LOTS = 100_000
        private const val EMOJI = "😀"

        private fun ok(spec: NewSessionSpec) = Outcome.Ok(P to spec)

        private fun problem(problem: NewAgentProblem) = Outcome.Err(problem)

        private fun form(
            prompt: String = "fix",
            name: String = "",
            agent: String = "",
        ) = NewAgentForm(P, prompt = prompt, name = name, agent = agent)

        @JvmStatic
        @Suppress("LongMethod") // a table of cases
        fun cases(): List<Arguments> =
            listOf(
                arguments("no project", NewAgentForm(null, prompt = "fix"), problem(NewAgentProblem.NoProject)),
                arguments("no project wins over empty prompt", NewAgentForm(null), problem(NewAgentProblem.NoProject)),
                arguments("blank prompt", form(prompt = " \n "), problem(NewAgentProblem.EmptyPrompt)),
                arguments(
                    "empty prompt wins over bad name",
                    form(prompt = "", name = "-x"),
                    problem(NewAgentProblem.EmptyPrompt),
                ),
                arguments(
                    "prompt of exactly the maximum",
                    form(prompt = "a".repeat(LOTS)),
                    ok(NewSessionSpec("a".repeat(LOTS))),
                ),
                arguments(
                    "prompt over the maximum",
                    form(prompt = "a".repeat(LOTS + 1)),
                    problem(NewAgentProblem.PromptTooLong),
                ),
                arguments(
                    "maximum counts code points",
                    form(prompt = EMOJI.repeat(LOTS)),
                    ok(NewSessionSpec(EMOJI.repeat(LOTS))),
                ),
                arguments(
                    "over maximum in code points",
                    form(prompt = EMOJI.repeat(LOTS + 1)),
                    problem(NewAgentProblem.PromptTooLong),
                ),
                arguments("prompt may start with a dash", form(prompt = "--help me"), ok(NewSessionSpec("--help me"))),
                arguments("prompt is trimmed", form(prompt = " fix "), ok(NewSessionSpec("fix"))),
                arguments("name starting with a dash", form(name = "-x"), problem(NewAgentProblem.NameInvalid)),
                arguments(
                    "name with a space before the dash",
                    form(name = " -x"),
                    problem(NewAgentProblem.NameInvalid),
                ),
                arguments("name with a new line", form(name = "a\nb"), problem(NewAgentProblem.NameInvalid)),
                arguments("name with a carriage return", form(name = "a\rb"), problem(NewAgentProblem.NameInvalid)),
                arguments("name of 81 characters", form(name = "a".repeat(81)), problem(NewAgentProblem.NameInvalid)),
                arguments(
                    "name of exactly 80",
                    form(name = "a".repeat(80)),
                    ok(NewSessionSpec("fix", name = "a".repeat(80))),
                ),
                arguments(
                    "80 code points of name",
                    form(name = EMOJI.repeat(80)),
                    ok(NewSessionSpec("fix", name = EMOJI.repeat(80))),
                ),
                arguments(
                    "81 code points of name",
                    form(name = EMOJI.repeat(81)),
                    problem(NewAgentProblem.NameInvalid),
                ),
                arguments("dash inside a name is fine", form(name = "a-b"), ok(NewSessionSpec("fix", name = "a-b"))),
                arguments("name is trimmed", form(name = "  my task  "), ok(NewSessionSpec("fix", name = "my task"))),
                arguments("blank name is none", form(name = " "), ok(NewSessionSpec("fix"))),
                arguments("agent with a space", form(agent = "code reviewer"), problem(NewAgentProblem.AgentInvalid)),
                arguments("agent starting with a dash", form(agent = "-x"), problem(NewAgentProblem.AgentInvalid)),
                arguments("agent with a semicolon", form(agent = "a;b"), problem(NewAgentProblem.AgentInvalid)),
                arguments(
                    "agent starting with an underscore",
                    form(agent = "_x"),
                    problem(NewAgentProblem.AgentInvalid),
                ),
                arguments(
                    "agent with a non ASCII letter",
                    form(agent = "zażółć"),
                    problem(NewAgentProblem.AgentInvalid),
                ),
                arguments(
                    "agent of 65 characters",
                    form(agent = "a".repeat(65)),
                    problem(NewAgentProblem.AgentInvalid),
                ),
                arguments(
                    "agent of exactly 64",
                    form(agent = "a".repeat(64)),
                    ok(NewSessionSpec("fix", agent = "a".repeat(64))),
                ),
                arguments(
                    "agent with dot, underscore and dash",
                    form(agent = "a.b_c-d"),
                    ok(NewSessionSpec("fix", agent = "a.b_c-d")),
                ),
                arguments("blank agent is none", form(agent = ""), ok(NewSessionSpec("fix"))),
                arguments(
                    "options reach the spec",
                    NewAgentForm(
                        P,
                        "fix",
                        agent = "code-reviewer",
                        model = ModelChoice.OPUS,
                        permission = SessionPermissionMode.PLAN,
                    ),
                    ok(
                        NewSessionSpec(
                            "fix",
                            agent = "code-reviewer",
                            model = "opus",
                            permissionMode = SessionPermissionMode.PLAN,
                        ),
                    ),
                ),
                arguments(
                    "padded prompt, blank name and agent",
                    NewAgentForm(P, prompt = " fix ", name = " ", agent = ""),
                    ok(NewSessionSpec("fix", name = null, agent = null)),
                ),
            )

        @JvmStatic
        @Suppress("LongMethod") // a table of cases
        fun failureCases(): List<Arguments> =
            listOf(
                arguments("unknown project", SessionError.UnknownProject(P), "newAgent.failure.unknownProject"),
                arguments(
                    "not installed",
                    SessionError.Runtime(RuntimeProblem.NOT_INSTALLED),
                    "newAgent.failure.notInstalled",
                ),
                arguments(
                    "unsupported version",
                    SessionError.Runtime(RuntimeProblem.UNSUPPORTED_VERSION),
                    "newAgent.failure.unsupportedVersion",
                ),
                arguments(
                    "agent view disabled",
                    SessionError.Runtime(RuntimeProblem.AGENT_VIEW_DISABLED),
                    "newAgent.failure.agentViewDisabled",
                ),
                arguments(
                    "command failed",
                    SessionError.Runtime(RuntimeProblem.COMMAND_FAILED),
                    "newAgent.failure.commandFailed",
                ),
                arguments("timeout", SessionError.Runtime(RuntimeProblem.TIMEOUT), "newAgent.failure.unknownResult"),
                arguments(
                    "unparseable",
                    SessionError.Runtime(RuntimeProblem.UNPARSEABLE),
                    "newAgent.failure.unknownResult",
                ),
                arguments("unknown session", SessionError.UnknownSession(SessionId("s")), "newAgent.failure.other"),
                arguments("session active", SessionError.SessionActive(SessionId("s")), "newAgent.failure.other"),
                arguments("stop incomplete", SessionError.StopIncomplete(0, emptyList()), "newAgent.failure.other"),
            )
    }
}
