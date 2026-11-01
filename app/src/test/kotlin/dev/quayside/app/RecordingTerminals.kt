package dev.quayside.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.terminal.api.FakeTerminalApi
import dev.quayside.terminal.api.TerminalApi
import dev.quayside.terminal.api.TerminalError
import dev.quayside.terminal.api.TerminalSession
import dev.quayside.terminal.api.TerminalSettings
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

/**
 * [FakeTerminalApi] that remembers the directories (and the [settings]) shells were started with; the widgets are
 * drawn as markers.
 */
internal class RecordingTerminals(
    val fake: FakeTerminalApi = FakeTerminalApi(),
    private val settings: StateFlow<TerminalSettings>? = null,
) : TerminalApi {
    override val bells get() = fake.bells

    private val dirs = CopyOnWriteArrayList<Path>()
    private val opened = CopyOnWriteArrayList<TerminalSettings>()

    /** The [settings] read at every shell opened so far, as `JediTermTerminalApi` reads them; empty without them. */
    val settingsAtOpen: List<TerminalSettings> get() = opened.toList()

    /** Directories of every shell opened so far, in order. */
    val shellDirs: List<Path> get() = dirs.toList()

    override suspend fun openShell(dir: Path): Outcome<TerminalSession, TerminalError> {
        dirs.add(dir)
        settings?.let { opened.add(it.value) }
        return fake.openShell(dir)
    }

    override suspend fun openCommand(command: ShellCommand): Outcome<TerminalSession, TerminalError> =
        fake.openCommand(command)

    override fun close() = fake.close()

    /** What a pane draws in tests instead of the Swing widget. */
    val paneContent: @Composable (JComponent) -> Unit = { component ->
        val id =
            fake.sessions
                .first { it.component() === component }
                .id.value
        Box(Modifier.testTag(WindowTags.terminalContent(id)))
    }
}
