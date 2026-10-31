package dev.agentmanager.agentruntime.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [AgentEventSource] driven by the test. Like the real hook server it has no replay: an event
 * emitted while nobody collects is lost.
 */
public class FakeAgentEventSource : AgentEventSource {
    private val flow = MutableSharedFlow<AgentEvent>()

    override val events: Flow<AgentEvent> = flow.asSharedFlow()

    /** Delivers [event] to every current collector, suspending until each has taken it. */
    public suspend fun emit(event: AgentEvent) {
        flow.emit(event)
    }
}
