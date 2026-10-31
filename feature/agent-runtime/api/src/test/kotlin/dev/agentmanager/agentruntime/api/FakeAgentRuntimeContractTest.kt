package dev.agentmanager.agentruntime.api

class FakeAgentRuntimeContractTest : AgentRuntimeContract() {
    override fun create(world: RuntimeWorld): AgentRuntime = FakeAgentRuntime(world)
}
