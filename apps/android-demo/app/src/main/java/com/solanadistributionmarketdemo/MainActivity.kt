package com.solanadistributionmarketdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val terminalColorScheme = lightColorScheme(
    primary = Color(0xFF0891B2),
    secondary = Color(0xFFF59E0B),
    tertiary = Color(0xFF10B981),
    background = Color(0xFFF6F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EEF5),
    onPrimary = Color.White,
    onSecondary = Color(0xFF1F2937),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF4B5563),
    error = Color(0xFFDC2626),
)

class MainActivity : ComponentActivity() {
    private lateinit var walletSender: ActivityResultSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        walletSender = ActivityResultSender(this)
        setContent {
            MaterialTheme(colorScheme = terminalColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    val payload = remember {
                        loadDemoPayload(
                            context.assets.open("demo_market.json").bufferedReader().use { it.readText() }
                        )
                    }
                    TradeAppScreen(payload, walletSender)
                }
            }
        }
    }
}

data class DemoPayload(
    val market: DemoMarket,
    val presets: List<DemoPreset>,
    val quoteGrid: List<DemoPreset>,
)

data class DemoMarket(
    val title: String,
    val status: String,
    val currentMuDisplay: String,
    val currentSigmaDisplay: String,
    val backingDisplay: String,
    val makerFeesEarnedDisplay: String,
    val makerDepositDisplay: String,
    val totalTrades: Long,
    val maxOpenTrades: Long,
    val expirySlot: Long,
)

data class DemoCurvePoint(
    val x: Double,
    val current: Double,
    val proposed: Double,
    val edge: Double,
)

data class DemoPreset(
    val id: String,
    val label: String,
    val targetMuDisplay: String,
    val targetSigmaDisplay: String,
    val collateralRequiredDisplay: String,
    val feePaidDisplay: String,
    val totalDebitDisplay: String,
    val maxTotalDebitDisplay: String,
    val quoteExpirySlot: Long,
    val serializedInstructionHex: String,
    val curvePoints: List<DemoCurvePoint>,
)

data class SubmitStatus(
    val message: String,
    val isError: Boolean = false,
    val isWorking: Boolean = false,
)

private enum class AppTab(val label: String) {
    Trade("Trade"),
    Positions("Positions"),
    Market("Market"),
    Maker("Maker"),
}

@Composable
private fun TradeAppScreen(
    payload: DemoPayload,
    walletSender: ActivityResultSender,
) {
    var targetMu by remember { mutableStateOf(payload.presets.first().targetMuDisplay.toFloat()) }
    var targetSigma by remember { mutableStateOf(payload.presets.first().targetSigmaDisplay.toFloat()) }
    var activeTab by remember { mutableStateOf(AppTab.Trade) }
    var submitStatus by remember { mutableStateOf<SubmitStatus?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val previewQuote = nearestQuote(payload.quoteGrid, targetMu.toDouble(), targetSigma.toDouble())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar(payload.market, submitStatus)
        TabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            AppTab.entries.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = { Text(tab.label, maxLines = 1) },
                )
            }
        }

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (activeTab) {
                AppTab.Trade -> TradeTab(
                    payload = payload,
                    previewQuote = previewQuote,
                    targetMu = targetMu,
                    targetSigma = targetSigma,
                    submitStatus = submitStatus,
                    onMuChange = {
                        targetMu = it
                        submitStatus = null
                    },
                    onSigmaChange = {
                        targetSigma = it
                        submitStatus = null
                    },
                    onPreset = {
                        targetMu = it.targetMuDisplay.toFloat()
                        targetSigma = it.targetSigmaDisplay.toFloat()
                        submitStatus = null
                    },
                    onSubmit = {
                        submitStatus = SubmitStatus(
                            message = "Opening wallet for demo memo approval...",
                            isWorking = true,
                        )
                        coroutineScope.launch {
                            submitStatus = when (val result = WalletSubmitter.submitTradeMemo(walletSender, previewQuote)) {
                                is WalletSubmitResult.Success -> SubmitStatus(
                                    message = "Demo memo submitted. Wallet ${result.walletAddress.take(12)}... signed ${result.signatureHex.take(16)}...",
                                )

                                is WalletSubmitResult.NoWalletFound -> SubmitStatus(
                                    message = "No Mobile Wallet Adapter wallet found on this device.",
                                    isError = true,
                                )

                                is WalletSubmitResult.Failure -> SubmitStatus(
                                    message = result.message,
                                    isError = true,
                                )
                            }
                        }
                    },
                )

                AppTab.Positions -> PositionsTab(previewQuote, submitStatus)
                AppTab.Market -> MarketTab(payload.market)
                AppTab.Maker -> MakerTab(payload.market)
            }
        }
    }
}

