package dev.quayside.agentruntime.api

class FakeSessionHistoryContractTest : SessionHistoryContract() {
    override fun create(conversations: List<PastConversation>): SessionHistory = FakeSessionHistory(conversations)

    override fun createWith(
        conversation: PastConversation,
        messages: List<ConversationMessage>,
    ): SessionHistory =
        FakeSessionHistory(listOf(conversation)).also { it.putMessages(conversation.conversationId, messages) }
}
