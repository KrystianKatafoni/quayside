package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.agentruntime.api.SessionHistoryContract
import dev.quayside.core.domain.NoOpLogger
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** [SessionHistoryContract] on the file-based implementation over session files in a temporary directory. */
@Suppress("InjectDispatcher")
class FileSessionHistoryContractTest : SessionHistoryContract() {
    @TempDir
    lateinit var dir: Path

    override fun create(conversations: List<PastConversation>): SessionHistory {
        conversations.forEach { writeSessionFile(dir, it) }
        return sessionHistoryModule(dir, Dispatchers.IO, NoOpLogger)
    }
}
