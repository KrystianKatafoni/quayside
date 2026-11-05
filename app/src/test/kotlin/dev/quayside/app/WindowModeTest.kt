package dev.quayside.app

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The mode of the window after each event (plan AM-809, table 4). */
internal class WindowModeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    fun `mode after an event`(
        @Suppress("UNUSED_PARAMETER") name: String,
        from: WindowMode,
        event: WindowModeEvent,
        hasTabs: Boolean,
        expected: WindowMode,
    ) {
        from.after(event, hasTabs) shouldBe expected
    }

    @Test
    fun `state follows the events`() {
        var tabs = true
        val state = WindowModeState { tabs }
        state.mode.value shouldBe WindowMode.LIST

        state.onEvent(WindowModeEvent.FullScreen)
        state.mode.value shouldBe WindowMode.TERMINAL

        tabs = false
        state.onEvent(WindowModeEvent.TabsGone)
        state.mode.value shouldBe WindowMode.LIST

        // without tabs the full screen request changes nothing
        state.onEvent(WindowModeEvent.FullScreen)
        state.mode.value shouldBe WindowMode.LIST
    }

    companion object {
        @JvmStatic
        fun table(): List<Arguments> {
            val list = WindowMode.LIST
            val terminal = WindowMode.TERMINAL
            val leaving = listOf(WindowModeEvent.Back, WindowModeEvent.ProjectPicked, WindowModeEvent.TabsGone)
            return listOf(
                arguments("list, full screen with tabs", list, WindowModeEvent.FullScreen, true, terminal),
                arguments("list, full screen without tabs", list, WindowModeEvent.FullScreen, false, list),
                arguments("terminal, full screen with tabs", terminal, WindowModeEvent.FullScreen, true, terminal),
                arguments("terminal, full screen without tabs", terminal, WindowModeEvent.FullScreen, false, terminal),
            ) +
                leaving.flatMap { event ->
                    listOf(true, false).flatMap { tabs ->
                        listOf(
                            arguments("list, $event, tabs=$tabs", list, event, tabs, list),
                            arguments("terminal, $event, tabs=$tabs", terminal, event, tabs, list),
                        )
                    }
                }
        }
    }
}
