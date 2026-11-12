package dev.quayside.accounts.impl

import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors

/** Base of the tests on a real file system: a temp directory and real IO threads (virtual time does not drive IO). */
open class AccountsFsTest {
    @TempDir
    lateinit var tempDir: Path

    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    val env: AccountsEnv by lazy { AccountsEnv(tempDir, io) }
}
