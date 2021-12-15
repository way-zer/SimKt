import cf.wayzer.simkt.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration.Companion.minutes
/*
呼叫经过路由器被接线员处理
有两个呼叫者X、Y,呼叫间隔事件分别为callTimeX、callTimeY（服从指数分布）
两个呼叫者的呼叫按照呼叫时间顺序接入路由器中，路由器的处理时间为processTimeRouter（服从正态分布）。
路由器根据呼叫者类型决定呼叫由哪个接线员处理，X由接线员1处理，Y由接线员2处理。
接线员1处理X的呼叫的时间为processTimeX，接线员2处理Y的呼叫的时间为processTimeY（服从正态分布）。
*/
val callTimeX = R.exponential(3.0).map { it.minutes }
val callTimeY = R.exponential(6.0).map { it.minutes }
val processTimeRouter = R.normalGaussianByBoxMuller().with(1.0, 0.1)
    .map { it.coerceAtLeast(0.1).minutes }
val processTimeX = R.normalGaussianByBoxMuller().with(5.0, 1.0)
    .map { it.coerceAtLeast(0.1).minutes }
val processTimeY = R.normalGaussianByBoxMuller().with(7.0, 2.0)
    .map { it.coerceAtLeast(0.1).minutes }

val queueIn = Channel<String>(Channel.UNLIMITED)
Process("X_join") {
    while (true) {
        println("[${Time.nowH}] X 呼叫")
        queueIn.send("X_join")
        wait(callTimeX())
    }
}
Process("Y_join") {
    while (true) {
        println("[${Time.nowH}] Y 呼叫")
        queueIn.send("Y_join")
        wait(callTimeY())
    }
}
val queueX = Channel<String>(0)
val queueY = Channel<String>(0)
Process("Router") {
    while (true) {
        val msg = queueIn.receive()
        println("[${Time.nowH}] 路由开始处理$msg")
        wait(processTimeRouter())
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
        wait(processTimeX())
        println("[${Time.nowH}] X处理完成")
    }
}
Process("Y_get") {
    while (true) {
        queueY.receive()
        println("[${Time.nowH}] 接线员2处理Y")
        wait(processTimeY())
        println("[${Time.nowH}] Y处理完成")
    }
}
Time.run(60.minutes)
println("[${Time.nowH}] Simulate end")