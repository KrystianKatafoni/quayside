package dev.quayside.core.domain

import kotlin.coroutines.cancellation.CancellationException

/**
 * Result of an operation that crosses a module boundary (ADR 0005).
 * Errors are typed values, usually a `sealed interface` owned by the module's `api`.
 */
public sealed interface Outcome<out T, out E> {
    public data class Ok<out T>(
        val value: T,
    ) : Outcome<T, Nothing>

    public data class Err<out E>(
        val error: E,
    ) : Outcome<Nothing, E>
}

public val Outcome<*, *>.isOk: Boolean get() = this is Outcome.Ok

public fun <T> Outcome<T, *>.getOrNull(): T? = (this as? Outcome.Ok)?.value

public fun <E> Outcome<*, E>.errorOrNull(): E? = (this as? Outcome.Err)?.error

public inline fun <T, E> Outcome<T, E>.getOrElse(onError: (E) -> T): T =
    when (this) {
        is Outcome.Ok -> value
        is Outcome.Err -> onError(error)
    }

public inline fun <T, E, R> Outcome<T, E>.fold(
    onOk: (T) -> R,
    onErr: (E) -> R,
): R =
    when (this) {
        is Outcome.Ok -> onOk(value)
        is Outcome.Err -> onErr(error)
    }

public inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(transform(value))
        is Outcome.Err -> this
    }

public inline fun <T, E, F> Outcome<T, E>.mapError(transform: (E) -> F): Outcome<T, F> =
    when (this) {
        is Outcome.Ok -> this
        is Outcome.Err -> Outcome.Err(transform(error))
    }

public inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> transform(value)
        is Outcome.Err -> this
    }

/**
 * For adapters only: runs [block] and maps any [Exception] to a domain error.
 * [CancellationException] is rethrown so structured concurrency keeps working.
 * Catching every [Exception] is intended: this is the one place adapters turn them into errors.
 */
@Suppress("TooGenericExceptionCaught")
public inline fun <T, E> catching(
    onException: (Exception) -> E,
    block: () -> T,
): Outcome<T, E> =
    try {
        Outcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Err(onException(e))
    }
