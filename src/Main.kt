import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration.Companion.minutes

//路由器例子
fun main() {
    val queueIn = Channel<String>(Channel.UNLIMITED)
    Process("X_join") {
        while (true) {
            println("[${Time.nowH}] X call")
            queueIn.send("X_join")
            wait(5.minutes)
        }
    }
    Process("Y_join") {
        while (true) {
            println("[${Time.nowH}] Y call")
            queueIn.send("Y_join")
            wait(10.minutes)
        }
    }
    val queueX = Channel<String>(0)
    val queueY = Channel<String>(0)
    Process("Router") {
        while (true) {
            val msg = queueIn.receive()
            println("[${Time.nowH}] 路由开始处理$msg")
            wait(1.minutes)
            when (msg) {
                "X_join" -> {
                    queueX.send(msg)
                    println("[${Time.nowH}] X发给接线员1")
                }
                "Y_join" -> {
                    queueY.send(msg)
                    println("[${Time.nowH}] Y发给接线员2")
                }
            }
        }
    }
    Process("X_get") {
        while (true) {
            queueX.receive()
            println("[${Time.nowH}] 接线员1处理X")
            wait(4.minutes)
            println("[${Time.nowH}] X处理完成")
        }
    }
    Process("Y_get") {
        while (true) {
            queueY.receive()
            println("[${Time.nowH}] 接线员2处理Y")
            wait(7.minutes)
            println("[${Time.nowH}] Y处理完成")
        }
    }
    Time.run(10.minutes)
    println("[${Time.nowH}] Simulate end")
}