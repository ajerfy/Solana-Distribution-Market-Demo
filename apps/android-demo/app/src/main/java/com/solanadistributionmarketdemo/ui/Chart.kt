package com.solanadistributionmarketdemo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.solanadistributionmarketdemo.core.normalPdf
import kotlin.math.max

@Composable
fun DistributionChart(
    crowdMu: Double,
    crowdSigma: Double,
    yourMu: Double?,
    yourSigma: Double?,
    realizedOutcome: Double? = null,
    height: Dp = 240.dp,
    modifier: Modifier = Modifier,
) {
    val muMin = listOfNotNull(crowdMu, yourMu).min()
    val muMax = listOfNotNull(crowdMu, yourMu).max()
    val widestSigma = max(crowdSigma, yourSigma ?: crowdSigma)
    val xLow = muMin - widestSigma * 4.0
    val xHigh = muMax + widestSigma * 4.0

    val plotBg = DemoColors.SurfaceElevated
    val gridLine = if (DemoColors.isLight) Color(0x141B2034) else Color(0x10FFFFFF)
    val axisLine = DemoColors.BorderStrong
    val crowdLine = DemoColors.AccentCrowd
    val youLine = DemoColors.AccentYou
    val warnLine = DemoColors.AccentWarn

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(plotBg),
    ) {
        val w = size.width
        val h = size.height
        val padX = 14f
        val padTop = 14f
        val padBottom = 22f
        val plotW = w - padX * 2
        val plotH = h - padTop - padBottom

        // crowd curve samples
        val samples = 96
        val crowdPts = DoubleArray(samples)
        val yourPts = if (yourMu != null && yourSigma != null) DoubleArray(samples) else null
        var maxY = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / (samples - 1)
            val x = xLow + (xHigh - xLow) * t
            crowdPts[i] = normalPdf(x, crowdMu, crowdSigma)
            if (yourPts != null && yourMu != null && yourSigma != null) {
                yourPts[i] = normalPdf(x, yourMu, yourSigma)
                if (yourPts[i] > maxY) maxY = yourPts[i]
            }
            if (crowdPts[i] > maxY) maxY = crowdPts[i]
        }
        val yScale = (plotH * 0.92f) / maxY.toFloat().coerceAtLeast(1e-6f)

        // grid: 4 horizontal hairlines
        repeat(4) { idx ->
            val gy = padTop + plotH * (idx + 1) / 5f
            drawLine(
                color = gridLine,
                start = Offset(padX, gy),
                end = Offset(padX + plotW, gy),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)),
            )
        }
        // axis
        drawLine(
            color = axisLine,
            start = Offset(padX, padTop + plotH),
            end = Offset(padX + plotW, padTop + plotH),
            strokeWidth = 1f,
        )

        fun pathOf(values: DoubleArray): Path {
            val p = Path()
            for (i in values.indices) {
                val x = padX + plotW * (i.toFloat() / (samples - 1))
                val y = padTop + plotH - (values[i].toFloat() * yScale)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        fun filledPathOf(values: DoubleArray): Path {
            val p = pathOf(values)
            p.lineTo(padX + plotW, padTop + plotH)
            p.lineTo(padX, padTop + plotH)
            p.close()
            return p
        }

        // crowd fill
        drawPath(filledPathOf(crowdPts), color = crowdLine.copy(alpha = 0.10f))
        // crowd line
        drawPath(pathOf(crowdPts), color = crowdLine, style = Stroke(width = 2f, cap = StrokeCap.Round))

        if (yourPts != null) {
            drawPath(filledPathOf(yourPts), color = youLine.copy(alpha = 0.16f))
            drawPath(pathOf(yourPts), color = youLine, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }

        fun xToCanvas(x: Double): Float =
            padX + plotW * ((x - xLow) / (xHigh - xLow)).toFloat()

        // crowd μ marker
        val crowdX = xToCanvas(crowdMu)
        drawLine(
            color = crowdLine.copy(alpha = 0.55f),
            start = Offset(crowdX, padTop),
            end = Offset(crowdX, padTop + plotH),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
        )

        // your μ marker
        if (yourMu != null) {
            val yx = xToCanvas(yourMu)
            drawLine(
                color = youLine.copy(alpha = 0.85f),
                start = Offset(yx, padTop),
                end = Offset(yx, padTop + plotH),
                strokeWidth = 1.4f,
            )
            drawCircle(color = youLine, radius = 5f, center = Offset(yx, padTop + plotH))
        }

        if (realizedOutcome != null) {
            val rx = xToCanvas(realizedOutcome).coerceIn(padX, padX + plotW)
            drawLine(
                color = warnLine,
                start = Offset(rx, padTop),
                end = Offset(rx, padTop + plotH),
                strokeWidth = 2f,
            )
            drawCircle(color = warnLine, radius = 6f, center = Offset(rx, padTop + plotH))
        }
    }
}
