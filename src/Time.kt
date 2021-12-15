import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

object Time {
    private val time = AtomicReference(0.0) //seconds
    val now get() = time.get()!!
    val nowH get() = now.seconds

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

    fun deSchedule(process: Process) = synchronized(queue) {
        queue.remove(process)
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

    fun run(time: Duration) = run(time.toDouble(DurationUnit.SECONDS))

    var parallelStrategy = ParallelStrategy.NoParallel

    sealed interface ParallelStrategy {
        fun onTick()

        object NoParallel : ParallelStrategy {
            override fun onTick() = Unit
        }

        /**
         * Allow parallel if the interval less than [eps]
         * @param eps max interval to parallel
         * */
        @Suppress("MemberVisibilityCanBePrivate", "unused")
        class WithTimeWrong(val eps: Double) : ParallelStrategy {
            override fun onTick() {
                while ((queue.peek()?.nextTime ?: Double.MAX_VALUE) < now + eps) {
                    queue.poll().resume()
                }
            }
        }
    }
}