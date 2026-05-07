package com.solanadistributionmarketdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanadistributionmarketdemo.WalletSubmitResult
import com.solanadistributionmarketdemo.WalletSubmitter
import com.solanadistributionmarketdemo.core.buildContinuousQuotePreview
import com.solanadistributionmarketdemo.core.compactDecimal
import com.solanadistributionmarketdemo.core.simulatedPayoff
import com.solanadistributionmarketdemo.data.AppState
import com.solanadistributionmarketdemo.data.BetRecord
import com.solanadistributionmarketdemo.data.ContinuousQuotePreview
import com.solanadistributionmarketdemo.data.MarketListing
import com.solanadistributionmarketdemo.data.SubmitStatus
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetSheet(
    state: AppState,
    market: MarketListing,
    walletSender: ActivityResultSender,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val (prefillMu, prefillSigma) = remember { BetSheetPrefill.consume() }
    var mu by remember(market.id) { mutableFloatStateOf((prefillMu ?: market.crowdMu).toFloat()) }
    var sigma by remember(market.id) { mutableFloatStateOf((prefillSigma ?: market.crowdSigma).toFloat()) }
    var stake by remember(market.id) { mutableStateOf(50.0) }
    var status by remember(market.id) { mutableStateOf<SubmitStatus?>(null) }
    var showAdvanced by remember(market.id) { mutableStateOf(false) }

    val muRange = market.muMin.toFloat()..market.muMax.toFloat()
    val sigmaRange = market.sigmaMin.toFloat()..market.sigmaMax.toFloat()

    val quote: ContinuousQuotePreview? = if (market.isOnChain) {
        remember(mu, sigma) {
            buildContinuousQuotePreview(
                market = state.payload.market,
                targetMu = mu.toDouble(),
                targetSigma = sigma.toDouble(),
            )
        }
    } else null

    val collateral = quote?.collateralRequired ?: estimateCollateral(market, mu.toDouble(), sigma.toDouble())
    val fee = quote?.feePaid ?: max(stake * 0.003, 0.05)
    val maxWin = stake * 1.85
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (market.isOnChain) TagPill("ON-CHAIN", color = DemoColors.AccentChain, filled = true)
                Text("BET", color = DemoColors.TextDim, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            }
            Text(
                market.title,
                color = DemoColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            DistributionChart(
                crowdMu = market.crowdMu,
                crowdSigma = market.crowdSigma,
                yourMu = mu.toDouble(),
                yourSigma = sigma.toDouble(),
                height = 180.dp,
            )

            ValueRow(
                left = "Average" to "${mu.toDouble().compactDecimal(3)} ${market.unit}",
                right = "Crowd average" to "${market.crowdMu.compactDecimal(2)} ${market.unit}",
            )
            Slider(
                value = mu,
                onValueChange = { mu = it; status = null },
                valueRange = muRange,
                colors = sliderColors(DemoColors.AccentYou),
            )

            // Confidence slider (inverted view of σ over [sigmaMin, sigmaMax]).
            val sigmaMin = market.sigmaMin.toFloat()
            val sigmaMax = market.sigmaMax.toFloat()
            val span = (sigmaMax - sigmaMin).coerceAtLeast(0.0001f)
            val confidence = (1f - (sigma - sigmaMin) / span).coerceIn(0f, 1f)
            val crowdConfidence = (1f - (market.crowdSigma.toFloat() - sigmaMin) / span).coerceIn(0f, 1f)
            ValueRow(
                left = "Confidence" to "${(confidence * 100).toInt()}%",
                right = "Crowd's" to "${(crowdConfidence * 100).toInt()}%",
            )
            Slider(
                value = confidence,
                onValueChange = { c -> sigma = sigmaMin + (1f - c) * span; status = null },
                valueRange = 0f..1f,
                colors = sliderColors(DemoColors.AccentCrowd),
            )

            // Advanced toggle row — exposes the raw σ slider for power users.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    if (showAdvanced) "hide σ" else "advanced · show σ",
                    color = if (showAdvanced) DemoColors.AccentLong else DemoColors.AccentCrowd,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showAdvanced = !showAdvanced },
                )
            }

            if (showAdvanced) {
                ValueRow(
                    left = "Standard deviation" to sigma.toDouble().compactDecimal(3),
                    right = "Crowd's σ" to market.crowdSigma.compactDecimal(2),
                )
                Slider(
                    value = sigma,
                    onValueChange = { sigma = it; status = null },
                    valueRange = sigmaRange,
                    colors = sliderColors(DemoColors.AccentLong),
                )
            }

            SectionLabel("Stake")
            StakeChips(stake = stake, onPick = { stake = it })

            Spacer(Modifier.height(2.dp))

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(DemoColors.SurfaceElevated)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatRow("Stake locked", collateral.compactDecimal(4))
                StatRow("Fee", fee.compactDecimal(4))
                CompactDivider()
                StatRow("Max win", "+$${maxWin.compactDecimal(2)}", accent = DemoColors.AccentLong, strong = true)
                StatRow("Max loss", "−$${maxLoss.compactDecimal(2)}", accent = DemoColors.AccentShort)
            }

            status?.let { StatusBlock(it) }

            PrimaryButton(
                label = when {
                    status?.isWorking == true -> "Waiting on wallet…"
                    market.isOnChain -> "Sign & place bet · devnet"
                    else -> "Place bet · $${stake.compactDecimal(0)}"
                },
                onClick = {
                    if (market.isOnChain && quote != null) {
                        status = SubmitStatus("Opening wallet for signature…", isWorking = true)
                        scope.launch {
                            val result = WalletSubmitter.submitTradeMemo(walletSender, quote)
                            when (result) {
                                is WalletSubmitResult.Success -> {
                                    state.walletAddress.value = result.walletAddress
                                    val record = BetRecord(
                                        id = "bet-${System.currentTimeMillis()}",
                                        marketId = market.id,
                                        marketTitle = market.title,
                                        mu = mu.toDouble(),
                                        sigma = sigma.toDouble(),
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
                                    state.addBet(record)
                                    state.lastSubmit.value = SubmitStatus("Bet placed. Tx ${result.signatureHex.take(12)}…")
                                    onDismiss()
                                }
                                is WalletSubmitResult.NoWalletFound ->
                                    status = SubmitStatus("No Solana wallet detected on this device.", isError = true)
                                is WalletSubmitResult.Failure ->
                                    status = SubmitStatus(result.message, isError = true)
                            }
                        }
                    } else {
                        val record = BetRecord(
                            id = "bet-${System.currentTimeMillis()}",
                            marketId = market.id,
                            marketTitle = market.title,
                            mu = mu.toDouble(),
                            sigma = sigma.toDouble(),
                            stake = stake,
                            collateral = collateral,
                            fee = fee,
                            placedAtMillis = System.currentTimeMillis(),
                            resolved = false,
                            realizedOutcome = null,
                            realizedPnl = null,
                            txSignatureHex = null,
                            isOnChain = false,
                        )
                        state.addBet(record)
                        state.lastSubmit.value = SubmitStatus("Bet placed locally · resolve from Portfolio.")
                        onDismiss()
                    }
                },
                enabled = status?.isWorking != true,
                accent = if (market.isOnChain) DemoColors.AccentYou else DemoColors.AccentLong,
            )

            Text(
                if (market.isOnChain)
                    "Sign on devnet to lock collateral. Quote envelope is the same one the program expects."
                else "Demo market. Bet records locally — use Resolve to draw a synthetic outcome.",
                color = DemoColors.TextDim,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    LaunchedEffect(Unit) {
        // ensure sheet expands to full height
    }
}

@Composable
private fun ValueRow(left: Pair<String, String>, right: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(left.first.uppercase(), color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall)
            Text(left.second, color = DemoColors.AccentYou, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(right.first.uppercase(), color = DemoColors.TextDim, style = MaterialTheme.typography.labelSmall)
            Text(right.second, color = DemoColors.AccentCrowd, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StakeChips(stake: Double, onPick: (Double) -> Unit) {
    val options = listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { opt ->
            Box(modifier = Modifier.weight(1f)) {
                StakeChip(opt, selected = opt == stake, onClick = { onPick(opt) })
            }
        }
    }
}

@Composable
private fun StakeChip(value: Double, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) DemoColors.AccentYou else DemoColors.SurfaceElevated
    val fg = if (selected) DemoColors.OnAccent else DemoColors.TextPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$${value.compactDecimal(0)}",
            color = fg,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusBlock(status: SubmitStatus) {
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
private fun sliderColors(accent: Color) = SliderDefaults.colors(
    thumbColor = accent,
    activeTrackColor = accent,
    inactiveTrackColor = DemoColors.SurfaceMuted,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

private fun estimateCollateral(market: MarketListing, mu: Double, sigma: Double): Double {
    val muDelta = kotlin.math.abs(mu - market.crowdMu)
    val sigmaRatio = (sigma / market.crowdSigma.coerceAtLeast(0.001))
    val base = market.crowdSigma * 0.6
    return base + muDelta * 0.4 + max(0.0, 1.0 - sigmaRatio) * 1.5
}

internal fun simulateRealizedAndPnl(market: MarketListing, bet: BetRecord, seedTweak: Long): Pair<Double, Double> {
    val rng = kotlin.random.Random(bet.id.hashCode().toLong() xor seedTweak)
    // realized drawn from crowd belief, slightly biased
    val z = rng.nextDouble() * 2.0 - 1.0
    val realized = market.crowdMu + z * market.crowdSigma * 1.2
    val pnl = simulatedPayoff(bet.stake, bet.mu, bet.sigma, realized)
    return realized to pnl
}
