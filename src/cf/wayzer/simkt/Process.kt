package cf.wayzer.simkt

import kotlinx.coroutines.*
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.SelectClause0
import kotlinx.coroutines.selects.SelectInstance
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.DurationUnit

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class Process(val name: String, body: suspend Process.() -> Unit) : Comparable<Process>, CoroutineScope {
    override val coroutineContext = CoroutineName(name) + Job() + object : CoroutineDispatcher() {
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
    }

    var nextTime: Double = 0.0
        private set
    var running = true
        private set
    private lateinit var co: Continuation<Unit>
    val job: Job

    init {
        job = launch {
            wait()
            body()
        }
        job.invokeOnCompletion {
            Time.deSchedule(this)
            (coroutineContext[Job] as CompletableJob).complete()
        }
    }

    suspend fun wait(duration: Duration = Duration.ZERO) {
        suspendCoroutine<Unit> { co ->
            this.co = co
            nextTime = Time.now + duration.toDouble(DurationUnit.SECONDS)
            Time.schedule(this)
        }
    }

    data class JobWithPriority(val job: Job, val priority: Int, val lastCo: Continuation<Unit>) : Comparable<JobWithPriority> {
        override fun compareTo(other: JobWithPriority): Int {
            return compareValues(priority, other.priority)
        }
    }

    private val stack = sortedSetOf<JobWithPriority>()// priority to job
    private val stackLock = Mutex()

    /**@return true means Not in waiting, can't intercept */
    suspend fun intercept(priority: Int, body: suspend Process.() -> Unit) {
        while (true) {
            stackLock.lock()
            val job = this.stack.lastOrNull()?.takeIf { it.priority >= priority } ?: break
            stackLock.unlock()
            job.job.join()
        }
        lateinit var item: JobWithPriority
        val job = launch {
            if (Time.deSchedule(this@Process).not()) yield()
            val leftTime = nextTime - Time.now
            this@Process.body()
            stackLock.withLock {
                stack.remove(item)
            }
            //finish intercept job, try resume wait
            co = item.lastCo
            if (Time.deSchedule(this@Process).not()) yield()
            nextTime = Time.now + leftTime
            Time.schedule(this@Process)
        }
        item = JobWithPriority(job, priority, co)
        stack.add(item)
        stackLock.unlock()
        job.join()
    }

    fun <T> SelectBuilder<T>.onTimeout(time: Duration, body: suspend () -> T) {
        Process("Timeout for $name") {
            wait(time)
        }.job.onJoinWithCancel(body)
    }

    /** @return isTimeout */
    suspend fun withTimeout(time: Duration, body: suspend () -> Unit): Boolean {
        return coroutineScope {
            select {
                onTimeout(time) { true }
                launch {
                    body()
                }.onJoinWithCancel { false }
            }
        }
    }

    internal fun resume() {
        if (job.isCancelled) return
        co.resume(Unit)
    }

    final override fun compareTo(other: Process): Int {
        return compareValues(nextTime, other.nextTime)
    }

    companion object {
        val maxProcess = System.getProperty("maxProcess")?.toInt() ?: 100000
        val processCoroutineContext = Dispatchers.Default
        internal val runningCnt = AtomicInteger(0)
        internal suspend fun waitRunning() {
            while (runningCnt.get() > 0) {
                yield()
            }
        }

        @OptIn(InternalCoroutinesApi::class)
        val Job.onJoinWithCancel
            get() = object : SelectClause0 {
                override fun <R> registerSelectClause0(select: SelectInstance<R>, block: suspend () -> R) {
                    select.disposeOnSelect { job.cancel() }
                    job.onJoin.registerSelectClause0(select, block)
                }
            }
    }
}