@file:OptIn(ExperimentalCoroutinesApi::class)
@file:Suppress("MatchingDeclarationName") // PaneSetup plus the functions that wire it into the window

package dev.quayside.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.JsonStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.nio.file.Path

/**
 * What the window of [WindowSetup] needs for the resizable terminal pane (AM-819), kept out of `WindowWorld.kt`:
 * the layout [store] (null = no pane height, the fixed default), the layout [initial] that `main` would have read,
 * and the height of the container the window is drawn in ([windowHeight], null = the whole test window), which a
 * test changes to resize the window.
 */
internal class PaneSetup {
    var store: WindowLayoutStore? = null
    var initial: WindowLayout = WindowLayout()
    val windowHeight: MutableState<Dp?> = mutableStateOf(null)

    /** A store on [dir] with file IO inline; the file is `window.json` there. */
    fun storeOn(dir: Path) {
        store = layoutStoreOn(dir)
    }
}

/** The layout store on [dir] (a temp directory of the test); file IO runs inline on the test thread. */
internal fun layoutStoreOn(dir: Path): WindowLayoutStore =
    WindowLayoutStore(JsonStorage(dir, UnconfinedTestDispatcher()), NoOpLogger)

/** The terminal height of the window: [TerminalPaneHeight] over the store of [setup], null without a store. */
@Composable
internal fun rememberPane(
    setup: PaneSetup,
    scope: CoroutineScope,
): TerminalPaneHeight? =
    remember {
        setup.store?.let { TerminalPaneHeight(setup.initial.terminalHeight.dp, it, scope, NoOpLogger) }
    }

/** [content] in a container as high as [PaneSetup.windowHeight] says, or in the whole test window. */
@Composable
internal fun WindowFrame(
    setup: PaneSetup,
    content: @Composable () -> Unit,
) {
    val height by setup.windowHeight
    Box(height?.let { Modifier.fillMaxWidth().height(it) } ?: Modifier.fillMaxSize()) { content() }
}
