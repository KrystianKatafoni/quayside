package dev.agentmanager.commandpalette.ui

import dev.agentmanager.commandpalette.api.CommandGroup
import dev.agentmanager.commandpalette.api.aCommand
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The pure transitions of the palette (AM-408 plan, table "PaletteState.reduce"). */
internal class PaletteStateTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("transitions")
    fun `reduce follows the plan`(
        @Suppress("UNUSED_PARAMETER") name: String,
        before: PaletteState,
        event: PaletteEvent,
        resultCount: Int,
        after: PaletteState,
    ) {
        before.reduce(event, resultCount) shouldBe after
    }

    @Test
    fun `view clamps the highlight to the last result`() {
        val state = PaletteState(open = true, query = "", group = null, highlighted = 2)

        val view = state.view(listOf(aCommand(id = "a", title = "Alpha")))

        view.highlighted shouldBe 0
        view.results.map { it.id.value } shouldBe listOf("a")
    }

    @Test
    fun `view filters by the query ignoring case and Polish letters`() {
        val state = PaletteState(open = true, query = "USUN", group = null, highlighted = 0)

        val view =
            state.view(
                listOf(aCommand(id = "add", title = "Dodaj projekt"), aCommand(id = "remove", title = "Usuń projekt")),
            )

        view.results.map { it.id.value } shouldBe listOf("remove")
        view.query shouldBe "USUN"
        view.open shouldBe true
    }

    @Test
    fun `view limits results to the group`() {
        val state = PaletteState(open = true, query = "", group = CommandGroup.GO_TO_PROJECT, highlighted = 0)

        val view =
            state.view(
                listOf(
                    aCommand(id = "add", group = CommandGroup.PROJECT),
                    aCommand(id = "goto", group = CommandGroup.GO_TO_PROJECT),
                ),
            )

        view.results.map { it.id.value } shouldBe listOf("goto")
    }

    @Test
    fun `view of a closed palette is closed`() {
        PaletteState().view(listOf(aCommand())).open shouldBe false
    }

    companion object {
        private val open = PaletteState(open = true, query = "", group = null, highlighted = 0)

        @JvmStatic
        @Suppress("LongMethod")
        fun transitions(): List<Arguments> =
            listOf(
                arguments("MoveDown from 0", open, PaletteEvent.MoveDown, 3, open.copy(highlighted = 1)),
                arguments(
                    "MoveDown at the last result does not wrap",
                    open.copy(highlighted = 2),
                    PaletteEvent.MoveDown,
                    3,
                    open.copy(highlighted = 2),
                ),
                arguments("MoveUp at the first result stays", open, PaletteEvent.MoveUp, 3, open),
                arguments(
                    "MoveUp from 2",
                    open.copy(highlighted = 2),
                    PaletteEvent.MoveUp,
                    3,
                    open.copy(highlighted = 1),
                ),
                arguments(
                    "QueryChanged resets the highlight",
                    open.copy(highlighted = 2),
                    PaletteEvent.QueryChanged("x"),
                    3,
                    open.copy(query = "x", highlighted = 0),
                ),
                arguments(
                    "Dismiss closes and clears query and group",
                    open.copy(query = "ab", group = CommandGroup.PROJECT, highlighted = 1),
                    PaletteEvent.Dismiss,
                    3,
                    PaletteState(open = false, query = "", group = null, highlighted = 0),
                ),
                arguments("MoveDown without results stays", open, PaletteEvent.MoveDown, 0, open),
            )
    }
}
