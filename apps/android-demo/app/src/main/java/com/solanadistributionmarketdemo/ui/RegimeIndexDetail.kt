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
import androidx.compose.runtime.LaunchedEffect
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
import com.solanadistributionmarketdemo.data.DemoRegimeIndex
import com.solanadistributionmarketdemo.data.DemoRegimeQuote
import com.solanadistributionmarketdemo.data.MarketListing
import com.solanadistributionmarketdemo.data.SubmitStatus
import kotlinx.coroutines.launch

private enum class RegimeSide(val label: String) { Long("Long"), Short("Short") }

@Composable
fun RegimeIndexDetailBody(
    state: AppState,
    market: MarketListing,
    regime: DemoRegimeIndex,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DemoColors.Background),
        contentPadding = PaddingValues(bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DetailBackBar(onBack = onBack, trailing = "${regime.symbol} · level") }
        item { RegimeHeader(market, regime) }
        item { RegimeHistoryCard(regime, modifier = Modifier.padding(horizontal = 20.dp)) }
        item { RegimeThesisCard(regime, modifier = Modifier.padding(horizontal = 20.dp)) }
        item { RegimeConstituentsCard(regime, modifier = Modifier.padding(horizontal = 20.dp)) }
    }
}

@Composable
fun RegimeBottomCta(
    modifier: Modifier = Modifier,
    onPickSide: (RegimeBetSide) -> Unit,
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
            PrimaryButton(
                label = "Long",
                onClick = { onPickSide(RegimeBetSide.Long) },
                accent = DemoColors.AccentLong,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            PrimaryButton(
                label = "Short",
                onClick = { onPickSide(RegimeBetSide.Short) },
                accent = DemoColors.AccentShort,
            )
        }
    }
}

enum class RegimeBetSide { Long, Short }

