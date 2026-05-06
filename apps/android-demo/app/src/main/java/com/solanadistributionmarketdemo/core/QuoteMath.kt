package com.solanadistributionmarketdemo.core

import com.solanadistributionmarketdemo.data.ContinuousQuotePreview
import com.solanadistributionmarketdemo.data.DemoCurvePoint
import com.solanadistributionmarketdemo.data.DemoMarket
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

fun buildContinuousQuotePreview(
    market: DemoMarket,
    targetMu: Double,
    targetSigma: Double,
): ContinuousQuotePreview {
    val currentMu = market.currentMuDisplay.toDouble()
    val currentSigma = market.currentSigmaDisplay.toDouble()
    val k = market.kDisplay.toDouble()
    val searchBounds = computeSearchBounds(currentMu, currentSigma, targetMu, targetSigma)
    val collateralRequired = computeCollateralRequired(
        currentMu = currentMu,
        currentSigma = currentSigma,
        proposedMu = targetMu,
        proposedSigma = targetSigma,
        k = k,
        lowerBound = searchBounds.first,
        upperBound = searchBounds.second,
        coarseSamples = market.coarseSamples,
        refineSamples = market.refineSamples,
    )
    val minTakerFee = market.minTakerFeeDisplay.toDouble()
    val feePaid = max(collateralRequired * market.takerFeeBps / 10_000.0, minTakerFee)
    val totalDebit = collateralRequired + feePaid
    val serializedInstructionHex = buildTradeInstructionHex(
        market = market,
        targetMu = targetMu,
        targetSigma = targetSigma,
        collateralRequired = collateralRequired,
        feePaid = feePaid,
        totalDebit = totalDebit,
        maxTotalDebit = totalDebit,
        lowerBound = searchBounds.first,
        upperBound = searchBounds.second,
    )

    return ContinuousQuotePreview(
        targetMu = targetMu,
        targetSigma = targetSigma,
        collateralRequired = collateralRequired,
        feePaid = feePaid,
        totalDebit = totalDebit,
        maxTotalDebit = totalDebit,
        quoteExpirySlot = market.demoQuoteExpirySlot,
        serializedInstructionHex = serializedInstructionHex,
    )
}

fun computeCollateralRequired(
    currentMu: Double,
    currentSigma: Double,
    proposedMu: Double,
    proposedSigma: Double,
    k: Double,
    lowerBound: Double,
    upperBound: Double,
    coarseSamples: Int,
    refineSamples: Int,
): Double {
    val coarse = maximumDirectionalLossWithArgmax(
        currentMu, currentSigma, proposedMu, proposedSigma, k,
        lowerBound, upperBound, coarseSamples,
    )
    val coarseStep = (upperBound - lowerBound) / coarseSamples.coerceAtLeast(1)
    val refineLower = max(lowerBound, coarse.first - coarseStep)
    val refineUpper = min(upperBound, coarse.first + coarseStep)
    val refine = maximumDirectionalLossWithArgmax(
        currentMu, currentSigma, proposedMu, proposedSigma, k,
        refineLower, refineUpper, refineSamples,
    )
    val endpointLoss = max(
        directionalLossAt(lowerBound, currentMu, currentSigma, proposedMu, proposedSigma, k),
        directionalLossAt(upperBound, currentMu, currentSigma, proposedMu, proposedSigma, k),
    )
    return max(max(coarse.second, refine.second), endpointLoss) + FIXED_EPSILON
}

private fun computeSearchBounds(
    currentMu: Double,
    currentSigma: Double,
    proposedMu: Double,
    proposedSigma: Double,
): Pair<Double, Double> {
    val span = abs(currentMu - proposedMu)
    val sigma = max(currentSigma, proposedSigma)
    val tail = span + sigma * 8.0
    return Pair(min(currentMu, proposedMu) - tail, max(currentMu, proposedMu) + tail)
}

private fun maximumDirectionalLossWithArgmax(
    currentMu: Double,
    currentSigma: Double,
    proposedMu: Double,
    proposedSigma: Double,
    k: Double,
    lowerBound: Double,
    upperBound: Double,
    samples: Int,
): Pair<Double, Double> {
    var bestX = lowerBound
    var bestLoss = 0.0
    val safeSamples = samples.coerceAtLeast(1)
    for (step in 0..safeSamples) {
        val x = lowerBound + (upperBound - lowerBound) * step.toDouble() / safeSamples.toDouble()
        val loss = directionalLossAt(x, currentMu, currentSigma, proposedMu, proposedSigma, k)
        if (loss > bestLoss) {
            bestLoss = loss
            bestX = x
        }
    }
    return Pair(bestX, bestLoss)
}

