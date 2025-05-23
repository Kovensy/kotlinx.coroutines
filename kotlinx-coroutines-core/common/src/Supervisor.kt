@file:OptIn(ExperimentalContracts::class)
@file:Suppress("LEAKED_IN_PLACE_LAMBDA", "WRONG_INVOCATION_KIND")

package kotlinx.coroutines

import kotlinx.coroutines.internal.*
import kotlinx.coroutines.intrinsics.*
import kotlin.contracts.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.jvm.*

/**
 * Creates a _supervisor_ job object in an active state.
 * Children of a supervisor job can fail independently of each other.
 * 
 * A failure or cancellation of a child does not cause the supervisor job to fail and does not affect its other children,
 * so a supervisor can implement a custom policy for handling failures of its children:
 *
 * - A failure of a child job that was created using [launch][CoroutineScope.launch] can be handled via [CoroutineExceptionHandler] in the context.
 * - A failure of a child job that was created using [async][CoroutineScope.async] can be handled via [Deferred.await] on the resulting deferred value.
 *
 * If a [parent] job is specified, then this supervisor job becomes a child job of the [parent] and is cancelled when the
 * parent fails or is cancelled. All this supervisor's children are cancelled in this case, too.
 */
@Suppress("FunctionName")
public fun SupervisorJob(parent: Job? = null) : CompletableJob = SupervisorJobImpl(parent)

/** @suppress Binary compatibility only */
@Suppress("FunctionName")
@Deprecated(level = DeprecationLevel.HIDDEN, message = "Since 1.2.0, binary compatibility with versions <= 1.1.x")
@JvmName("SupervisorJob")
public fun SupervisorJob0(parent: Job? = null) : Job = SupervisorJob(parent)

/**
 * Creates a [CoroutineScope] with [SupervisorJob] and calls the specified suspend [block] with this scope.
 * The provided scope inherits its [coroutineContext][CoroutineScope.coroutineContext] from the outer scope, using the
 * [Job] from that context as the parent for the new [SupervisorJob].
 * This function returns as soon as the given block and all its child coroutines are completed.
 *
 * Unlike [coroutineScope], a failure of a child does not cause this scope to fail and does not affect its other children,
 * so a custom policy for handling failures of its children can be implemented. See [SupervisorJob] for additional details.
 *
 * If an exception happened in [block], then the supervisor job is failed and all its children are cancelled.
 * If the current coroutine was cancelled, then both the supervisor job itself and all its children are cancelled.
 *
 * The method may throw a [CancellationException] if the current job was cancelled externally,
 * or rethrow an exception thrown by the given [block].
 */
public suspend fun <R> supervisorScope(block: suspend CoroutineScope.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return suspendCoroutineUninterceptedOrReturn { uCont ->
        val coroutine = SupervisorCoroutine(uCont.context, uCont)
        coroutine.startUndispatchedOrReturn(coroutine, block)
    }
}

private class SupervisorJobImpl(parent: Job?) : JobImpl(parent) {
    override fun childCancelled(cause: Throwable): Boolean = false
}

private class SupervisorCoroutine<in T>(
    context: CoroutineContext,
    uCont: Continuation<T>
) : ScopeCoroutine<T>(context, uCont) {
    override fun childCancelled(cause: Throwable): Boolean = false
}
