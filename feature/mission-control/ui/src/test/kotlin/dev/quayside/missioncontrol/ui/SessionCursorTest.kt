package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The pure rule of j and k over the visible rows (AM-409 plan, table 3). `null` = no change. */
internal class SessionCursorTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `next and previous move over the rows without wrapping`(
        @Suppress("UNUSED_PARAMETER") name: String,
        rows: List<String>,
        selected: String?,
        next: String?,
        previous: String?,
    ) {
        val ids = rows.map(::SessionId)
        val current = selected?.let(::SessionId)

        SessionCursor.next(ids, current) shouldBe next?.let(::SessionId)
        SessionCursor.previous(ids, current) shouldBe previous?.let(::SessionId)
    }

    companion object {
        private val abc = listOf("a", "b", "c")

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("no rows", emptyList<String>(), null, null, null),
                arguments("no rows, stale selection", emptyList<String>(), "x", null, null),
                arguments("no selection", abc, null, "a", "c"),
                arguments("hidden selection", abc, "x", "a", "c"),
                arguments("first selected", abc, "a", "b", "a"),
                arguments("middle selected", abc, "b", "c", "a"),
                arguments("last selected", abc, "c", "c", "b"),
                arguments("single row selected", listOf("a"), "a", "a", "a"),
                arguments("single row, no selection", listOf("a"), null, "a", "a"),
            )
    }
}
