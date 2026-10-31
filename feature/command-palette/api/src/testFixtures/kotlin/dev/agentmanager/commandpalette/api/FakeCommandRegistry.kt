package dev.agentmanager.commandpalette.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [CommandRegistry] for tests of other features. [scope] keeps the merged list hot; in tests pass
 * `backgroundScope` and call `runCurrent()` after changes. Follows the same rules as the real registry.
 */
public class FakeCommandRegistry(
    scope: CoroutineScope,
) : CommandRegistry {
    private val providers = MutableStateFlow<List<CommandProvider>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    override val commands: StateFlow<List<Command>> =
        providers
            .flatMapLatest { list ->
                if (list.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(list.map { it.commands }) { lists -> dedupe(lists.toList().flatten()) }
                }
            }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun register(provider: CommandProvider): AutoCloseable {
        providers.update { it + provider }
        val closed = AtomicBoolean(false)
        return AutoCloseable { if (closed.compareAndSet(false, true)) providers.update { list -> list - provider } }
    }

    private fun dedupe(all: List<Command>): List<Command> {
        val ids = mutableSetOf<CommandId>()
        val shortcuts = mutableSetOf<Shortcut>()
        return all.filter { ids.add(it.id) }.map { command ->
            val shortcut = command.shortcut
            if (shortcut == null || shortcuts.add(shortcut)) command else command.copy(shortcut = null)
        }
    }
}