@Composable
private fun RegimeHeader(market: MarketListing, regime: DemoRegimeIndex) {
    val change = regime.changeDisplay.toDoubleOrNull() ?: 0.0
    val changeColor = when {
        change > 0 -> DemoColors.AccentLong
        change < 0 -> DemoColors.AccentShort
        else -> DemoColors.TextSecondary
    }
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagPill("THEME", color = DemoColors.AccentLong, filled = true)
            Text(
                "Symbol ${regime.symbol}",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(market.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                regime.levelDisplay.take(8),
                color = DemoColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            val sign = if (change > 0) "+" else ""
            Text(
                "$sign${change.compactDecimal(3)} since last rebalance",
                color = changeColor,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("LAST", regime.previousLevelDisplay.take(7), DemoColors.TextSecondary)
            MetricPill("REBALANCE", "slot ${regime.nextRebalanceSlot}", DemoColors.AccentCrowd)
            MetricPill("LEGS", regime.constituents.size.toString())
        }
    }
}

@Composable
private fun RegimeHistoryCard(regime: DemoRegimeIndex, modifier: Modifier = Modifier) {
    val points = regime.history.mapNotNull { it.levelDisplay.toDoubleOrNull() }
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Level history", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${points.size} pts", color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        if (points.size >= 2) {
            Sparkline(values = points, modifier = Modifier.fillMaxWidth(), color = DemoColors.AccentLong, height = 96.dp, strokeWidth = 2.5f)
        } else {
            Text("Not enough history points yet.", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RegimeThesisCard(regime: DemoRegimeIndex, modifier: Modifier = Modifier) {
    if (regime.thesis.isBlank()) return
    Card(modifier = modifier) {
        SectionLabel("Thesis")
        Spacer(Modifier.height(6.dp))
        Text(regime.thesis, color = DemoColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RegimeConstituentsCard(regime: DemoRegimeIndex, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Basket legs", color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "${regime.constituents.size} markets",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(10.dp))
        regime.constituents.forEach { c ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DemoColors.SurfaceElevated)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.label, color = DemoColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${c.side.uppercase()} · ${(c.weightBps / 100.0).compactDecimal(1)}% weight",
                        color = DemoColors.TextDim,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    c.probabilityDisplay.take(6) + "%",
                    color = if (c.side.equals("long", ignoreCase = true)) DemoColors.AccentLong else DemoColors.AccentShort,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegimeBetSheet(
    state: AppState,
    market: MarketListing,
    regime: DemoRegimeIndex,
    initialSide: RegimeBetSide,
    walletSender: ActivityResultSender,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var side by remember(market.id, initialSide) { mutableStateOf(initialSide) }
    var stake by remember(market.id) { mutableStateOf(50.0) }
    var status by remember(market.id) { mutableStateOf<SubmitStatus?>(null) }

    val quote: DemoRegimeQuote = if (side == RegimeBetSide.Long) regime.longQuote else regime.shortQuote
    val collateral = quote.collateralRequiredDisplay.toDoubleOrNull() ?: 0.0
    val fee = quote.feePaidDisplay.toDoubleOrNull() ?: 0.0
    val maxWin = stake * 2.0
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
                TagPill("THEME", color = DemoColors.AccentLong, filled = true)
                Text(regime.symbol, color = DemoColors.TextDim, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            }
            Text(market.title, color = DemoColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            SideToggle(side, onChange = { side = it; status = null })

            SectionLabel("Stake")
            StakeChipsRow(stake, onPick = { stake = it })

            Column(
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(DemoColors.SurfaceElevated).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatRow("Entry level", quote.entryLevelDisplay.take(8), DemoColors.AccentCrowd)
                StatRow("Token price", quote.tokenPriceDisplay.take(8))
                StatRow("Collateral", collateral.compactDecimal(4))
                StatRow("Fee", fee.compactDecimal(4))
                CompactDivider()
                StatRow("Max win", "+$${maxWin.compactDecimal(2)}", DemoColors.AccentLong, strong = true)
                StatRow("Max loss", "−$${maxLoss.compactDecimal(2)}", DemoColors.AccentShort)
            }

            status?.let { StatusBlockSimple(it) }

            PrimaryButton(
                label = when {
                    status?.isWorking == true -> "Waiting on wallet…"
                    side == RegimeBetSide.Long -> "Sign long memo · devnet"
                    else -> "Sign short memo · devnet"
                },
                onClick = {
                    status = SubmitStatus("Opening wallet for signature…", isWorking = true)
                    scope.launch {
                        when (val result = WalletSubmitter.submitRegimeMemo(walletSender, regime, quote)) {
                            is WalletSubmitResult.Success -> {
                                state.walletAddress.value = result.walletAddress
                                state.addBet(
                                    BetRecord(
                                        id = "regime-${System.currentTimeMillis()}",
                                        marketId = market.id,
                                        marketTitle = market.title,
                                        mu = regime.levelDisplay.toDoubleOrNull() ?: 0.0,
                                        sigma = 0.0,
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
                                state.lastSubmit.value = SubmitStatus("Long/short memo signed · ${result.signatureHex.take(12)}…")
                                onDismiss()
                            }
                            is WalletSubmitResult.NoWalletFound -> status = SubmitStatus("No Solana wallet detected.", isError = true)
                            is WalletSubmitResult.Failure -> status = SubmitStatus(result.message, isError = true)
                        }
                    }
                },
                enabled = status?.isWorking != true,
                accent = if (side == RegimeBetSide.Long) DemoColors.AccentLong else DemoColors.AccentShort,
            )

            Text(
                "${if (side == RegimeBetSide.Long) "Long" else "Short"} the basket of " +
                    regime.constituents.take(3).joinToString(" / ") { it.label } +
                    if (regime.constituents.size > 3) " + ${regime.constituents.size - 3} more" else "",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    LaunchedEffect(Unit) { /* sheet expands */ }
}

@Composable
private fun SideToggle(active: RegimeBetSide, onChange: (RegimeBetSide) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DemoColors.SurfaceElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RegimeBetSide.entries.forEach { side ->
            val selected = side == active
            val accent = if (side == RegimeBetSide.Long) DemoColors.AccentLong else DemoColors.AccentShort
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) accent else Color.Transparent)
                    .clickable { onChange(side) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    side.name,
                    color = if (selected) DemoColors.OnAccent else DemoColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun StakeChipsRow(stake: Double, onPick: (Double) -> Unit) {
    val options = listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { opt ->
            val selected = opt == stake
            Box(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) DemoColors.AccentYou else DemoColors.SurfaceElevated)
                        .clickable { onPick(opt) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$${opt.compactDecimal(0)}",
                        color = if (selected) DemoColors.OnAccent else DemoColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatusBlockSimple(status: SubmitStatus) {
    val color = when {
        status.isError -> DemoColors.AccentShort
        status.isWorking -> DemoColors.AccentWarn
        else -> DemoColors.AccentLong
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Text(status.message, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun DetailBackBar(onBack: () -> Unit, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DemoColors.SurfaceElevated)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", color = DemoColors.TextPrimary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.size(10.dp))
        Text("Markets", color = DemoColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(trailing, color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}
