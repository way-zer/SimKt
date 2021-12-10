import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.atomic.AtomicReference

object Time {
    private val time = AtomicReference(0.0) //seconds
    val now get() = time.get()!!

//    fun reset() {
//        queue.forEach { it.second.resumeWithException(CancellationException()) }
//        queue.clear()
//        time.reset()
//    }

    private val queue = PriorityQueue<Process>()
    fun schedule(process: Process) {
        synchronized(queue) {
            queue.add(process)
        }
    }

    fun run(seconds: Double) {
        runBlocking(Dispatchers.Default) {
            val end = (now + seconds).coerceAtMost(Double.MAX_VALUE)
            while (true) {
                Process.waitRunning()
                if ((queue.peek()?.nextTime ?: Double.POSITIVE_INFINITY) > end) break
                synchronized(queue) { queue.poll() }?.let { next ->
                    time.set(next.nextTime)
                    next.resume()
                }
                parallelStrategy.onTick()
            }
            time.set(end)
        }
    }

    var parallelStrategy = ParallelStrategy.NoParallel

    sealed interface ParallelStrategy {
        fun onTick()

        object NoParallel : ParallelStrategy {
            override fun onTick() = Unit
        }

        /**时间差小于[eps]的事件并行进行*/
        class WithTimeWrong(val eps: Double) : ParallelStrategy {
            override fun onTick() {
                while ((queue.peek()?.nextTime ?: Double.MAX_VALUE) < now + eps) {
                    queue.poll().resume()
                }
            }
        }
    }
}