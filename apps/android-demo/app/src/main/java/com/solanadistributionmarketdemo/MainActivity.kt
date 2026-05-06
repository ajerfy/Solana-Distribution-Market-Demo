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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
    val regimeIndexes: List<DemoRegimeIndex>,
)

data class DemoMarket(
    val title: String,
    val status: String,
    val marketIdHex: String,
    val stateVersion: Long,
    val currentMuDisplay: String,
    val currentSigmaDisplay: String,
    val kDisplay: String,
    val backingDisplay: String,
    val takerFeeBps: Int,
    val minTakerFeeDisplay: String,
    val makerFeesEarnedDisplay: String,
    val makerDepositDisplay: String,
    val totalTrades: Long,
    val maxOpenTrades: Long,
    val expirySlot: Long,
    val demoQuoteSlot: Long,
    val demoQuoteExpirySlot: Long,
    val coarseSamples: Int,
    val refineSamples: Int,
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

data class ContinuousQuotePreview(
    val targetMu: Double,
    val targetSigma: Double,
    val collateralRequired: Double,
    val feePaid: Double,
    val totalDebit: Double,
    val maxTotalDebit: Double,
    val quoteExpirySlot: Long,
    val serializedInstructionHex: String,
)

data class DemoRegimeIndex(
    val id: String,
    val symbol: String,
    val title: String,
    val thesis: String,
    val status: String,
    val levelDisplay: String,
    val previousLevelDisplay: String,
    val changeDisplay: String,
    val rebalanceSlot: Long,
    val nextRebalanceSlot: Long,
    val quoteExpirySlot: Long,
    val constituents: List<DemoRegimeConstituent>,
    val history: List<DemoRegimeHistoryPoint>,
    val longQuote: DemoRegimeQuote,
    val shortQuote: DemoRegimeQuote,
)

data class DemoRegimeConstituent(
    val id: String,
    val label: String,
    val side: String,
    val weightBps: Int,
    val probabilityDisplay: String,
    val previousProbabilityDisplay: String,
    val levelContributionDisplay: String,
    val signedPressureDisplay: String,
    val status: String,
    val expirySlot: Long,
)

data class DemoRegimeHistoryPoint(
    val slot: Long,
    val levelDisplay: String,
)

data class DemoRegimeQuote(
    val side: String,
    val sizeDisplay: String,
    val entryLevelDisplay: String,
    val tokenPriceDisplay: String,
    val collateralRequiredDisplay: String,
    val feePaidDisplay: String,
    val totalDebitDisplay: String,
    val memoPayload: String,
)

private enum class AppTab(val label: String) {
    Trade("Trade"),
    Indexes("Indexes"),
    Positions("Positions"),
    Market("Market"),
    Maker("Maker"),
}

private enum class RegimeTradeSide(val label: String) {
    Long("Long"),
    Short("Short"),
}

@Composable
private fun TradeAppScreen(
    payload: DemoPayload,
    walletSender: ActivityResultSender,
) {
    var targetMu by remember { mutableStateOf(payload.presets.first().targetMuDisplay.toFloat()) }
    var targetSigma by remember { mutableStateOf(payload.presets.first().targetSigmaDisplay.toFloat()) }
    var activeTab by remember { mutableStateOf(AppTab.Trade) }
    var selectedRegimeId by remember { mutableStateOf(payload.regimeIndexes.first().id) }
    var selectedRegimeSide by remember { mutableStateOf(RegimeTradeSide.Long) }
    var submitStatus by remember { mutableStateOf<SubmitStatus?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val selectedRegime = payload.regimeIndexes.firstOrNull { it.id == selectedRegimeId }
        ?: payload.regimeIndexes.first()
    val previewQuote = nearestQuote(payload.quoteGrid, targetMu.toDouble(), targetSigma.toDouble())
    val continuousQuote = remember(payload.quoteGrid, targetMu, targetSigma, previewQuote) {
        buildContinuousQuotePreview(
            market = payload.market,
            targetMu = targetMu.toDouble(),
            targetSigma = targetSigma.toDouble(),
        )
    }
    val continuousCurvePoints = remember(
        payload.market.currentMuDisplay,
        payload.market.currentSigmaDisplay,
        targetMu,
        targetSigma,
    ) {
        buildContinuousCurvePoints(
            currentMu = payload.market.currentMuDisplay.toDouble(),
            currentSigma = payload.market.currentSigmaDisplay.toDouble(),
            proposedMu = targetMu.toDouble(),
            proposedSigma = targetSigma.toDouble(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar(payload.market, submitStatus)
        PrimaryNavigation(activeTab) { activeTab = it }

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (activeTab) {
                AppTab.Trade -> TradeTab(
                    payload = payload,
                    previewQuote = previewQuote,
                    continuousQuote = continuousQuote,
                    curvePoints = continuousCurvePoints,
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
                            submitStatus = when (val result = WalletSubmitter.submitTradeMemo(walletSender, continuousQuote)) {
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

                AppTab.Indexes -> RegimeIndexesTab(
                    indexes = payload.regimeIndexes,
                    selectedIndex = selectedRegime,
                    selectedSide = selectedRegimeSide,
                    submitStatus = submitStatus,
                    onSelectIndex = {
                        selectedRegimeId = it.id
                        submitStatus = null
                    },
                    onSideChange = {
                        selectedRegimeSide = it
                        submitStatus = null
                    },
                    onSubmit = {
                        submitStatus = SubmitStatus(
                            message = "Opening wallet for regime memo approval...",
                            isWorking = true,
                        )
                        coroutineScope.launch {
                            val quote = selectedRegime.quoteFor(selectedRegimeSide)
                            submitStatus = when (
                                val result = WalletSubmitter.submitRegimeMemo(
                                    walletSender,
                                    selectedRegime,
                                    quote,
                                )
                            ) {
                                is WalletSubmitResult.Success -> SubmitStatus(
                                    message = "Regime memo submitted. Wallet ${result.walletAddress.take(12)}... signed ${result.signatureHex.take(16)}...",
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

                AppTab.Positions -> PositionsTab(continuousQuote, submitStatus)
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
private fun PrimaryNavigation(
    activeTab: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { onSelect(tab) },
                    label = {
                        Text(
                            text = tab.label,
                            maxLines = 1,
                            fontWeight = if (activeTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
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
    continuousQuote: ContinuousQuotePreview,
    curvePoints: List<DemoCurvePoint>,
    targetMu: Float,
    targetSigma: Float,
    submitStatus: SubmitStatus?,
    onMuChange: (Float) -> Unit,
    onSigmaChange: (Float) -> Unit,
    onPreset: (DemoPreset) -> Unit,
    onSubmit: () -> Unit,
) {
    DistributionChartPanel(
        targetMu = targetMu,
        targetSigma = targetSigma,
        curvePoints = curvePoints,
    )
    QuoteControls(
        targetMu = targetMu,
        targetSigma = targetSigma,
        onMuChange = onMuChange,
        onSigmaChange = onSigmaChange,
    )
    PresetStrip(payload.presets, previewQuote.id, onPreset)
    QuoteExecutionPanel(continuousQuote, submitStatus, onSubmit)
}

@Composable
private fun RegimeIndexesTab(
    indexes: List<DemoRegimeIndex>,
    selectedIndex: DemoRegimeIndex,
    selectedSide: RegimeTradeSide,
    submitStatus: SubmitStatus?,
    onSelectIndex: (DemoRegimeIndex) -> Unit,
    onSideChange: (RegimeTradeSide) -> Unit,
    onSubmit: () -> Unit,
) {
    RegimeSelector(indexes, selectedIndex.id, onSelectIndex)
    RegimeIndexPanel(selectedIndex)
    RegimeHistoryPanel(selectedIndex)
    RegimeConstituentsPanel(selectedIndex)
    RegimeTradePanel(
        selectedIndex = selectedIndex,
        selectedSide = selectedSide,
        submitStatus = submitStatus,
        onSideChange = onSideChange,
        onSubmit = onSubmit,
    )
}

@Composable
private fun RegimeSelector(
    indexes: List<DemoRegimeIndex>,
    selectedId: String,
    onSelect: (DemoRegimeIndex) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        indexes.forEach { index ->
            FilterChip(
                selected = index.id == selectedId,
                onClick = { onSelect(index) },
                label = { Text(index.symbol, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun RegimeIndexPanel(index: DemoRegimeIndex) {
    val change = index.changeDisplay.toDoubleOrNull() ?: 0.0
    val changeColor = if (change >= 0.0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(index.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    index.thesis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    index.symbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(index.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCell("Index level", index.levelDisplay.trimZeros(), Modifier.weight(1f))
            StatCell("24h change", signedDisplay(index.changeDisplay), Modifier.weight(1f))
        }
        Text(
            "Next rebalance slot ${index.nextRebalanceSlot}",
            style = MaterialTheme.typography.bodySmall,
            color = changeColor,
        )
    }
}

@Composable
private fun RegimeHistoryPanel(index: DemoRegimeIndex) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Regime level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "rolls at ${index.nextRebalanceSlot}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RegimeHistoryCanvas(index.history)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot("Index", MaterialTheme.colorScheme.primary)
            LegendDot("Rebalance path", MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun RegimeHistoryCanvas(points: List<DemoRegimeHistoryPoint>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        if (points.size < 2) return@Canvas
        val levels = points.map { it.levelDisplay.toDouble() }
        val minY = (levels.minOrNull() ?: 0.0).minus(8.0).coerceAtLeast(0.0)
        val maxY = (levels.maxOrNull() ?: 100.0).plus(8.0).coerceAtMost(100.0)
        val ySpan = (maxY - minY).coerceAtLeast(1.0)
        val left = 16f
        val right = size.width - 12f
        val top = 12f
        val bottom = size.height - 16f
        val width = right - left
        val height = bottom - top

        fun xOf(index: Int): Float = left + width * index / (points.size - 1).coerceAtLeast(1)
        fun yOf(level: Double): Float = bottom - (((level - minY) / ySpan) * height).toFloat()

        repeat(3) { gridIndex ->
            val y = top + height * gridIndex / 2f
            drawLine(Color(0xFFE5E7EB), Offset(left, y), Offset(right, y), 1f)
        }
        points.zipWithNext().forEachIndexed { index, (leftPoint, rightPoint) ->
            drawLine(
                color = Color(0xFF0891B2),
                start = Offset(xOf(index), yOf(leftPoint.levelDisplay.toDouble())),
                end = Offset(xOf(index + 1), yOf(rightPoint.levelDisplay.toDouble())),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
        points.forEachIndexed { index, point ->
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = 4.5f,
                center = Offset(xOf(index), yOf(point.levelDisplay.toDouble())),
            )
        }
    }
}

@Composable
private fun RegimeConstituentsPanel(index: DemoRegimeIndex) {
    Panel {
        Text("Constituents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        index.constituents.forEachIndexed { rowIndex, constituent ->
            if (rowIndex > 0) {
                HorizontalDivider()
            }
            RegimeConstituentRow(constituent)
        }
    }
}

@Composable
private fun RegimeConstituentRow(constituent: DemoRegimeConstituent) {
    val sideColor = if (constituent.side == "Short") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(constituent.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${constituent.side} ${constituent.weightBps.toWeightPercent()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = sideColor,
                )
            }
            Text(
                constituent.probabilityDisplay.trimZeros(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DenseStats(
            listOf(
                "Contribution" to constituent.levelContributionDisplay,
                "Pressure" to signedDisplay(constituent.signedPressureDisplay),
                "Expiry" to constituent.expirySlot.toString(),
                "Status" to constituent.status,
            )
        )
    }
}

@Composable
private fun RegimeTradePanel(
    selectedIndex: DemoRegimeIndex,
    selectedSide: RegimeTradeSide,
    submitStatus: SubmitStatus?,
    onSideChange: (RegimeTradeSide) -> Unit,
    onSubmit: () -> Unit,
) {
    val quote = selectedIndex.quoteFor(selectedSide)
    var showDetails by remember { mutableStateOf(false) }
    Panel {
        Text("Regime token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RegimeTradeSide.entries.forEach { side ->
                FilterChip(
                    selected = selectedSide == side,
                    onClick = { onSideChange(side) },
                    label = { Text(side.label) },
                )
            }
        }
        QuoteRow("Entry level", quote.entryLevelDisplay)
        QuoteRow("Token price", quote.tokenPriceDisplay)
        QuoteRow("Size", quote.sizeDisplay)
        QuoteRow("Required collateral", quote.collateralRequiredDisplay)
        QuoteRow("Maker fee", quote.feePaidDisplay)
        HorizontalDivider()
        QuoteRow("Total debit", quote.totalDebitDisplay, strong = true)
        QuoteRow("Quote expiry slot", selectedIndex.quoteExpirySlot.toString())
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = submitStatus?.isWorking != true,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (submitStatus?.isWorking == true) "Waiting on wallet" else "Submit regime memo")
        }
        Text(
            text = "This is a synthetic regime-token intent built from the SDK index payload.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        submitStatus?.let { StatusMessage(it) }
        OutlinedButton(onClick = { showDetails = !showDetails }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showDetails) "Hide memo details" else "Show memo details")
        }
        if (showDetails) {
            Text(
                text = quote.memoPayload,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DistributionChartPanel(
    targetMu: Float,
    targetSigma: Float,
    curvePoints: List<DemoCurvePoint>,
) {
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
                "mu ${"%.1f".format(targetMu).trimZeros()}  sigma ${"%.1f".format(targetSigma).trimZeros()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        DistributionCanvas(curvePoints)
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
    quote: ContinuousQuotePreview,
    submitStatus: SubmitStatus?,
    onSubmit: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    Panel {
        Text("Quote", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        QuoteRow("Required collateral", quote.collateralRequired.formattedDecimal())
        QuoteRow("Maker fee", quote.feePaid.formattedDecimal())
        HorizontalDivider()
        QuoteRow("Total debit", quote.totalDebit.formattedDecimal(), strong = true)
        QuoteRow("Max total debit", quote.maxTotalDebit.formattedDecimal())
        QuoteRow("Quote expiry slot", quote.quoteExpirySlot.toString())
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = submitStatus?.isWorking != true,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (submitStatus?.isWorking == true) "Waiting on wallet" else "Submit demo memo")
        }
        Text(
            text = "This preview and the submitted demo payload are built from the same live Normal quote.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun PositionsTab(quote: ContinuousQuotePreview, submitStatus: SubmitStatus?) {
    Panel {
        Text("Position preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "A submitted program trade will appear here once the memo path is replaced with the deployed market instruction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QuoteRow("Pending mean", quote.targetMu.formattedDecimal())
        QuoteRow("Pending sigma", quote.targetSigma.formattedDecimal())
        QuoteRow("Pending total debit", quote.totalDebit.formattedDecimal(), strong = true)
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
    val regimeIndexesArray = root.getJSONArray("regime_indexes")
    return DemoPayload(
        market = DemoMarket(
            title = marketObject.getString("title"),
            status = marketObject.getString("status"),
            marketIdHex = marketObject.getString("market_id_hex"),
            stateVersion = marketObject.getLong("state_version"),
            currentMuDisplay = marketObject.getString("current_mu_display"),
            currentSigmaDisplay = marketObject.getString("current_sigma_display"),
            kDisplay = marketObject.getString("k_display"),
            backingDisplay = marketObject.getString("backing_display"),
            takerFeeBps = marketObject.getInt("taker_fee_bps"),
            minTakerFeeDisplay = marketObject.getString("min_taker_fee_display"),
            makerFeesEarnedDisplay = marketObject.getString("maker_fees_earned_display"),
            makerDepositDisplay = marketObject.getString("maker_deposit_display"),
            totalTrades = marketObject.getLong("total_trades"),
            maxOpenTrades = marketObject.getLong("max_open_trades"),
            expirySlot = marketObject.getLong("expiry_slot"),
            demoQuoteSlot = marketObject.getLong("demo_quote_slot"),
            demoQuoteExpirySlot = marketObject.getLong("demo_quote_expiry_slot"),
            coarseSamples = marketObject.getInt("coarse_samples"),
            refineSamples = marketObject.getInt("refine_samples"),
        ),
        presets = presetsArray.toPresetList(),
        quoteGrid = quoteGridArray.toPresetList(),
        regimeIndexes = regimeIndexesArray.toRegimeIndexList(),
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

private fun JSONArray.toRegimeIndexList(): List<DemoRegimeIndex> {
    val indexes = mutableListOf<DemoRegimeIndex>()
    for (index in 0 until length()) {
        val item = getJSONObject(index)
        indexes.add(
            DemoRegimeIndex(
                id = item.getString("id"),
                symbol = item.getString("symbol"),
                title = item.getString("title"),
                thesis = item.getString("thesis"),
                status = item.getString("status"),
                levelDisplay = item.getString("level_display"),
                previousLevelDisplay = item.getString("previous_level_display"),
                changeDisplay = item.getString("change_display"),
                rebalanceSlot = item.getLong("rebalance_slot"),
                nextRebalanceSlot = item.getLong("next_rebalance_slot"),
                quoteExpirySlot = item.getLong("quote_expiry_slot"),
                constituents = item.getJSONArray("constituents").toRegimeConstituents(),
                history = item.getJSONArray("history").toRegimeHistory(),
                longQuote = item.getJSONObject("long_quote").toRegimeQuote(),
                shortQuote = item.getJSONObject("short_quote").toRegimeQuote(),
            )
        )
    }
    return indexes
}

private fun JSONArray.toRegimeConstituents(): List<DemoRegimeConstituent> {
    val constituents = mutableListOf<DemoRegimeConstituent>()
    for (index in 0 until length()) {
        val item = getJSONObject(index)
        constituents.add(
            DemoRegimeConstituent(
                id = item.getString("id"),
                label = item.getString("label"),
                side = item.getString("side"),
                weightBps = item.getInt("weight_bps"),
                probabilityDisplay = item.getString("probability_display"),
                previousProbabilityDisplay = item.getString("previous_probability_display"),
                levelContributionDisplay = item.getString("level_contribution_display"),
                signedPressureDisplay = item.getString("signed_pressure_display"),
                status = item.getString("status"),
                expirySlot = item.getLong("expiry_slot"),
            )
        )
    }
    return constituents
}

private fun JSONArray.toRegimeHistory(): List<DemoRegimeHistoryPoint> {
    val history = mutableListOf<DemoRegimeHistoryPoint>()
    for (index in 0 until length()) {
        val item = getJSONObject(index)
        history.add(
            DemoRegimeHistoryPoint(
                slot = item.getLong("slot"),
                levelDisplay = item.getString("level_display"),
            )
        )
    }
    return history
}

private fun JSONObject.toRegimeQuote(): DemoRegimeQuote {
    return DemoRegimeQuote(
        side = getString("side"),
        sizeDisplay = getString("size_display"),
        entryLevelDisplay = getString("entry_level_display"),
        tokenPriceDisplay = getString("token_price_display"),
        collateralRequiredDisplay = getString("collateral_required_display"),
        feePaidDisplay = getString("fee_paid_display"),
        totalDebitDisplay = getString("total_debit_display"),
        memoPayload = getString("memo_payload"),
    )
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

private fun buildContinuousQuotePreview(
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

private fun computeCollateralRequired(
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
        currentMu,
        currentSigma,
        proposedMu,
        proposedSigma,
        k,
        lowerBound,
        upperBound,
        coarseSamples,
    )
    val coarseStep = (upperBound - lowerBound) / coarseSamples.coerceAtLeast(1)
    val refineLower = max(lowerBound, coarse.first - coarseStep)
    val refineUpper = min(upperBound, coarse.first + coarseStep)
    val refine = maximumDirectionalLossWithArgmax(
        currentMu,
        currentSigma,
        proposedMu,
        proposedSigma,
        k,
        refineLower,
        refineUpper,
        refineSamples,
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

private fun packFixed(value: Double): List<Byte> {
    val scaled = BigInteger.valueOf((value * FIXED_SCALE).let { kotlin.math.round(it).toLong() })
    return packI128LittleEndian(scaled)
}

private fun packU64(value: Long): List<Byte> =
    List(8) { index -> ((value ushr (index * 8)) and 0xff).toByte() }

private fun packU32(value: Long): List<Byte> =
    List(4) { index -> ((value ushr (index * 8)) and 0xff).toByte() }

private fun packI128LittleEndian(value: BigInteger): List<Byte> {
    val signByte: Byte = if (value.signum() < 0) 0xff.toByte() else 0x00
    val bigEndian = value.toByteArray()
    val padded = MutableList(16) { signByte }
    val copyStart = max(0, bigEndian.size - 16)
    val copyLength = min(16, bigEndian.size)
    for (index in 0 until copyLength) {
        padded[16 - copyLength + index] = bigEndian[copyStart + index]
    }
    return padded.reversed()
}

private fun decodeHex(value: String): ByteArray {
    val bytes = ByteArray(value.length / 2)
    for (index in bytes.indices) {
        val high = value[index * 2].digitToInt(16)
        val low = value[index * 2 + 1].digitToInt(16)
        bytes[index] = ((high shl 4) or low).toByte()
    }
    return bytes
}

private const val FIXED_SCALE = 1_000_000_000.0
private const val FIXED_EPSILON = 0.000000001

fun Double.formattedDecimal(): String = "%.9f".format(this)

private fun DemoRegimeIndex.quoteFor(side: RegimeTradeSide): DemoRegimeQuote {
    return when (side) {
        RegimeTradeSide.Long -> longQuote
        RegimeTradeSide.Short -> shortQuote
    }
}

private fun Int.toWeightPercent(): String = "%.1f%%".format(this / 100.0).trimZeros()

private fun signedDisplay(value: String): String {
    val number = value.toDoubleOrNull() ?: return value
    val formatted = kotlin.math.abs(number).formattedDecimal().trimZeros()
    return if (number >= 0.0) "+$formatted" else "-$formatted"
}

private fun buildContinuousCurvePoints(
    currentMu: Double,
    currentSigma: Double,
    proposedMu: Double,
    proposedSigma: Double,
    samples: Int = 61,
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
        DemoCurvePoint(
            x = x,
            current = current,
            proposed = proposed,
            edge = proposed - current,
        )
    }
}

private fun normalPdf(x: Double, mu: Double, sigma: Double): Double {
    val safeSigma = sigma.coerceAtLeast(0.0001)
    val coefficient = 1.0 / (safeSigma * kotlin.math.sqrt(2.0 * Math.PI))
    val exponent = -((x - mu) * (x - mu)) / (2.0 * safeSigma * safeSigma)
    return coefficient * kotlin.math.exp(exponent)
}

private fun String.trimZeros(): String {
    return if (contains(".") && length > 6) {
        trimEnd('0').trimEnd('.')
    } else {
        this
    }
}
