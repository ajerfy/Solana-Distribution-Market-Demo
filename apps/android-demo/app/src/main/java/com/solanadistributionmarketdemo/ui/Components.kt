package com.solanadistributionmarketdemo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: String? = null,
) {
    val bg = if (selected) DemoColors.AccentYou else DemoColors.SurfaceElevated
    val fg = if (selected) DemoColors.OnAccent else DemoColors.TextPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (leading != null) {
            Text(leading, color = fg, style = MaterialTheme.typography.labelMedium)
        }
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    accent: Color = DemoColors.TextPrimary,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DemoColors.SurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label.uppercase(),
            color = DemoColors.TextDim,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            value,
            color = accent,
            fontFamily = NumericFamily,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun StatRow(label: String, value: String, accent: Color = DemoColors.TextPrimary, strong: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            color = accent,
            fontFamily = NumericFamily,
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = DemoColors.TextDim,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun TagPill(text: String, color: Color = DemoColors.AccentCrowd, filled: Boolean = false) {
    val bg = if (filled) color else color.copy(alpha = 0.14f)
    val fg = if (filled) DemoColors.OnAccent else color
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text.uppercase(),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(DemoColors.Surface)
        .border(1.dp, DemoColors.Border, shape)
    val tappable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(modifier = modifier.then(tappable).padding(padding), content = content)
}

@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = DemoColors.AccentCrowd,
    height: Dp = 28.dp,
    strokeWidth: Float = 2f,
) {
    Canvas(modifier = modifier.height(height)) {
        if (values.size < 2) return@Canvas
        val minV = values.min()
        val maxV = values.max()
        val span = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val w = size.width
        val h = size.height
        val step = w / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = h - ((v - minV) / span * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        // last-point dot
        val lastX = w
        val lastY = h - ((values.last() - minV) / span * h).toFloat()
        drawCircle(color = color, radius = strokeWidth + 1.2f, center = Offset(lastX, lastY))
    }
}

@Composable
fun PnlText(value: Double, prefix: String = "", style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium) {
    val color = when {
        value > 0 -> DemoColors.AccentLong
        value < 0 -> DemoColors.AccentShort
        else -> DemoColors.TextSecondary
    }
    val sign = if (value > 0) "+" else ""
    Text(
        "$prefix$sign${"%.2f".format(value)}",
        color = color,
        fontFamily = NumericFamily,
        style = style,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun CompactDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DemoColors.Border)
    )
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = DemoColors.AccentYou,
    leading: @Composable (() -> Unit)? = null,
) {
    val bg = if (enabled) accent else DemoColors.SurfaceMuted
    val fg = if (enabled) DemoColors.OnAccent else DemoColors.TextDim
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DemoColors.BorderStrong, RoundedCornerShape(12.dp))
            .background(DemoColors.SurfaceElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (enabled) DemoColors.TextPrimary else DemoColors.TextDim,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(DemoColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text("◌", color = DemoColors.TextDim, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
