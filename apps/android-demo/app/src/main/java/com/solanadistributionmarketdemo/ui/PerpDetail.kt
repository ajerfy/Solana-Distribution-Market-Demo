package com.solanadistributionmarketdemo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanadistributionmarketdemo.WalletSubmitResult
import com.solanadistributionmarketdemo.WalletSubmitter
import com.solanadistributionmarketdemo.core.compactDecimal
import com.solanadistributionmarketdemo.data.AppState
import com.solanadistributionmarketdemo.data.BetRecord
import com.solanadistributionmarketdemo.data.DemoPerpMarket
import com.solanadistributionmarketdemo.data.DemoPerpQuote
import com.solanadistributionmarketdemo.data.MarketListing
import com.solanadistributionmarketdemo.data.SubmitStatus
import kotlinx.coroutines.launch
import kotlin.math.max

enum class PerpBetSide { Long, Short }

@Composable
fun PerpDetailBody(
    state: AppState,
    market: MarketListing,
    perp: DemoPerpMarket,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DemoColors.Background),
        contentPadding = PaddingValues(bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DetailBackBar(onBack = onBack, trailing = "${perp.symbol} · perpetual") }
        item { PerpHeader(market, perp) }
        item { PerpCurveCard(perp, modifier = Modifier.padding(horizontal = 20.dp)) }
        item { PerpFundingCard(perp, modifier = Modifier.padding(horizontal = 20.dp)) }
        item { PerpPositionsCard(perp, modifier = Modifier.padding(horizontal = 20.dp)) }
    }
}

@Composable
fun PerpBottomCta(
    modifier: Modifier = Modifier,
    onPickSide: (PerpBetSide) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, DemoColors.Background)
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            PrimaryButton("Long", onClick = { onPickSide(PerpBetSide.Long) }, accent = DemoColors.AccentLong)
        }
        Box(modifier = Modifier.weight(1f)) {
            PrimaryButton("Short", onClick = { onPickSide(PerpBetSide.Short) }, accent = DemoColors.AccentShort)
        }
    }
}

@Composable
private fun PerpHeader(market: MarketListing, perp: DemoPerpMarket) {
    val funding = perp.spotFundingRateDisplay.toDoubleOrNull() ?: 0.0
    val fundingColor = if (funding > 0) DemoColors.AccentLong else DemoColors.AccentShort
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagPill("PERP", color = DemoColors.AccentWarn, filled = true)
            Text(
                "${perp.symbol} · funding every ${perp.fundingInterval} slots",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(market.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "$${perp.markPriceDisplay.take(7)}",
                color = DemoColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Mark · funding ${if (funding >= 0) "+" else ""}${(funding * 100).compactDecimal(3)}%",
                color = fundingColor,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("ANCHOR μ", perp.anchorMuDisplay.take(6), DemoColors.AccentCrowd)
            MetricPill("AMM μ", perp.ammMuDisplay.take(6), DemoColors.AccentYou)
            MetricPill("σ", perp.ammSigmaDisplay.take(5))
            MetricPill("OPEN", perp.openPositions.toString())
        }
    }
}

@Composable
private fun PerpCurveCard(perp: DemoPerpMarket, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AMM vs anchor curve", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("KL ${perp.klDisplay.take(6)}", color = DemoColors.AccentCrowd, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Long pays funding when AMM is above anchor; short pays when below.",
            color = DemoColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        if (perp.curvePoints.size >= 2) {
            PerpCurveCanvas(perp)
        } else {
            Text("Curve not available.", color = DemoColors.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendChip("AMM", DemoColors.AccentYou)
            LegendChip("Anchor", DemoColors.AccentCrowd)
            LegendChip("Edge", DemoColors.AccentWarn)
        }
    }
}

