package dev.agentmanager.core.testing

import dev.agentmanager.core.domain.AppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope

/**
 * [AppDispatchers] where main, io and default share one [TestCoroutineScheduler],
 * so `runTest` virtual time and `advanceUntilIdle()` cover work on every dispatcher.
 */
public fun testDispatchers(scheduler: TestCoroutineScheduler): AppDispatchers {
    val dispatcher = StandardTestDispatcher(scheduler)
    return AppDispatchers(main = dispatcher, io = dispatcher, default = dispatcher)
}

/** Shortcut inside `runTest { }`: dispatchers bound to this test's scheduler. */
public fun TestScope.testDispatchers(): AppDispatchers = testDispatchers(testScheduler)
