import cf.wayzer.simkt.*
import jetbrains.letsPlot.geom.geomDensity
import jetbrains.letsPlot.geom.geomLine
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.scaleColorDiscrete
import jetbrains.letsPlot.scale.scaleShape
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleYContinuous
import jetbrains.letsPlot.tooltips.layerTooltips
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

/*
银行排队
间隔指数分布的时间arrivalTime客户到达，每个客户都有一个符合高斯分布的耐心时间naixin，
当每个客户等待的时间超过了耐心时间后，客户就会离开。
柜台处理每个客户的事情的时间为服从正态分布的处理时间processTime。
 */

val arrivalTime = R.exponential(1.0).map { it.minutes }
val processTime = R.normalGaussianByBoxMuller().with(10.0, 3.0)
    .map { it.coerceAtLeast(0.1).minutes }
val naixin = R.normalGaussianByBoxMuller().with(30.0, 10.0)
    .map { it.coerceAtLeast(0.1).minutes }

data class TimeBank(val time: Duration, val arrive: Int, val leave: Int)

val timeBankLog = mutableListOf(TimeBank(Duration.ZERO, 0, 0))
fun newTimeBankLog(body: TimeBank.() -> TimeBank) = timeBankLog.add(timeBankLog.last().body())
val startTime = mutableMapOf<String, Duration>()
val endTime = mutableListOf<Triple<String, Duration, Boolean>>()//name,endTime,timeout

//银行排队
val queueIn = Channel<String>(0)
Process("客户") {
    var i = 1
    while (true) {
        Process("客户#$i") {
            startTime[name] = Time.nowH
            newTimeBankLog { copy(time = Time.nowH, arrive = arrive + 1) }
            val timeout = withTimeout(naixin()) {
                log("到达")
                queueIn.send(name)
            }
            if (timeout) {
                log("放弃排队")
                endTime.add(Triple(name, Time.nowH, true))
                newTimeBankLog { copy(time = Time.nowH, leave = leave + 1) }
            }
        }
        wait(arrivalTime())
        i++
    }
}
repeat(4) {
    Process("柜台#$it") {
        while (true) {
            val visitor = queueIn.receive()
            log("接待 $visitor")
            wait(processTime())
            endTime.add(Triple(visitor, Time.nowH, false))
            newTimeBankLog { copy(time = Time.nowH, leave = leave + 1) }
            log("处理完成 $visitor")
        }
    }
}

Log.init("bankRenege.csv")
Time.run(300.minutes)
println("[${Time.nowH}] Simulate end")
Log.close()
Time.reset()

val timeCost = endTime.map { (name, end, b) -> Triple(name, (end - startTime[name]!!), b) }
LetPlotHelper.wrap("bankRenege.html") {
    this += letsPlot() +
            ggtitle("客户排队和离开银行的情况") +
            xlab("Time (Hours)") + ylab("人数") +
            geomLine(tooltips = layerTooltips().line("时间 | @{x}h").line("@{group}人数 @y")) {
                fun data(name: String, map: TimeBank.() -> Any) {
                    x = (x as List<*>?).orEmpty() + timeBankLog.map { it.time.toDouble(DurationUnit.HOURS) }
                    y = (y as List<*>?).orEmpty() + timeBankLog.map { it.map() }
                    group = (group as List<*>?).orEmpty() + List(timeBankLog.size) { name }
                    color = group
                }
                data("到达银行") { arrive }
                data("离开银行") { leave }
                data("处在银行") { arrive - leave }
            } +
            scaleColorDiscrete(name = "")
    this += letsPlot() +
            ggtitle("每个客户的等待时间") +
            ylab("等待时间") +
            geomPoint {
                x = timeCost.map { it.first }
                y = timeCost.map { it.second.toDouble(DurationUnit.MINUTES) }
                shape = timeCost.map { if (it.third) "超时离开" else "办理完成" }
            } +
            scaleShape(name = "")
    this += letsPlot() +
            ggtitle("等待时间分布(均值%.2f分钟)".format(timeCost.map { it.second.toDouble(DurationUnit.MINUTES) }.average())) +
            xlab("等待时间 (Minutes)") +
            ylab("probability density") +
            geomDensity {
                x = timeCost.map { it.second.toDouble(DurationUnit.MINUTES) }
            } +
            scaleXContinuous() + scaleYContinuous()
}