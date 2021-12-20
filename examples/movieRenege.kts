import cf.wayzer.simkt.*
import cf.wayzer.simkt.Process.Companion.onJoinWithCancel
import jetbrains.letsPlot.geom.geomDensity
import jetbrains.letsPlot.geom.geomLine
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.*
import jetbrains.letsPlot.tooltips.layerTooltips
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

val waitTime = R.exponential(2.0).map { it.minutes }
val processTime = R.normalGaussianByBoxMuller().with(2.0, 0.5)
    .map { it.coerceAtLeast(0.1).minutes }
val movie = R.enumerated(0.5, 0.3, 0.2).map {
    when (it) {
        0 -> "MovieA"
        1 -> "MovieB"
        else -> "MovieC"
    }
}
val ticketNum = R.poisson(1.5).map { it.coerceAtLeast(1) }
val tickets = mutableMapOf(
    "MovieA" to 50,
    "MovieB" to 60,
    "MovieC" to 20,
)


data class TicketInfo(val name: String, val movie: String, val num: Int) {
    var ok = false
}

val ticketChange = MutableSharedFlow<Unit>(0, 0)
val queue = Channel<TicketInfo>(0)
val ticketLock = Mutex()


data class TimeMovie(val time: Duration, val arrive: Int, val leave: Int)

val timeMovieLog = mutableListOf(TimeMovie(Duration.ZERO, 0, 0))
fun newTimeMovieLog(body: TimeMovie.() -> TimeMovie) = timeMovieLog.add(timeMovieLog.last().body())
val startTime = mutableMapOf<String, Duration>()
val endTime = mutableListOf<Triple<String, Duration, Boolean>>()//name,endTime,timeout
val timeTickets = mutableListOf<Pair<Duration, Map<String, Int>>>()

Process("买票") {
    var i = 1
    while (true) {
        val info = TicketInfo("顾客#$i", movie(), ticketNum())
        Process(info.name) {
            if (tickets[info.movie]!! < info.num) {
                log("${info.movie} 票余量不足需求${info.num}张,不进行排队")
            } else {
                log("开始排队 ")
                startTime[name] = Time.nowH
                newTimeMovieLog { copy(time = Time.nowH, arrive = arrive + 1) }
                select<Unit> {
                    queue.onSend(info) {}
                    launch {
                        ticketChange.first { tickets[info.movie]!! < info.num }
                    }.onJoinWithCancel {
                        log("${info.movie} 票余量不足需求${info.num}张,退出排队")
                        endTime.add(Triple(info.name, Time.nowH, true))
                        newTimeMovieLog { copy(time = Time.nowH, leave = leave + 1) }
                    }
                }
            }
        }
        wait(waitTime())
        i++
    }
}
Process("卖票") {
    while (true) {
        val info = queue.receive()
        log("${info.name} 开始买票 ")
        wait(processTime())
        ticketLock.withLock {
            if (tickets[info.movie] == null) error("没有该类型票")
            if (tickets[info.movie]!! >= info.num) {
                tickets[info.movie] = tickets[info.movie]!! - info.num
                info.ok = true
            }
            ticketChange.emit(Unit)//通知票余量改变
        }
        if (info.ok) {
            log("${info.name}成功购买${info.num}张${info.movie}电影票")
            endTime.add(Triple(info.name, Time.nowH, false))
            newTimeMovieLog { copy(time = Time.nowH, leave = leave + 1) }
        } else {
            log("${info.name}买票失败,${info.movie}票余量不足${info.num}张")
            endTime.add(Triple(info.name, Time.nowH, true))
            newTimeMovieLog { copy(time = Time.nowH, leave = leave + 1) }
        }
        if (tickets.all { it.value == 0 }) {
            println("所有票均已卖完")
            break
        }
    }
}
Process("票余量统计") {
    while (true) {
        timeTickets.add(Time.nowH to tickets.toMap())
        wait(1.minutes)
    }
}
Log.init("movieRenege.csv")
Time.run(180.0.minutes)
println("Simulate end at ${Time.nowH}")
Log.close()
Time.reset()


val timeCost = endTime.map { (name, end, b) -> Triple(name, (end - startTime[name]!!), b) }
LetPlotHelper.wrap("movieRenege.html") {
    this += letsPlot() +
            ggtitle("顾客排队和离开售票处的情况") +
            xlab("Time (Hours)") + ylab("人数") +
            geomLine(tooltips = layerTooltips().line("时间 | @{x}h").line("@{group}人数 @y")) {
                fun data(name: String, map: TimeMovie.() -> Any) {
                    x = (x as List<*>?).orEmpty() + timeMovieLog.map { it.time.toDouble(DurationUnit.HOURS) }
                    y = (y as List<*>?).orEmpty() + timeMovieLog.map { it.map() }
                    group = (group as List<*>?).orEmpty() + List(timeMovieLog.size) { name }
                    color = group
                }
                data("到达售票处") { arrive }
                data("离开售票处") { leave }
                data("处在售票处") { arrive - leave }
            } +
            scaleColorDiscrete(name = "")
    this += letsPlot() +
            ggtitle("每种类型的电影票余量") +
            xlab("Time (Hours)")+
            ylab("票数") +
            geomLine(tooltips = layerTooltips().line("时间 | @{x}h").line("@{color} @y")) {
                tickets.keys.forEach { key ->
                    x = (x as List<*>?).orEmpty() + timeTickets.map { it.first.toDouble(DurationUnit.HOURS) }
                    y = (y as List<*>?).orEmpty() + timeTickets.map { it.second[key] }
                    color = (color as List<*>?).orEmpty() + List(timeTickets.size) { key }
                }
            } +
            scaleColorDiscrete(name = "电影票类型") +
            scaleXDiscrete()
    this += letsPlot() +
            ggtitle("每个顾客的等待时间") +
            ylab("等待时间") +
            geomPoint {
                x = timeCost.map { it.first }
                y = timeCost.map { it.second.toDouble(DurationUnit.MINUTES) }
                shape = timeCost.map { if (it.third) "未买到票" else "买票完成" }
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