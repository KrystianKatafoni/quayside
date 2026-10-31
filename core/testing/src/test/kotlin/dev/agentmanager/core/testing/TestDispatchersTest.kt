@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.core.testing

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class TestDispatchersTest {
    @Test
    fun `all dispatchers run on the test's virtual time`() =
        runTest {
            val dispatchers = testDispatchers()
            val done = mutableListOf<String>()

            launch(dispatchers.io) {
                delay(10.seconds)
                done += "io"
            }
            launch(dispatchers.default) {
                delay(20.seconds)
                done += "default"
            }
            launch(dispatchers.main) { done += "main" }

            runCurrent()
            done shouldBe listOf("main")

            advanceTimeBy(15.seconds)
            done shouldBe listOf("main", "io")

            advanceTimeBy(10.seconds)
            done shouldBe listOf("main", "io", "default")
        }
}
