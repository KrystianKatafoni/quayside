package dev.quayside.sessions.api

class FakeSessionHistorySettingsContractTest : SessionHistorySettingsContract() {
    override suspend fun create(): SessionHistorySettingsRepo = FakeSessionHistorySettings()
}
