package dev.quayside.app

import dev.quayside.core.domain.SessionId
import dev.quayside.terminal.ui.TabKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The session behind the key of an attach tab, for the session bar of the terminal mode (plan AM-809). */
internal class AttachTabKeyTest {
    @Test
    fun `attach tab key gives its session back`() {
        sessionOfTabKey(attachTabKey(SessionId("s1"))) shouldBe SessionId("s1")
        sessionOfTabKey(attachTabKey(SessionId("other-7"))) shouldBe SessionId("other-7")
    }

    @Test
    fun `a key that is not an attach key gives no session`() {
        sessionOfTabKey(TabKey("shell:s1")).shouldBeNull()
        sessionOfTabKey(TabKey("attach:")).shouldBeNull()
    }
}
