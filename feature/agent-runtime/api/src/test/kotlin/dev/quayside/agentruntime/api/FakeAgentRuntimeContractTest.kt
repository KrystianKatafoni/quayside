package dev.quayside.agentruntime.api

class FakeAgentRuntimeContractTest : AgentRuntimeContract() {
    override fun create(world: RuntimeWorld): AgentRuntime = FakeAgentRuntime(world)
}
