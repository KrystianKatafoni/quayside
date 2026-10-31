package dev.quayside.commandpalette.impl

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandProvider
import dev.quayside.commandpalette.api.CommandRegistry
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.warn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Registry kept in memory; the merged list is recomputed whenever a provider is added, removed or changes.
 * A conflict is logged once while it persists, not on every emission.
 */
internal class InMemoryCommandRegistry(
    scope: CoroutineScope,
    private val logger: Logger,
) : CommandRegistry {
    private val lastIssues = AtomicReference<List<MergeIssue>>(emptyList())
    private val providers = MutableStateFlow<List<CommandProvider>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    override val commands: StateFlow<List<Command>> =
        providers
            .flatMapLatest { list -> merged(list) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun register(provider: CommandProvider): AutoCloseable {
        providers.update { it + provider }
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) providers.update { list -> list - provider }
        }
    }

    private fun merged(list: List<CommandProvider>): Flow<List<Command>> =
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(list.map { it.commands }) { lists -> mergeCommands(lists.toList().flatten()) }
                .map { report(it) }
        }

    private fun report(merged: Merged): List<Command> {
        val fresh = merged.issues - lastIssues.getAndSet(merged.issues).toSet()
        fresh.forEach {
            logger.warn(
                "command conflict",
                "reason" to it.reason,
                "commandId" to it.commandId.value,
            )
        }
        return merged.commands
    }
}
