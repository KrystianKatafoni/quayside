package dev.agentmanager.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.impl.agentRuntimeModule
import dev.agentmanager.commandpalette.impl.commandPaletteModule
import dev.agentmanager.core.domain.info
import dev.agentmanager.core.process.LoginShellProcessRunner
import dev.agentmanager.projects.impl.projectInspectorModule
import dev.agentmanager.projects.impl.projectsModule
import dev.agentmanager.sessions.impl.sessionsModule
import dev.agentmanager.terminal.api.TerminalApi
import dev.agentmanager.terminal.impl.terminalModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * Entry point: builds the dependency graph once, then opens the main window. The project list is read before
 * the window opens: `main` is the one place allowed to block (ENG-C3).
 */
@Suppress("ForbiddenMethodCall")
fun main() {
    // Before the first use of AWT: FileDialog then chooses folders, natively on macOS (AwtDirectoryPicker).
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    val core = productionCoreDeps()
    core.logger.info("app start")
    val runtime = agentRuntimeModule(core.process, core.clock, core.logger, core.homeDir)
    val projects = runBlocking { projectsModule(core.storage, core.dispatchers.io, core.logger) }
    val inspector = projectInspectorModule(projects, runtime, core.process, core.dispatchers.io, core.logger)
    // Lives as long as the app, not the window (AM-405 risks).
    val sessionsScope = CoroutineScope(SupervisorJob() + core.dispatchers.io)
    val sessions = sessionsModule(runtime, projects, sessionsScope, core.clock, core.logger)
    // The registry only merges lists on Default; commands run in the window scope on Main (see MainWindow).
    val commandsScope = CoroutineScope(SupervisorJob() + core.dispatchers.default)
    val registry = commandPaletteModule(commandsScope, core.logger)
    val shell = LoginShellProcessRunner.defaultShell()
    val terminal = startTerminals(core, shell)
    val modules = AppModules(projects, sessions, inspector, registry, terminalDeps(terminal, shell, runtime))
    val keys = WindowKeys()
    // Before the first application {}: Compose content (the palette) must draw over the Swing terminals (ADR 0012).
    System.setProperty("compose.interop.blending", "true")
    application {
        Window(
            onCloseRequest = {
                terminal.close()
                exitApplication()
            },
            onKeyEvent = keys::onKeyEvent,
            title = Strings["app.title"],
            state = rememberWindowState(size = DpSize(width = 1280.dp, height = 800.dp)),
        ) {
            MainWindow(core, modules, keys)
        }
    }
}

private fun terminalDeps(
    terminal: TerminalApi,
    shell: Path,
    runtime: AgentRuntime,
): TerminalDeps = TerminalDeps(terminal, shell.fileName.toString(), runtime::attachCommand)

/**
 * The terminal module on [shell]. Its scope lives as long as the app (it disposes widgets on Main). Cmd-Q bypasses
 * `onCloseRequest`, so a shutdown hook also kills every shell tree.
 */
private fun startTerminals(
    core: CoreDeps,
    shell: Path,
): TerminalApi {
    val terminal =
        terminalModule(core.dispatchers, CoroutineScope(SupervisorJob() + core.dispatchers.default), core.logger, shell)
    Runtime.getRuntime().addShutdownHook(Thread { terminal.close() })
    return terminal
}
