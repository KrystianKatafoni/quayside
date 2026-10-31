package dev.quayside.agentruntime.api

class FakeAgentEventSourceContractTest : AgentEventSourceContract() {
    private val source = FakeAgentEventSource()

    override fun create(): AgentEventSource = source

    override suspend fun emit(event: AgentEvent) {
        source.emit(event)
    }
}
