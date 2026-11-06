package dev.quayside.app

import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** Which buttons the selected row enables (NFR-5, AM-609); the table is the one of the plan. */
internal class HookDialogStateTest {
    @ParameterizedTest(name = "{0} {1} busy={2} -> install={3} uninstall={4}")
    @MethodSource("cases")
    fun `buttons follow the selected row`(
        target: HookTarget,
        status: HookStatus?,
        busy: Boolean,
        install: Boolean,
        uninstall: Boolean,
    ) {
        val state = HookDialogState(rows = listOf(HookRow(target, status)), busy = busy)

        state.canInstall shouldBe install
        state.canUninstall shouldBe uninstall
    }

    @Test
    fun `buttons act on the selected row and not on the first`() {
        val state =
            HookDialogState(
                rows =
                    listOf(
                        HookRow(HookTarget.Global, HookStatus.Installed),
                        HookRow(projectTarget("alpha"), HookStatus.NotInstalled),
                    ),
                selected = 1,
            )

        state.current.target shouldBe projectTarget("alpha")
        state.canInstall shouldBe true
        state.canUninstall shouldBe false
    }

    companion object {
        private val present = projectTarget("alpha")
        private val missing = projectTarget("gone", present = false)

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                Arguments.of(present, HookStatus.NotInstalled, false, true, false),
                Arguments.of(present, HookStatus.Partial(setOf(HookKind.STOP)), false, true, true),
                Arguments.of(present, HookStatus.Installed, false, false, true),
                Arguments.of(present, HookStatus.Unreadable("json:invalid"), false, false, false),
                Arguments.of(present, null, false, false, false),
                Arguments.of(missing, null, false, false, false),
                Arguments.of(missing, HookStatus.Installed, false, false, false),
                Arguments.of(HookTarget.Global, HookStatus.Partial(setOf(HookKind.STOP)), true, false, false),
            )
    }
}
