import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.DurationUnit

open class Process(val name: String, val body: suspend Process.() -> Unit) : Comparable<Process> {
    var nextTime: Double = 0.0
        private set
    var running = true
        private set
    private lateinit var co: Continuation<Unit>
    val job: Job

    init {
        job = launch(CoroutineName(name) + object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
//                println("$name: increment")
                runningCnt.incrementAndGet()
                running = true
                if (runningCnt.get() > maxProcess) {
                    println("Too many process, exceed than $maxProcess")
                    exitProcess(-1)
                }
                Dispatchers.Default.dispatch(context) {
                    block.run()
                    running = false
                    runningCnt.decrementAndGet()
//                    println("$name: decrement")
                }
            }
        }) {
            wait()
            body()
        }
    }

    suspend fun wait(duration: Duration = Duration.ZERO) {
        suspendCoroutine<Unit> { co ->
            this.co = co
            nextTime = Time.now + duration.toDouble(DurationUnit.SECONDS)
            Time.schedule(this)
//            println("schedule")
        }
    }

    internal fun resume() {
        co.resume(Unit)
    }

    final override fun compareTo(other: Process): Int {
        return compareValues(nextTime, other.nextTime)
    }

    companion object : CoroutineScope {
        val maxProcess = System.getProperty("maxProcess")?.toInt() ?: 100000
        override val coroutineContext = Dispatchers.Default
        internal val runningCnt = AtomicInteger(0)
        internal suspend fun waitRunning() {
            while (runningCnt.get() > 0) {
                yield()
            }
        }
    }
}