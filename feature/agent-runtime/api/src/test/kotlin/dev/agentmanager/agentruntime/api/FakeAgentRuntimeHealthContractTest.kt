package dev.agentmanager.agentruntime.api

class FakeAgentRuntimeHealthContractTest : AgentRuntimeHealthContract() {
    override fun create(world: RuntimeWorld): AgentRuntime = FakeAgentRuntime(world)
}
