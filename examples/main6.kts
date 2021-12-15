import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
/*
小学数学题
小明起床 穿衣3min  烧开水 15min  吃饭 7min 洗碗筷 2min 整理书包 2min 冲奶粉1min
最短几分钟完成？
3+15+1 = 19
* */
Process("") {
    println("[${Time.nowH}] 开始穿衣服")
    wait(3.minutes)
    println("[${Time.nowH}] 衣服穿完了")

    val d = Process("D") {
        println("[${Time.nowH}] 开始烧开水")
        wait(15.minutes)
        println("[${Time.nowH}] 水烧开了")
    }
    val e = Process("E") {
        println("[${Time.nowH}] 开始吃早饭")
        wait(7.minutes)
        println("[${Time.nowH}] 吃完早饭")
    }
    e.job.join()
    val f = Process("F") {
        println("[${Time.nowH}] 开始洗碗筷")
        wait(2.minutes)
        println("[${Time.nowH}] 洗完碗筷")
    }
    f.job.join()
    val g = Process("G") {
        println("[${Time.nowH}] 开始整理书包")
        wait(2.minutes)
        println("[${Time.nowH}] 整理书包完成")
    }
    d.job.join()
    g.job.join()

    println("[${Time.nowH}] 开始冲奶粉")
    wait(1.minutes)
    println("[${Time.nowH}] 奶粉冲完")

}
Time.run(Duration.INFINITE)
println("[${Time.nowH}] Simulate end")