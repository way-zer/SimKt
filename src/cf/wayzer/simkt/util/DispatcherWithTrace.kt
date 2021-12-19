package cf.wayzer.simkt.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class DispatcherWithTrace(
    val raw: CoroutineDispatcher, var maxCoroutine: Int,
    val exceedCallback: (context: CoroutineContext, block: Runnable) -> Unit
) : CoroutineDispatcher() {
    private val cnt = AtomicInteger(0)
    val count get() = cnt.get()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        cnt.incrementAndGet()
        if (cnt.get() > maxCoroutine)
            exceedCallback(context, block)
        raw.dispatch(context) {
            block.run()
            cnt.decrementAndGet()
        }
    }

    suspend fun waitRunning() {
        withContext(raw) {
            while (cnt.get() > 0) yield()
        }
    }
}