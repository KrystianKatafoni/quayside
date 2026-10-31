package dev.quayside.agentruntime.api

class FakeAgentRuntimeHealthContractTest : AgentRuntimeHealthContract() {
    override fun create(world: RuntimeWorld): AgentRuntime = FakeAgentRuntime(world)
}
