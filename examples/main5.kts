import Process.Companion.onJoinWithCancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes

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
    "MovieB" to 20,
    "MovieC" to 20,
)


data class TicketInfo(val name: String, val movie: String, val num: Int) {
    var ok = false
}

val ticketChange = MutableSharedFlow<Unit>(0, 0)
val queue = Channel<TicketInfo>(0)
val ticketLock = Mutex()

Process("买票") {
    var i = 1
    while (true) {
        val info = TicketInfo("顾客#$i", movie(), ticketNum())
        Process(info.name) {
            if (tickets[info.movie]!! < info.num) {
                println("[${Time.nowH}] ${info.movie} 票余量不足需求${info.num}张, $name 不进行排队")
            } else {
                println("[${Time.nowH}] $name 开始排队 ")
                select<Unit> {
                    queue.onSend(info) {}
                    launch {
                        ticketChange.first { tickets[info.movie]!! < info.num }
                    }.onJoinWithCancel {
                        println("[${Time.nowH}] ${info.movie} 票余量不足需求${info.num}张, $name 退出排队")
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
        println("[${Time.nowH}] ${info.name} 开始买票 ")
        wait(processTime())
        ticketLock.withLock {
            if (tickets[info.movie] == null) error("没有该类型票")
            if (tickets[info.movie]!! >= info.num) {
                tickets[info.movie] = tickets[info.movie]!! - info.num
                info.ok = true
            }
            ticketChange.emit(Unit)//通知票余量改变
        }
        if (info.ok)
            println("[${Time.nowH}] ${info.name}成功购买${info.num}张${info.movie}电影票")
        else
            println("[${Time.nowH}] ${info.name}买票失败,${info.movie}票余量不足${info.num}张")
        if (tickets.all { it.value == 0 }) {
            println("所有票均已卖完")
            break
        }
    }
}
Time.run(180.0.minutes)
println("Simulate end at ${Time.nowH}")