package kotlinx.coroutines.intrinsics

import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Use this function to start coroutine in a cancellable way, so that it can be cancelled
 * while waiting to be dispatched.
 *
 * @suppress **This is internal API and it is subject to change.**
 */
@InternalCoroutinesApi
public fun <T> (suspend () -> T).startCoroutineCancellable(completion: Continuation<T>): Unit = runSafely(completion) {
    createCoroutineUnintercepted(completion).intercepted().resumeCancellableWith(Result.success(Unit))
}

/**
 * Use this function to start coroutine in a cancellable way, so that it can be cancelled
 * while waiting to be dispatched.
 */
internal fun <R, T> (suspend (R) -> T).startCoroutineCancellable(
    receiver: R, completion: Continuation<T>,
) = runSafely(completion) {
    createCoroutineUnintercepted(receiver, completion).intercepted().resumeCancellableWith(Result.success(Unit))
}

/**
 * Similar to [startCoroutineCancellable], but for already created coroutine.
 * [fatalCompletion] is used only when interception machinery throws an exception
 */
internal fun Continuation<Unit>.startCoroutineCancellable(fatalCompletion: Continuation<*>) =
    runSafely(fatalCompletion) {
        intercepted().resumeCancellableWith(Result.success(Unit))
    }

/**
 * Runs given block and completes completion with its exception if it occurs.
 * Rationale: [startCoroutineCancellable] is invoked when we are about to run coroutine asynchronously in its own dispatcher.
 * Thus if dispatcher throws an exception during coroutine start, coroutine never completes, so we should treat dispatcher exception
 * as its cause and resume completion.
 */
private inline fun runSafely(completion: Continuation<*>, block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        dispatcherFailure(completion, e)
    }
}

private fun dispatcherFailure(completion: Continuation<*>, e: Throwable) {
    /*
     * This method is invoked when we failed to start a coroutine due to the throwing
     * dispatcher implementation or missing Dispatchers.Main.
     * This situation is not recoverable, so we are trying to deliver the exception by all means:
     * 1) Resume the coroutine with an exception, so it won't prevent its parent from completion
     * 2) Rethrow the exception immediately, so it will crash the caller (e.g. when the coroutine had
     *    no parent or it was async/produce over MainScope).
     */
    val reportException = if (e is DispatchException) e.cause else e
    completion.resumeWith(Result.failure(reportException))
    throw reportException
}