@Composable
private fun PerpCurveCanvas(perp: DemoPerpMarket) {
    val ammColor = DemoColors.AccentYou
    val anchorColor = DemoColors.AccentCrowd
    val edgeColor = DemoColors.AccentWarn
    val negEdgeColor = DemoColors.AccentShort
    val plotBg = DemoColors.SurfaceElevated
    val gridColor = if (DemoColors.isLight) Color(0x141B2034) else Color(0x10FFFFFF)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(plotBg),
    ) {
        val pad = 14f
        val w = size.width - pad * 2
        val h = size.height - pad * 2 - 8f
        val xs = perp.curvePoints.map { it.x }
        val xMin = xs.min(); val xMax = xs.max()
        val yMax = perp.curvePoints.flatMap { listOf(it.amm, it.anchor) }.max().coerceAtLeast(1e-6)
        val edgeMax = perp.curvePoints.maxOf { kotlin.math.abs(it.edge) }.coerceAtLeast(1e-6)

        repeat(3) { i ->
            val gy = pad + h * (i + 1) / 4f
            drawLine(gridColor, Offset(pad, gy), Offset(pad + w, gy), 1f)
        }
        fun xOf(x: Double): Float = pad + ((x - xMin) / (xMax - xMin)).toFloat() * w
        fun yOf(y: Double): Float = pad + h - (y / yMax).toFloat() * h
        val edgeBaseY = pad + h - 12f

        val ammPath = Path()
        val anchorPath = Path()
        perp.curvePoints.forEachIndexed { i, p ->
            val x = xOf(p.x)
            if (i == 0) {
                ammPath.moveTo(x, yOf(p.amm))
                anchorPath.moveTo(x, yOf(p.anchor))
            } else {
                ammPath.lineTo(x, yOf(p.amm))
                anchorPath.lineTo(x, yOf(p.anchor))
            }
        }
        // Edge bars (signed) along the bottom strip.
        val barW = w / perp.curvePoints.size
        perp.curvePoints.forEachIndexed { i, p ->
            val cx = pad + (i + 0.5f) * barW
            val barHeight = ((p.edge / edgeMax) * 12f).toFloat()
            val top = if (barHeight >= 0) edgeBaseY - barHeight else edgeBaseY
            val height = max(kotlin.math.abs(barHeight), 1f)
            drawRect(
                color = if (p.edge >= 0) edgeColor else negEdgeColor,
                topLeft = Offset(cx - barW * 0.4f, top),
                size = androidx.compose.ui.geometry.Size(barW * 0.8f, height),
            )
        }
        drawPath(anchorPath, color = anchorColor, style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawPath(ammPath, color = ammColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Text(label, color = DemoColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PerpFundingCard(perp: DemoPerpMarket, modifier: Modifier = Modifier) {
    val fundingPoints = perp.fundingPath.mapNotNull { it.fundingRateDisplay.toDoubleOrNull() }
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Funding history", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("next slot ${perp.nextFundingSlot}", color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        if (fundingPoints.size >= 2) {
            Sparkline(values = fundingPoints, modifier = Modifier.fillMaxWidth(), color = DemoColors.AccentWarn, height = 64.dp, strokeWidth = 2f)
        } else {
            Text("Not enough funding history yet.", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PerpPositionsCard(perp: DemoPerpMarket, modifier: Modifier = Modifier) {
    if (perp.positions.isEmpty()) return
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Open positions", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${perp.positions.size}", color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        perp.positions.forEach { pos ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DemoColors.SurfaceElevated)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        pos.side.uppercase(),
                        color = if (pos.side.equals("long", true)) DemoColors.AccentLong else DemoColors.AccentShort,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("entry μ ${pos.entryMuDisplay.take(7)}", color = DemoColors.TextSecondary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(pos.markPayoutDisplay.take(8), color = DemoColors.TextPrimary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(pos.status, color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerpBetSheet(
    state: AppState,
    market: MarketListing,
    perp: DemoPerpMarket,
    initialSide: PerpBetSide,
    walletSender: ActivityResultSender,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var side by remember(market.id, initialSide) { mutableStateOf(initialSide) }
    var stake by remember(market.id) { mutableStateOf(50.0) }
    var status by remember(market.id) { mutableStateOf<SubmitStatus?>(null) }

    val quote: DemoPerpQuote = if (side == PerpBetSide.Long) perp.longQuote else perp.shortQuote
    val collateral = quote.collateralRequiredDisplay.toDoubleOrNull() ?: 0.0
    val fee = quote.feePaidDisplay.toDoubleOrNull() ?: 0.0
    val funding = quote.estimatedFundingDisplay.toDoubleOrNull() ?: 0.0
    val maxWin = stake * 1.6
    val maxLoss = stake

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DemoColors.Surface,
        contentColor = DemoColors.TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(DemoColors.BorderStrong)
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagPill("PERP", color = DemoColors.AccentWarn, filled = true)
                Text(perp.symbol, color = DemoColors.TextDim, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            }
            Text(market.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            PerpSideToggle(side, onChange = { side = it; status = null })

            SectionLabel("Stake")
            StakeChipsRow(stake, onPick = { stake = it })

            Column(
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(DemoColors.SurfaceElevated).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatRow("Target μ", quote.targetMuDisplay.take(7), DemoColors.AccentCrowd)
                StatRow("Target σ", quote.targetSigmaDisplay.take(7))
                StatRow("Mark on close", quote.closeMarkDisplay.take(8))
                StatRow("Collateral", collateral.compactDecimal(4))
                StatRow("Fee", fee.compactDecimal(4))
                StatRow(
                    "Est. funding (one period)",
                    "${if (funding >= 0) "+" else ""}${funding.compactDecimal(5)}",
                    if (funding >= 0) DemoColors.AccentLong else DemoColors.AccentShort,
                )
                CompactDivider()
                StatRow("Max win", "+$${maxWin.compactDecimal(2)}", DemoColors.AccentLong, strong = true)
                StatRow("Max loss", "−$${maxLoss.compactDecimal(2)}", DemoColors.AccentShort)
            }

            status?.let { StatusBlockSimple(it) }

            PrimaryButton(
                label = when {
                    status?.isWorking == true -> "Waiting on wallet…"
                    side == PerpBetSide.Long -> "Sign long memo · devnet"
                    else -> "Sign short memo · devnet"
                },
                onClick = {
                    status = SubmitStatus("Opening wallet for signature…", isWorking = true)
                    scope.launch {
                        when (val result = WalletSubmitter.submitPerpMemo(walletSender, perp, quote)) {
                            is WalletSubmitResult.Success -> {
                                state.walletAddress.value = result.walletAddress
                                state.addBet(
                                    BetRecord(
                                        id = "perp-${System.currentTimeMillis()}",
                                        marketId = market.id,
                                        marketTitle = market.title,
                                        mu = quote.targetMuDisplay.toDoubleOrNull() ?: 0.0,
                                        sigma = quote.targetSigmaDisplay.toDoubleOrNull() ?: 0.0,
                                        stake = stake,
                                        collateral = collateral,
                                        fee = fee,
                                        placedAtMillis = System.currentTimeMillis(),
                                        resolved = false,
                                        realizedOutcome = null,
                                        realizedPnl = null,
                                        txSignatureHex = result.signatureHex,
                                        isOnChain = true,
                                    )
                                )
                                state.lastSubmit.value = SubmitStatus("Perp memo signed · ${result.signatureHex.take(12)}…")
                                onDismiss()
                            }
                            is WalletSubmitResult.NoWalletFound -> status = SubmitStatus("No Solana wallet detected.", isError = true)
                            is WalletSubmitResult.Failure -> status = SubmitStatus(result.message, isError = true)
                        }
                    }
                },
                enabled = status?.isWorking != true,
                accent = if (side == PerpBetSide.Long) DemoColors.AccentLong else DemoColors.AccentShort,
            )

            Text(
                "Funding settles every ${perp.fundingInterval} slots. Long pays when AMM > anchor.",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PerpSideToggle(active: PerpBetSide, onChange: (PerpBetSide) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DemoColors.SurfaceElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PerpBetSide.entries.forEach { s ->
            val selected = s == active
            val accent = if (s == PerpBetSide.Long) DemoColors.AccentLong else DemoColors.AccentShort
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) accent else Color.Transparent)
                    .clickable { onChange(s) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.name,
                    color = if (selected) DemoColors.OnAccent else DemoColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
