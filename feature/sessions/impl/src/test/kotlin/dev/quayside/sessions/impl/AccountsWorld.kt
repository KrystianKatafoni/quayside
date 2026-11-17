@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.FakeSessionHistory
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration.Companion.seconds

/** The account `work` of the AM-1003 tests; the default account is [AccountId.DEFAULT]. */
internal val WORK = AccountId("work")

/**
 * `sessionsModule` over a default runtime and an account runtime `work` (AM-1003): both are [SwitchableRuntime]s over a
 * [FakeAgentRuntime], so a test can switch one account off and read when each was listed. [extra] is the
 * `extraRuntimes` the module watches; a test changes it to add or drop the account. No loop has run when the world is
 * built: call `runCurrent` first.
 */
internal class AccountsWorld(
    val default: SwitchableRuntime,
    val work: SwitchableRuntime,
    val extra: MutableStateFlow<Map<AccountId, AgentRuntime>>,
    val hooks: FakeAgentEventSource,
    val logger: RecordingLogger,
    val api: SessionsApi,
) {
    val sessions: List<Session> get() = api.monitor.snapshot.value.sessions

    fun session(id: String): Session = sessions.single { it.id == SessionId(id) }

    fun listedIds(): List<String> = sessions.map { it.id.value }

    /** Calls of the runtime except listings: what the commands asked of it. */
    fun commandCalls(runtime: SwitchableRuntime): List<String> = runtime.fake.calls.filterNot { it == "listSessions" }
}

/**
 * Project P1 at [P1_DIR]; poll interval 2 s, at most 15 s. [withWork] false = `work` is not in `extraRuntimes` yet.
 * [history] gives the world disk sessions.
 */
internal fun TestScope.accountsWorld(
    defaultSessions: List<RuntimeSession> = emptyList(),
    workSessions: List<RuntimeSession> = emptyList(),
    withWork: Boolean = true,
    history: FakeSessionHistory? = null,
): AccountsWorld {
    val now = { testScheduler.currentTime }
    val default = SwitchableRuntime(FakeAgentRuntime(RuntimeWorld(sessions = defaultSessions)), now)
    val work = SwitchableRuntime(FakeAgentRuntime(RuntimeWorld(sessions = workSessions, account = WORK)), now)
    val extra = MutableStateFlow<Map<AccountId, AgentRuntime>>(if (withWork) mapOf(WORK to work) else emptyMap())
    val hooks = FakeAgentEventSource()
    val logger = RecordingLogger()
    val api =
        sessionsModule(
            default,
            hooks,
            FakeProjectsApi(listOf(Project(P1, P1_DIR, "p1"))),
            backgroundScope,
            virtualClock(),
            logger,
            pollInterval = 2.seconds,
            maxRetryInterval = 15.seconds,
            history = history,
            extraRuntimes = extra,
        )
    return AccountsWorld(default, work, extra, hooks, logger, api)
}
