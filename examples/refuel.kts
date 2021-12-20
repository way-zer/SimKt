import cf.wayzer.simkt.*
import jetbrains.letsPlot.geom.geomDensity
import jetbrains.letsPlot.geom.geomLine
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.scaleColorDiscrete
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleXDiscrete
import jetbrains.letsPlot.scale.scaleYContinuous
import jetbrains.letsPlot.tooltips.layerTooltips
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

/*
加油站加油
加油站有８台加油机，每辆车经过间隔时间waitTime（指数分布）到达，经过refuelTime时间（正态分布）后加油完成，然后离开。
如果车辆到达后，所有加油机都在给车加油，新到达的车辆就会等待，直至有空闲的加油机，然后车辆进行加油。
*/
val waitTime = R.exponential(2.0).map { it.minutes }
val refuelTime = R.normalGaussianByBoxMuller().with(18.0, 1.0)
    .map { it.coerceAtLeast(5.0).minutes }
val fuelDispenserNum = 8

data class TimeCar(val time: Duration, val arrive: Int, val leave: Int)

val timeCarLog = mutableListOf(TimeCar(Duration.ZERO, 0, 0))
fun newTimeCarLog(body: TimeCar.() -> TimeCar) = timeCarLog.add(timeCarLog.last().body())
val timeCost = mutableListOf<Pair<Int, Double>>()

Process("") {
    val ma = Semaphore(fuelDispenserNum)
    var i = 1
    while (true) {
        val ii = i
        Process("Car#$i") {
            val start = Time.now
            newTimeCarLog { copy(time = Time.nowH, arrive = arrive + 1) }
            log("到达加油站")
            if (ma.availablePermits == 0)
                log("等待加油机中")
            ma.withPermit {
                log("开始加油")
                wait(refuelTime())
                log("加油完毕")
            }
            val end = Time.now
            newTimeCarLog { copy(time = Time.nowH, leave = leave + 1) }
            timeCost.add(ii to end - start)
        }
        wait(waitTime())
        i++
    }
}

Log.init("refuel.csv")
Time.run(8.hours)
println("Simulate end at ${Time.nowH}")
Log.close()
Time.reset()

LetPlotHelper.wrap("refuel.html") {
    this += letsPlot() +
            ggtitle("车辆加入和离开加油站情况") +
            xlab("Time (Hours)") + ylab("车辆数目") +
            geomLine(tooltips = layerTooltips().line("时间 | @{x}h").line("@{group}车辆数 @y")) {
                fun data(name: String, map: TimeCar.() -> Any) {
                    x = (x as List<*>?).orEmpty() + timeCarLog.map { it.time.toDouble(DurationUnit.HOURS) }
                    y = (y as List<*>?).orEmpty() + timeCarLog.map { it.map() }
                    group = (group as List<*>?).orEmpty() + List(timeCarLog.size) { name }
                    color = group
                }
                data("到达加油站") { arrive }
                data("离开加油站") { leave }
                data("处在加油站") { arrive - leave }
            } +
            scaleColorDiscrete() + scaleXDiscrete()
    this += letsPlot() +
            ggtitle("每辆车的等待时间") +
            xlab("车")+
            ylab("等待时间 (Minutes)") +
            geomLine {
                x = timeCost.map { it.first }
                y = timeCost.map { it.second / 60.0 }
            } +
            scaleXDiscrete() + scaleYContinuous()
    this += letsPlot() +
            ggtitle("等待时间分布(均值%.2f分钟)".format(timeCost.map { it.second / 60.0 }.average())) +
            xlab("等待时间 (Minutes)") +
            ylab("probability density") +
            geomDensity {
                x = timeCost.map { it.second / 60.0 }
            } +
            scaleXContinuous() + scaleYContinuous()
}