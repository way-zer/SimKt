import cf.wayzer.simkt.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/*
机器工厂
在一个工厂里面，有８台机器，1个维修工。
这八台机器间隔faultTime（服从指数分布）发生故障，修理工修理机器的时间为processTime（服从正态分布）
维修工，平时没有事情的时候，维修工休息idleTime（服从指数分布）
维修工平时也会有其他的事情需要做，完成其他的事情的时间为fishTime（服从正态分布）
维修工处理其他的事情的优先级比处理机器故障的优先级低
当维修工处理其他的事情的时候，若有机器发生故障，则会发生中断
当维修完机器后，维修工继续完成未完成的其他的事情
*/
val faultIntervalTime = R.exponential(80.0).map { it.minutes }
val processTime = R.normalGaussianByBoxMuller().with(5.0, 0.1)
    .map { it.coerceAtLeast(0.1).minutes }
val idleTime = R.exponential(5.0).map { it.minutes }
val fishTime = R.normalGaussianByBoxMuller().with(20.0, 0.1)
    .map { it.coerceAtLeast(0.1).minutes }
var workTime = Duration.ZERO

val worker = Process("修理工") {
    wait(Duration.INFINITE)
}

//机器
repeat(8) {
    Process("机器#${it}") mach@{
        while (true) {
            val t = faultIntervalTime()
            wait(t)
            workTime += t
            log("发生故障")
            worker.intercept(2) {
                log("维修 ${this@mach.name}")
                wait(processTime())
                log("修理完成 ${this@mach.name}")
            }
        }
    }
}
Process("其他事情") {
    while (true) {
        wait(idleTime())
        worker.intercept(1) {
            log("正在干其他事情")
            wait(fishTime())
            log("完成其他事情")
        }
    }
}

Log.init("repairman.csv")
Time.run(8.hours)
Log.log("[${Time.nowH}] Simulate end")
Log.log("八台机器平均生产时间 %s (%.2f%%)".format(workTime / 8, workTime / 8 / Time.nowH * 100.0))
Log.close()


