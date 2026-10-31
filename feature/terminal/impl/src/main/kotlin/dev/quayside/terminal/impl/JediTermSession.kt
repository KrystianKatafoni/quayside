package dev.quayside.terminal.impl

import dev.quayside.core.domain.AppDispatchers
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.catching
import dev.quayside.core.domain.info
import dev.quayside.terminal.api.TerminalError
import dev.quayside.terminal.api.TerminalId
import dev.quayside.terminal.api.TerminalSearch
import dev.quayside.terminal.api.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

/** What every session shares: dispatchers, the app-long [scope] that disposes widgets, and the [logger]. */
internal class SessionContext(
    val dispatchers: AppDispatchers,
    val scope: CoroutineScope,
    val logger: Logger,
)

/** A pty4j process shown in a searchable JediTerm [widget]; built on the Swing thread by [JediTermTerminalApi]. */
internal class JediTermSession(
    override val id: TerminalId,
    private val connector: PtyTtyConnector,
    private val widget: SearchableWidget,
    private val context: SessionContext,
    private val onClosed: (TerminalId) -> Unit,
) : TerminalSession {
    private val exit = MutableStateFlow<Int?>(null)
    private val closed = AtomicBoolean(false)

    override val exitCode: StateFlow<Int?> = exit

    override val search: TerminalSearch = JediTermSearch(widget, context.dispatchers) { closed.get() }

    init {
        connector.process.onExit().thenAccept { process ->
            exit.value = process.exitValue()
            context.logger.info("terminal exited", "terminalId" to id.value, "exitCode" to process.exitValue())
        }
    }

    override fun component(): JComponent = widget

    override suspend fun send(text: String): Outcome<Unit, TerminalError> =
        if (closed.get() || exit.value != null) {
            Outcome.Err(TerminalError.Closed)
        } else {
            withContext(context.dispatchers.io) {
                catching(onException = { TerminalError.Closed }) { connector.write(text) }
            }
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        connector.close()
        onClosed(id)
        context.scope.launch(context.dispatchers.main) { widget.close() }
    }
}
