// WITH_RUNTIME
// FULL_JDK

import java.util.concurrent.CompletableFuture
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

fun foo(): CompletableFuture<String> = CompletableFuture.supplyAsync { "foo" }
fun bar(v: String): CompletableFuture<String> = CompletableFuture.supplyAsync { "bar with $v" }
fun exception(v: String): CompletableFuture<String> = CompletableFuture.supplyAsync { throw RuntimeException(v) }

fun foobar(x: String, y: String) = x + y

fun box(): String {
    var result = ""
    fun log(x: String) {
        if (result.isNotEmpty()) result += "\n"
        result += x
    }

    val future = async<String> {
        log("start")
        val x = await(foo())
        log("got '$x'")
        val y = foobar("123 ", await(bar(x)))
        log("got '$y' after '$x'")
        y
    }

    future.whenComplete { value, t ->
        log("completed with '$value'")
    }.join()

    val expectedResult =
    """
    |start
    |got 'foo'
    |got '123 bar with foo' after 'foo'
    |completed with '123 bar with foo'""".trimMargin().trim('\n', ' ')

    if (expectedResult != result) return result

    return "OK"
}

// LIBRARY CODE

fun <T> async(c: suspend () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    c.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resume(data: T) {
            future.complete(data)
        }

        override fun resumeWithException(exception: Throwable) {
            future.completeExceptionally(exception)
        }
    })
    return future
}

suspend fun <V> await(f: CompletableFuture<V>) = suspendCoroutineOrReturn<V> { machine ->
    f.whenComplete { value, throwable ->
        if (throwable == null)
            machine.resume(value)
        else
            machine.resumeWithException(throwable)
    }
    COROUTINE_SUSPENDED
}
