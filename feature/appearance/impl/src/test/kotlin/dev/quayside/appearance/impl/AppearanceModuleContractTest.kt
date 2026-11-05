@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceApiContract
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.InMemoryJsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.AfterEach

/**
 * The API over an in-memory store passes the shared contract (ENG-T2). The mode comes from a flow the test sets; the
 * scope runs at once, so `applied` is current right after each change.
 */
class AppearanceModuleContractTest : AppearanceApiContract() {
    private val systemDark = MutableStateFlow<Boolean?>(null)
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    @AfterEach
    fun stop() = scope.cancel()

    // The contract's factory is not suspending; runBlocking is allowed in tests (ENG-C3).
    @Suppress("ForbiddenMethodCall")
    override fun api(): AppearanceApi =
        runBlocking {
            JsonThemeStore.open(
                InMemoryJsonStore(AppearanceSettings()),
                builtInCatalog(),
                systemDark,
                scope,
                NoOpLogger,
            )
        }

    override fun setSystemDark(
        api: AppearanceApi,
        dark: Boolean?,
    ) {
        systemDark.value = dark
    }
}
