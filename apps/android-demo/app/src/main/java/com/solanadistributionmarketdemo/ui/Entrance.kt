package com.solanadistributionmarketdemo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ParabolaEntranceScreen(onEnter: () -> Unit) {
    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        composed = true
    }
    val reveal by animateFloatAsState(
        targetValue = if (composed) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "Parabola reveal",
    )
    val twinkle = rememberInfiniteTransition(label = "Entrance dot twinkle")
    val greenPulse by twinkle.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Green dot pulse",
    )
    val redPulse by twinkle.animateFloat(
        initialValue = 1.18f,
        targetValue = 0.76f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1080),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Red dot pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DemoColors.Background)
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        EntranceCurveScene(
            progress = reveal,
            greenPulse = greenPulse,
            redPulse = redPulse,
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.Center),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Parabola",
                style = MaterialTheme.typography.displayMedium,
                color = DemoColors.TextPrimary.copy(alpha = reveal),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Button(
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DemoColors.AccentYou,
                    contentColor = DemoColors.OnAccent,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Trade on estimation markets",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EntranceCurveScene(
    progress: Float,
    greenPulse: Float,
    redPulse: Float,
    modifier: Modifier = Modifier,
) {
    val grid = DemoColors.BorderStrong
    val surface = DemoColors.Surface
    val border = DemoColors.BorderStrong
    val crowd = DemoColors.TextSecondary
    val you = DemoColors.AccentCrowd
    val long = DemoColors.AccentLong
    val short = DemoColors.AccentShort

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight * 0.48f
        val chartLeft = canvasWidth * 0.06f
        val chartRight = canvasWidth * 0.94f
        val chartWidth = chartRight - chartLeft
        val curveAlpha = progress.coerceIn(0f, 1f)

        repeat(4) { index ->
            val y = centerY - 96f + index * 64f
            drawLine(
                color = grid.copy(alpha = curveAlpha * 0.45f),
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.4f,
            )
        }

        drawCircle(
            color = surface.copy(alpha = curveAlpha * 0.92f),
            radius = canvasWidth * 0.34f,
            center = Offset(canvasWidth * 0.50f, centerY),
        )
        drawCircle(
            color = border.copy(alpha = curveAlpha * 0.80f),
            radius = canvasWidth * 0.34f,
            center = Offset(canvasWidth * 0.50f, centerY),
            style = Stroke(width = 2.5f),
        )

        val samples = 72
        fun normalY(step: Int, mean: Float, sigma: Float): Float {
            val x = step.toFloat() / samples
            val z = (x - mean) / sigma
            val density = kotlin.math.exp((-(z * z) / 2f).toDouble()).toFloat()
            return centerY + 92f - density * 178f
        }

        for (step in 0 until samples) {
            val visibleStep = (samples * curveAlpha).toInt()
            if (step > visibleStep) break
            val x1 = chartLeft + chartWidth * step / samples
            val x2 = chartLeft + chartWidth * (step + 1) / samples
            drawLine(
                color = crowd.copy(alpha = curveAlpha),
                start = Offset(x1, normalY(step, 0.45f, 0.13f)),
                end = Offset(x2, normalY(step + 1, 0.45f, 0.13f)),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = you.copy(alpha = curveAlpha),
                start = Offset(x1, normalY(step, 0.58f, 0.12f)),
                end = Offset(x2, normalY(step + 1, 0.58f, 0.12f)),
                strokeWidth = 4.2f,
                cap = StrokeCap.Round,
            )
        }

        val greenCenter = Offset(chartLeft + chartWidth * 0.64f, centerY - 116f)
        val redCenter = Offset(chartLeft + chartWidth * 0.38f, centerY - 95f)
        drawCircle(
            color = long.copy(alpha = curveAlpha * 0.18f * greenPulse),
            radius = 12f * greenPulse,
            center = greenCenter,
        )
        drawCircle(
            color = long.copy(alpha = curveAlpha),
            radius = 5.8f + greenPulse * 1.2f,
            center = greenCenter,
        )
        drawCircle(
            color = short.copy(alpha = curveAlpha * 0.18f * redPulse),
            radius = 11f * redPulse,
            center = redCenter,
        )
        drawCircle(
            color = short.copy(alpha = curveAlpha),
            radius = 4.8f + redPulse * 1.1f,
            center = redCenter,
        )
    }
}
