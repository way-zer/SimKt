import cf.wayzer.simkt.Process
import cf.wayzer.simkt.Time
import java.io.BufferedWriter
import java.io.File

object Log {
    lateinit var writer: BufferedWriter
    fun init(file: String = "out.csv") {
        writer = File(file).bufferedWriter()
    }

    fun log(text: String) {
        println(text)
        writer.appendLine(text)
    }

    fun close() {
        writer.flush()
    }
}

fun Process.log(event: String) = Log.log("${Time.nowH}\t$name\t$event")