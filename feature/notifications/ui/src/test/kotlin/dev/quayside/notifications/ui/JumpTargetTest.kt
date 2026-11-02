package dev.quayside.notifications.ui

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** `jumpTarget` (plan AM-704, part B2b): pure; the letter jump of the voice field. */
class JumpTargetTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `jump target is the next name with the letter, wrapping, ignoring case`(
        case: String,
        names: List<String?>,
        from: Int,
        letter: Char,
        expected: Int?,
    ) {
        withClue(case) { jumpTarget(names, from, letter) shouldBe expected }
    }

    companion object {
        private val names = listOf(null, "Alex", "bob", "Daniel", "Zosia")

        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                arguments("lower-case name matches an upper-case letter", names, 0, 'B', 2),
                arguments("the next match after the current one", names, 1, 'D', 3),
                arguments("wraps round the end of the list", names, 4, 'A', 1),
                arguments("from the last element to an earlier one", names, 4, 'B', 2),
                arguments("the current element is the only match: it is found last, after the wrap", names, 1, 'A', 1),
                arguments("a letter with no name", names, 0, 'Q', null),
                arguments("the system voice (null name) never matches", listOf(null, "Alex"), 1, 'A', 1),
                arguments("empty list", emptyList<String?>(), 0, 'A', null),
            )
    }
}
