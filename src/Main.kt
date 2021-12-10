import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

fun main() {
//    Process {
//        //warm up
//        repeat(1000) {
//            Time.now
//            wait(1.nanoseconds)
//        }
//        println("Start performance")
//        //measure performance
//        val start = System.currentTimeMillis()
//        repeat(1000000) {
//            Time.now
//            wait(1.nanoseconds)
//        }
//        println("Cost Time ${System.currentTimeMillis() - start}ms")
//    }
    val queue = Channel<String>(Channel.UNLIMITED)
    Process("A") {
        val ma = Semaphore(8)
        ma.withPermit {
            wait(10.seconds)
        }
        queue.send("X")
        queue.receive()

        val subA = Process("SubA") {
            println("SubA start at ${Time.now}")
            wait(10.seconds)
            println("SubA end at ${Time.now}")
        }
        subA.job.join()
        while (true) {
            println("A Start parking at ${Time.now}")
            wait(5.seconds)

            println("A Start driving at ${Time.now}")
            wait(2.seconds)
        }
    }
    Process("B") {
        while (true) {
            println("B Start parking at ${Time.now}")
            wait(5.seconds)

            println("B Start driving at ${Time.now}")
            wait(2.seconds)
        }

    }
    Time.run(60.0)
    println("Simulate end at ${Time.now}")
}