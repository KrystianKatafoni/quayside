package dev.quayside.core.domain

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.Test

class AppDispatchersTest {
    @Test
    fun `holds the injected dispatchers`() {
        val main = StandardTestDispatcher(name = "main")
        val io = StandardTestDispatcher(name = "io")
        val default = StandardTestDispatcher(name = "default")

        val dispatchers = AppDispatchers(main = main, io = io, default = default)

        dispatchers.main shouldBe main
        dispatchers.io shouldBe io
        dispatchers.default shouldBe default
    }
}
