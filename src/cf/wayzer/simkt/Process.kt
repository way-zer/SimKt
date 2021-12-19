package cf.wayzer.simkt

import cf.wayzer.simkt.util.DispatcherWithTrace
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.SelectClause0
import kotlinx.coroutines.selects.SelectInstance
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.DurationUnit

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class Process(val name: String, body: suspend Process.() -> Unit) : Comparable<Process>, CoroutineScope {
    final override val coroutineContext = (CoroutineName(name) + tracedDispatcher).let {
        it + CoroutineScope(it).launch {
            wait()
            body.invoke(this@Process)
        }.apply {
            invokeOnCompletion { Time.deSchedule(this@Process) }
        }
    }

    val job by coroutineContext::job
    val active by job::isActive
    val finished by job::isCompleted
    var nextTime: Double = 0.0
        private set

    private lateinit var co: Continuation<Unit>
    suspend fun wait(duration: Duration = Duration.ZERO) {
        suspendCoroutine<Unit> { co ->
            this.co = co
            nextTime = Time.now + duration.toDouble(DurationUnit.SECONDS)
            Time.schedule(this)
        }
    }

    internal fun resume() {
        if (job.isCancelled) return
        co.resume(Unit)
    }

    data class JobWithPriority(val job: Job, val priority: Int, val lastCo: Continuation<Unit>) : Comparable<JobWithPriority> {
        override fun compareTo(other: JobWithPriority): Int {
            return compareValues(priority, other.priority)
        }
    }

    private val stack = sortedSetOf<JobWithPriority>()// priority to job
    private val stackLock = Mutex()

    suspend fun intercept(priority: Int, block: suspend Process.() -> Unit) {
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
            block.invoke(this@Process)
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

    final override fun compareTo(other: Process): Int {
        return compareValues(nextTime, other.nextTime)
    }

    companion object {
        val maxProcess = System.getProperty("maxProcess")?.toInt() ?: 100000
        var tracedDispatcher = DispatcherWithTrace(Dispatchers.Default, maxProcess) { _, _ ->
            println("Too many coroutine, exceed than $maxProcess")
            println("You can modify it with property \"maxProcess\"")
            exitProcess(-1)
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