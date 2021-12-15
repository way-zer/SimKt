import cf.wayzer.simkt.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.minutes
/*
加油站加油
加油站有８台加油机，每辆车经过间隔时间waitTime（指数分布）到达，经过refuelTime时间（正态分布）后加油完成，然后离开。
如果车辆到达后，所有加油机都在给车加油，新到达的车辆就会等待，直至有空闲的加油机，然后车辆进行加油。
*/
val waitTime = R.exponential(2.0).map { it.minutes }
val refuelTime = R.normalGaussianByBoxMuller().with(18.0, 1.0)
    .map { it.coerceAtLeast(5.0).minutes }
val fuelDispenserNum = 8

Process("") {
    val ma = Semaphore(fuelDispenserNum)
    var i = 1
    while (true) {
        Process("Car#$i") {
            println("$name 到达加油站 ${Time.nowH}")
            if (ma.availablePermits == 0)
                println("$name 等待加油机中")
            ma.withPermit {
                println("$name 开始加油 ${Time.nowH}")
                wait(refuelTime())
                println("$name 加油完成 ${Time.nowH}")
            }
        }
        wait(waitTime())
        i++
    }
}
Time.run(60.0.minutes)
println("Simulate end at ${Time.nowH}")