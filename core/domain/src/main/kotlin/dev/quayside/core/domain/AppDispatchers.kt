package dev.quayside.core.domain

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatchers injected into modules (ENG-C6) so tests can run on virtual time.
 * The production instance is built in `app`, where the Swing main dispatcher is available.
 */
public data class AppDispatchers(
    val main: CoroutineDispatcher,
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
)
