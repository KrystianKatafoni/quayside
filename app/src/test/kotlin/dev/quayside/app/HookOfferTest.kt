package dev.quayside.app

import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** When the start offers the hooks dialog (NFR-5, AM-607): only for missing hooks and only if not dismissed. */
internal class HookOfferTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `offer follows the status and the stored choice`(
        @Suppress("UNUSED_PARAMETER") name: String,
        status: HookStatus,
        dismissed: Boolean,
        expected: Boolean,
    ) {
        HookOffer.shouldOffer(status, dismissed) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                arguments("not installed", HookStatus.NotInstalled, false, true),
                arguments("partial", HookStatus.Partial(setOf(HookKind.STOP)), false, true),
                arguments("installed", HookStatus.Installed, false, false),
                arguments("unreadable", HookStatus.Unreadable("x"), false, false),
                arguments("not installed, dismissed", HookStatus.NotInstalled, true, false),
                arguments("partial, dismissed", HookStatus.Partial(setOf(HookKind.STOP)), true, false),
                arguments("installed, dismissed", HookStatus.Installed, true, false),
            )
    }
}
