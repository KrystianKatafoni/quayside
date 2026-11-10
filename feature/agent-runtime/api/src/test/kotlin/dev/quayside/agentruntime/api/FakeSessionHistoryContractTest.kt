package dev.quayside.agentruntime.api

class FakeSessionHistoryContractTest : SessionHistoryContract() {
    override fun create(conversations: List<PastConversation>): SessionHistory = FakeSessionHistory(conversations)
}
