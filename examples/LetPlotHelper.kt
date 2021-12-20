import jetbrains.datalore.plot.PlotHtmlHelper
import jetbrains.letsPlot.FrontendContext
import jetbrains.letsPlot.LetsPlot
import jetbrains.letsPlot.intern.Plot
import java.io.File

object LetPlotHelper {
    fun wrap(outFile: String = "out.html", body: HelperScope.() -> Unit) {
        val writer = File(outFile).bufferedWriter()
        writer.write(
            "<html lang=\"en\"><head>" +
                    "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />" +
                    PlotHtmlHelper.getStaticConfigureHtml(PlotHtmlHelper.scriptUrl("2.2.1")) +
                    "</head><body>"
        )
        LetsPlot.frontendContext = object : FrontendContext {
            override fun display(plotSpecRaw: MutableMap<String, Any>) {
                writer.write(PlotHtmlHelper.getStaticDisplayHtmlForRawSpec(plotSpecRaw))
                writer.newLine()
            }
        }
        HelperScope.body()
        writer.append("</body></html>")
        writer.close()
    }

    object HelperScope {
        operator fun plusAssign(value: Plot) {
            value.show()
        }
    }
}