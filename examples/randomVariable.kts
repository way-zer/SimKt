import cf.wayzer.simkt.R
import cf.wayzer.simkt.with
import jetbrains.letsPlot.geom.geomDensity
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleYContinuous


val gauss = R.normalGaussianByBoxMuller().with(mean = 10.0, dev = 5.0)
val samples = List(10000) { gauss() }

LetPlotHelper.wrap("randomVariable.html") {
    this += letsPlot() +
            ggtitle("高斯分布(N(10,5^2),实际均值%.2f)".format(samples.average())) +
            ylab("probability density") +
            geomDensity {
                x = samples
            } +
            scaleXContinuous() + scaleYContinuous()
}