@Composable
private fun TopBar(market: DemoMarket, submitStatus: SubmitStatus?) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = market.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge("Devnet")
                    StatusBadge(market.status)
                }
            }
            IconButton(onClick = { }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh market")
            }
            WalletBadge(submitStatus)
        }
    }
}

@Composable
private fun WalletBadge(submitStatus: SubmitStatus?) {
    val color = when {
        submitStatus?.isError == true -> MaterialTheme.colorScheme.error
        submitStatus?.isWorking == true -> MaterialTheme.colorScheme.secondary
        submitStatus != null -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountBalanceWallet,
            contentDescription = "Wallet status",
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (submitStatus == null) "Wallet" else "Wallet",
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatusBadge(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TradeTab(
    payload: DemoPayload,
    previewQuote: DemoPreset,
    targetMu: Float,
    targetSigma: Float,
    submitStatus: SubmitStatus?,
    onMuChange: (Float) -> Unit,
    onSigmaChange: (Float) -> Unit,
    onPreset: (DemoPreset) -> Unit,
    onSubmit: () -> Unit,
) {
    DistributionChartPanel(previewQuote)
    QuoteControls(
        targetMu = targetMu,
        targetSigma = targetSigma,
        onMuChange = onMuChange,
        onSigmaChange = onSigmaChange,
    )
    PresetStrip(payload.presets, previewQuote.id, onPreset)
    QuoteExecutionPanel(previewQuote, submitStatus, onSubmit)
}

@Composable
private fun DistributionChartPanel(quote: DemoPreset) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Distribution edge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Current vs proposed forecast",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "mu ${quote.targetMuDisplay.trimZeros()}  sigma ${quote.targetSigmaDisplay.trimZeros()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        DistributionCanvas(quote.curvePoints)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot("Current", Color(0xFF475569))
            LegendDot("Proposed", Color(0xFF0891B2))
            LegendDot("Taker profit", Color(0xFF10B981))
            LegendDot("Taker loss", Color(0xFFEF4444))
        }
    }
}

@Composable
private fun DistributionCanvas(points: List<DemoCurvePoint>) {
    val currentColor = Color(0xFF475569)
    val proposedColor = Color(0xFF0891B2)
    val profitColor = Color(0xFF10B981)
    val lossColor = Color(0xFFEF4444)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        if (points.size < 2) return@Canvas
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val maxY = max(points.maxOf { it.current }, points.maxOf { it.proposed }).coerceAtLeast(0.0001) * 1.12
        val left = 18f
        val right = size.width - 12f
        val top = 14f
        val bottom = size.height - 18f
        val width = right - left
        val height = bottom - top

        fun xOf(x: Double): Float = left + (((x - minX) / (maxX - minX)) * width).toFloat()
        fun yOf(y: Double): Float = bottom - ((y / maxY) * height).toFloat()

        repeat(4) { index ->
            val y = top + height * index / 3f
            drawLine(Color(0xFFE5E7EB), Offset(left, y), Offset(right, y), 1f)
        }

        val stepWidth = width / (points.size - 1)
        points.forEach { point ->
            val x = xOf(point.x)
            val currentY = yOf(point.current)
            val proposedY = yOf(point.proposed)
            val topY = min(currentY, proposedY)
            val zoneHeight = abs(currentY - proposedY).coerceAtLeast(1f)
            val zoneColor = if (point.edge >= 0.0) profitColor else lossColor
            drawRect(
                color = zoneColor.copy(alpha = 0.10f),
                topLeft = Offset(x - stepWidth / 2f, topY),
                size = Size(stepWidth, zoneHeight),
            )
        }

        points.zipWithNext().forEach { (leftPoint, rightPoint) ->
            drawLine(
                color = currentColor,
                start = Offset(xOf(leftPoint.x), yOf(leftPoint.current)),
                end = Offset(xOf(rightPoint.x), yOf(rightPoint.current)),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = proposedColor,
                start = Offset(xOf(leftPoint.x), yOf(leftPoint.proposed)),
                end = Offset(xOf(rightPoint.x), yOf(rightPoint.proposed)),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
        drawLine(Color(0xFFCBD5E1), Offset(left, bottom), Offset(right, bottom), 1.5f)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(99.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuoteControls(
    targetMu: Float,
    targetSigma: Float,
    onMuChange: (Float) -> Unit,
    onSigmaChange: (Float) -> Unit,
) {
    Panel {
        Text("Trade controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SliderControl("Mean", targetMu, 88f..105f, 1f, onMuChange)
        SliderControl("Sigma", targetSigma, 8.5f..12f, 0.5f, onSigmaChange)
    }
}

@Composable
private fun SliderControl(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onChange((value - step).coerceIn(range.start, range.endInclusive)) }) {
                    Text("-")
                }
                Text("%.1f".format(value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { onChange((value + step).coerceIn(range.start, range.endInclusive)) }) {
                    Text("+")
                }
            }
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun PresetStrip(
    presets: List<DemoPreset>,
    selectedId: String,
    onSelect: (DemoPreset) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.id == selectedId,
                onClick = { onSelect(preset) },
                label = { Text(preset.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun QuoteExecutionPanel(
    quote: DemoPreset,
    submitStatus: SubmitStatus?,
    onSubmit: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    Panel {
        Text("Quote", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        QuoteRow("Required collateral", quote.collateralRequiredDisplay)
        QuoteRow("Maker fee", quote.feePaidDisplay)
        HorizontalDivider()
        QuoteRow("Total debit", quote.totalDebitDisplay, strong = true)
        QuoteRow("Quote expiry slot", quote.quoteExpirySlot.toString())
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = submitStatus?.isWorking != true,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (submitStatus?.isWorking == true) "Waiting on wallet" else "Submit demo memo")
        }
        submitStatus?.let { status ->
            StatusMessage(status)
        }
        OutlinedButton(onClick = { showDetails = !showDetails }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showDetails) "Hide technical details" else "Show technical details")
        }
        if (showDetails) {
            Text(
                text = quote.serializedInstructionHex.take(180) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuoteRow(label: String, value: String, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(
            value.trimZeros(),
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun StatusMessage(status: SubmitStatus) {
    val color = if (status.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (status.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Text(status.message, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PositionsTab(quote: DemoPreset, submitStatus: SubmitStatus?) {
    Panel {
        Text("Position preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "A submitted program trade will appear here once the memo path is replaced with the deployed market instruction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QuoteRow("Pending mean", quote.targetMuDisplay)
        QuoteRow("Pending sigma", quote.targetSigmaDisplay)
        QuoteRow("Pending total debit", quote.totalDebitDisplay, strong = true)
        submitStatus?.let { StatusMessage(it) }
    }
}

@Composable
private fun MarketTab(market: DemoMarket) {
    Panel {
        Text("Market state", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        DenseStats(
            listOf(
                "Mean" to market.currentMuDisplay,
                "Sigma" to market.currentSigmaDisplay,
                "Backing" to market.backingDisplay,
                "Open trades" to "${market.totalTrades}/${market.maxOpenTrades}",
                "Expiry slot" to market.expirySlot.toString(),
                "Status" to market.status,
            )
        )
    }
}

@Composable
private fun MakerTab(market: DemoMarket) {
    Panel {
        Text("Maker view", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        DenseStats(
            listOf(
                "Maker deposit" to market.makerDepositDisplay,
                "Fees earned" to market.makerFeesEarnedDisplay,
                "Open trades" to market.totalTrades.toString(),
                "LP controls" to "Locked after first trade",
            )
        )
    }
}

@Composable
private fun DenseStats(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, value) ->
                    StatCell(label, value, Modifier.weight(1f))
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.trimZeros(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

private fun loadDemoPayload(json: String): DemoPayload {
    val root = JSONObject(json)
    val marketObject = root.getJSONObject("market")
    val presetsArray = root.getJSONArray("presets")
    val quoteGridArray = root.getJSONArray("quote_grid")
    return DemoPayload(
        market = DemoMarket(
            title = marketObject.getString("title"),
            status = marketObject.getString("status"),
            currentMuDisplay = marketObject.getString("current_mu_display"),
            currentSigmaDisplay = marketObject.getString("current_sigma_display"),
            backingDisplay = marketObject.getString("backing_display"),
            makerFeesEarnedDisplay = marketObject.getString("maker_fees_earned_display"),
            makerDepositDisplay = marketObject.getString("maker_deposit_display"),
            totalTrades = marketObject.getLong("total_trades"),
            maxOpenTrades = marketObject.getLong("max_open_trades"),
            expirySlot = marketObject.getLong("expiry_slot"),
        ),
        presets = presetsArray.toPresetList(),
        quoteGrid = quoteGridArray.toPresetList(),
    )
}

private fun JSONArray.toPresetList(): List<DemoPreset> {
    val presets = mutableListOf<DemoPreset>()
    for (index in 0 until length()) {
        val preset = getJSONObject(index)
        presets.add(
            DemoPreset(
                id = preset.getString("id"),
                label = preset.getString("label"),
                targetMuDisplay = preset.getString("target_mu_display"),
                targetSigmaDisplay = preset.getString("target_sigma_display"),
                collateralRequiredDisplay = preset.getString("collateral_required_display"),
                feePaidDisplay = preset.getString("fee_paid_display"),
                totalDebitDisplay = preset.getString("total_debit_display"),
                maxTotalDebitDisplay = preset.getString("max_total_debit_display"),
                quoteExpirySlot = preset.getLong("quote_expiry_slot"),
                serializedInstructionHex = preset.getString("serialized_instruction_hex"),
                curvePoints = preset.getJSONArray("curve_points").toCurvePoints(),
            )
        )
    }
    return presets
}

private fun JSONArray.toCurvePoints(): List<DemoCurvePoint> {
    val points = mutableListOf<DemoCurvePoint>()
    for (index in 0 until length()) {
        val point = getJSONObject(index)
        points.add(
            DemoCurvePoint(
                x = point.getString("x").toDouble(),
                current = point.getString("current").toDouble(),
                proposed = point.getString("proposed").toDouble(),
                edge = point.getString("edge").toDouble(),
            )
        )
    }
    return points
}

private fun nearestQuote(
    quotes: List<DemoPreset>,
    requestedMu: Double,
    requestedSigma: Double,
): DemoPreset {
    return quotes.minByOrNull { quote ->
        val mu = quote.targetMuDisplay.toDoubleOrNull() ?: 0.0
        val sigma = quote.targetSigmaDisplay.toDoubleOrNull() ?: 0.0
        val muDistance = requestedMu - mu
        val sigmaDistance = requestedSigma - sigma
        (muDistance * muDistance) + (sigmaDistance * sigmaDistance)
    } ?: quotes.first()
}

private fun String.trimZeros(): String {
    return if (contains(".") && length > 6) {
        trimEnd('0').trimEnd('.')
    } else {
        this
    }
}
