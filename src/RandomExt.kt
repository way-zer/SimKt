@file:Suppress("unused")

import org.apache.commons.rng.UniformRandomProvider
import org.apache.commons.rng.sampling.distribution.*
import org.apache.commons.rng.simple.RandomSource

class RandomVariable<T, Raw>(val raw: Raw, private val f: () -> T) : () -> T by f

/** Wrapper for apache common_rng */
open class R(private val rng: UniformRandomProvider = RandomSource.JDK.create()) {
    /***/
    fun exponential(mean: Double) = AhrensDieterExponentialSampler.of(rng, mean).run { RandomVariable(this!!, this::sample) }
    fun exponentialByZiggurat(mean: Double) = ZigguratSampler.Exponential.of(rng, mean).run { RandomVariable(this!!, this::sample) }
    fun gamma(alpha: Double, theta: Double) = AhrensDieterMarsagliaTsangGammaSampler.of(rng, alpha, theta).run { RandomVariable(this!!, this::sample) }
    fun beta(alpha: Double, beta: Double) = ChengBetaSampler.of(rng, alpha, beta).run { RandomVariable(this!!, this::sample) }

    //    fun gaussian(mean: Double, dev: Double) = GaussianSampler.of(uniform,mean,dev)
    fun normalGaussianByBoxMuller() = BoxMullerNormalizedGaussianSampler.of<BoxMullerNormalizedGaussianSampler>(rng).run { RandomVariable(this!!, this::sample) }
    fun normalGaussianByMarsaglia() = MarsagliaNormalizedGaussianSampler.of<MarsagliaNormalizedGaussianSampler>(rng).run { RandomVariable(this!!, this::sample) }
    fun normalGaussianByZiggurat() = ZigguratNormalizedGaussianSampler.of<ZigguratNormalizedGaussianSampler>(rng).run { RandomVariable(this!!, this::sample) }
    fun normalGaussianByModifiedZiggurat() = ZigguratSampler.NormalizedGaussian.of(rng).run { RandomVariable(this!!, this::sample) }

    fun uniform(min: Double, max: Double, excludeBounds: Boolean = true) = ContinuousUniformSampler.of(rng, min, max, excludeBounds).run { RandomVariable(this!!, this::sample) }
    fun uniformLong(min: Long, max: Long) = UniformLongSampler.of(rng, min, max).run { RandomVariable(this!!, this::sample) }

    fun dirichlet(vararg alpha: Double) = DirichletSampler.of(rng, *alpha).run { RandomVariable(this!!, this::sample) }
    fun geometric(probabilityOfSuccess: Double) = GeometricSampler.of(rng, probabilityOfSuccess).run { RandomVariable(this!!, this::sample) }
    fun pareto(scale: Double, shape: Double) = InverseTransformParetoSampler.of(rng, scale, shape).run { RandomVariable(this!!, this::sample) }

    fun poisson(mean: Double) = PoissonSampler.of(rng, mean).run { RandomVariable(this!!, this::sample) }
    fun poissonSmallMeanByKemp(mean: Double) = KempSmallMeanPoissonSampler.of(rng, mean).run { RandomVariable(this!!, this::sample) }
    fun poissonByMarsagliaTsangWang(mean: Double) = MarsagliaTsangWangDiscreteSampler.Poisson.of(rng, mean).run { RandomVariable(this!!, this::sample) }

    fun levy(location: Double, scale: Double) = LevySampler.of(rng, location, scale).run { RandomVariable(this!!, this::sample) }
    fun binomial(trials: Int, probabilityOfSuccess: Double) = MarsagliaTsangWangDiscreteSampler.Binomial.of(rng, trials, probabilityOfSuccess).run { RandomVariable(this!!, this::sample) }
    fun enumerated(vararg probabilities: Double) = MarsagliaTsangWangDiscreteSampler.Enumerated.of(rng, probabilities).run { RandomVariable(this!!, this::sample) }
    fun zipf(numberOfElements: Int, exponent: Double) = RejectionInversionZipfSampler.of(rng, numberOfElements, exponent).run { RandomVariable(this!!, this::sample) }
    fun stable(alpha: Double, beta: Double, gamma: Double = 1.0, delta: Double = 0.0) = StableSampler.of(rng, alpha, beta, gamma, delta).run { RandomVariable(this!!, this::sample) }

    companion object Default : R()
}

fun <Raw: NormalizedGaussianSampler> RandomVariable<Double, Raw>.with(mean: Double, dev: Double) = GaussianSampler.of(this, mean, dev).run { RandomVariable(this!!, this::sample) }
fun <Raw: NormalizedGaussianSampler> RandomVariable<Double, Raw>.withLog(scale: Double, shape: Double) = LogNormalSampler.of(this, scale, shape).run {
    RandomVariable(this!!, this::sample)
}

fun <T, R, Raw> RandomVariable<T, Raw>.map(body: (T) -> R) = RandomVariable(raw) { body(invoke()) }
fun <T> RandomVariable<Double, T>.coerceIn(min: Double, max: Double) = RandomVariable(raw) { invoke().coerceIn(min, max) }