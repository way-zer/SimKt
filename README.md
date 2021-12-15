# SimKt

[![](https://jitpack.io/v/way-zer/SimKt.svg)](https://jitpack.io/#way-zer/SimKt)

A discrete event simulator based on kotlin and coroutines

## Installation

```kotlin
allprojects {
    repositories {
        maven("https://jitpack.io")
    }
}
dependencies {
    implementation("cf.wayzer:SimKt:VERSION")// VERSION see badge 
}
```

## QuickStart

```kotlin
//from examples/repairman.kts

import cf.wayzer.simkt.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration.Companion.minutes

/*
银行排队
间隔指数分布的时间arrivalTime客户到达，每个客户都有一个符合高斯分布的耐心时间naixin，
当每个客户等待的时间超过了耐心时间后，客户就会离开。
柜台处理每个客户的事情的时间为服从正态分布的处理时间processTime。
 */

//Define lots of RandomVariables
val arrivalTime = R.exponential(6.0).map { it.minutes }
val processTime = R.normalGaussianByBoxMuller().with(10.0, 0.1)
    .map { it.coerceAtLeast(0.1).minutes }
val naixin = R.normalGaussianByBoxMuller().with(5.0, 2.0)
    .map { it.coerceAtLeast(0.1).minutes }

//Define process to simulate
val queueIn = Channel<String>(0)
Process("客户") {
    var i = 1
    while (true) {
        Process("客户#$i") {
            val timeout = withTimeout(naixin()) {
                println("[${Time.nowH}] ${name}到达")
                queueIn.send(name)
            }
            if (timeout) {
                println("[${Time.nowH}] $name 放弃排队")
            }
        }
        wait(arrivalTime())
        i++
    }
}
Process("柜台") {
    while (true) {
        val visitor = queueIn.receive()
        println("[${Time.nowH}] 柜台接待 $visitor")
        wait(processTime())
        println("[${Time.nowH}] 柜台处理完成 $visitor")
    }
}

//Run simulate in limited time
Time.run(120.minutes)
println("[${Time.nowH}] Simulate end")
```

## Tips

* Process starts running after definition, and all are in parallel.
* Be careful using `withTimeout/delay` by coroutine, because they are in real time(clock time).  
  Using `Process.withTimeout` and `Process.wait` instead.
* You can use `join` `launch` as you like, but only `Process.wait` has something with simulation time.
* There are more examples in [examples](./examples) dir.

## Internal Implement

All process call `Process.wait` to join the time queue.  
Each step(in [Time.kt](./src/cf/wayzer/simkt/Time.kt)), Wait all coroutines suspend, then move time forward as the queue head and resume the head.  
Internal, using custom Dispatcher(in [Process](./src/cf/wayzer/simkt/Process.kt)) and AtomicInteger to monitor coroutines running state.

## License

Code and documentation released under the MIT license.  
Copyright © 2021, WayZer and GSH