import cf.wayzer.simkt.*
import jetbrains.letsPlot.geom.geomDensity
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleYContinuous
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

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
Process("X") {
    var i = 0
    while (true) {
        i++
        log("呼叫 $i")
        queueIn.send("X#$i")
        wait(callTimeX())
    }
}
Process("Y") {
    var i = 0
    while (true) {
        i++
        log("呼叫 $i")
        queueIn.send("Y#$i")
        wait(callTimeY())
    }
}

val handleTime = mutableListOf<Duration>()
val queueX = Channel<String>(0)
val queueY = Channel<String>(0)
Process("Router") {
    while (true) {
        val msg = queueIn.receive()
        val start = Time.nowH
        log("开始处理 $msg")
        wait(processTimeRouter())
        when {
            msg.startsWith("X") -> {
                queueX.send(msg)
                log("发给接线员1 $msg")
            }
            msg.startsWith("Y") -> {
                queueY.send(msg)
                log("发给接线员2 $msg")
            }
        }
        val end = Time.nowH
        handleTime.add(end - start)
    }
}
Process("接线员1") {
    while (true) {
        val msg = queueX.receive()
        log("处理 $msg")
        wait(processTimeX())
        log("处理完成 $msg")
    }
}
Process("接线员2") {
    while (true) {
        val msg = queueY.receive()
        log("处理 $msg")
        wait(processTimeY())
        log("处理完成 $msg")
    }
}

Log.init("callAndRoute.csv")
Time.run(180.minutes)
println("[${Time.nowH}] Simulate end")
Log.close()
Time.reset()

LetPlotHelper.wrap("callAndRoute.html") {
    this += letsPlot() +
            ggtitle("处理时间分布(均值%.2f分钟)".format(handleTime.map { it.toDouble(DurationUnit.MINUTES) }.average())) +
            xlab("处理时间 (Minutes)") +
            ylab("probability density") +
            geomDensity {
                x = handleTime.map { it.toDouble(DurationUnit.MINUTES) }
            } +
            scaleXContinuous() + scaleYContinuous()
}