private fun directionalLossAt(
    x: Double,
    currentMu: Double,
    currentSigma: Double,
    proposedMu: Double,
    proposedSigma: Double,
    k: Double,
): Double {
    val currentValue = scaledNormalValue(x, currentMu, currentSigma, k)
    val proposedValue = scaledNormalValue(x, proposedMu, proposedSigma, k)
    return max(currentValue - proposedValue, 0.0)
}

private fun scaledNormalValue(x: Double, mu: Double, sigma: Double, k: Double): Double {
    val safeSigma = sigma.coerceAtLeast(0.0001)
    val lambda = k * sqrt(2.0 * safeSigma * sqrt(Math.PI))
    return lambda * normalPdf(x, mu, safeSigma)
}

fun normalPdf(x: Double, mu: Double, sigma: Double): Double {
    val safeSigma = sigma.coerceAtLeast(0.0001)
    val coefficient = 1.0 / (safeSigma * sqrt(2.0 * Math.PI))
    val exponent = -((x - mu) * (x - mu)) / (2.0 * safeSigma * safeSigma)
    return coefficient * exp(exponent)
}

fun buildContinuousCurvePoints(
    currentMu: Double,
    currentSigma: Double,
    proposedMu: Double,
    proposedSigma: Double,
    samples: Int = 81,
): List<DemoCurvePoint> {
    val widestSigma = max(currentSigma, proposedSigma)
    val centerMin = min(currentMu, proposedMu)
    val centerMax = max(currentMu, proposedMu)
    val lower = centerMin - widestSigma * 4.0
    val upper = centerMax + widestSigma * 4.0
    val step = (upper - lower) / (samples - 1).coerceAtLeast(1)
    return List(samples) { index ->
        val x = lower + step * index
        val current = normalPdf(x, currentMu, currentSigma)
        val proposed = normalPdf(x, proposedMu, proposedSigma)
        DemoCurvePoint(x = x, current = current, proposed = proposed, edge = proposed - current)
    }
}

private fun buildTradeInstructionHex(
    market: DemoMarket,
    targetMu: Double,
    targetSigma: Double,
    collateralRequired: Double,
    feePaid: Double,
    totalDebit: Double,
    maxTotalDebit: Double,
    lowerBound: Double,
    upperBound: Double,
): String {
    val bytes = mutableListOf<Byte>()
    bytes += 1.toByte()
    bytes += decodeHex(market.marketIdHex).toList()
    bytes += packU64(market.stateVersion)
    bytes += packFixed(targetMu)
    bytes += packFixed(targetSigma)
    bytes += packFixed(collateralRequired)
    bytes += packFixed(feePaid)
    bytes += packFixed(totalDebit)
    bytes += packFixed(maxTotalDebit)
    bytes += packU32(market.takerFeeBps.toLong())
    bytes += packFixed(market.minTakerFeeDisplay.toDouble())
    bytes += packFixed(lowerBound)
    bytes += packFixed(upperBound)
    bytes += packU32(market.coarseSamples.toLong())
    bytes += packU32(market.refineSamples.toLong())
    bytes += packU64(market.demoQuoteSlot)
    bytes += packU64(market.demoQuoteExpirySlot)
    return encodeHex(bytes.toByteArray())
}

fun Double.formattedDecimal(): String = "%.9f".format(this)

fun Double.compactDecimal(places: Int = 2): String {
    val s = "%.${places}f".format(this)
    return if (s.contains(".")) s.trimEnd('0').trimEnd('.') else s
}

fun String.trimZeros(): String {
    return if (contains(".") && length > 6) trimEnd('0').trimEnd('.') else this
}

fun simulatedPayoff(
    stake: Double,
    yourMu: Double,
    yourSigma: Double,
    realized: Double,
): Double {
    val safeSigma = yourSigma.coerceAtLeast(0.0001)
    val z = (realized - yourMu) / safeSigma
    val score = exp(-0.5 * z * z)
    return stake * (score - 0.4) * 2.5
}
