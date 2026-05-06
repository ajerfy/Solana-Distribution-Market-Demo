package com.solanadistributionmarketdemo.data

data class DemoPayload(
    val market: DemoMarket,
    val presets: List<DemoPreset>,
    val quoteGrid: List<DemoPreset>,
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

data class SubmitStatus(
    val message: String,
    val isError: Boolean = false,
    val isWorking: Boolean = false,
)

enum class MarketCategory(val label: String, val emoji: String) {
    All("All", "✦"),
    Macro("Macro", "◆"),
    Crypto("Crypto", "◈"),
    Equities("Equities", "◇"),
    Climate("Climate", "◉"),
    Sports("Sports", "◎"),
    Politics("Politics", "◐"),
}

data class MarketListing(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: MarketCategory,
    val unit: String,
    val resolvesAt: String,
    val crowdMu: Double,
    val crowdSigma: Double,
    val muMin: Double,
    val muMax: Double,
    val sigmaMin: Double,
    val sigmaMax: Double,
    val volumeUsd: Double,
    val bettorCount: Int,
    val crowdHistory: List<Double>,
    val isOnChain: Boolean,
    val resolutionSource: String,
    val resolutionRule: String,
)

data class ActivityEvent(
    val marketId: String,
    val anonHandle: String,
    val mu: Double,
    val sigma: Double,
    val stake: Double,
    val ageMinutes: Int,
)

data class BetRecord(
    val id: String,
    val marketId: String,
    val marketTitle: String,
    val mu: Double,
    val sigma: Double,
    val stake: Double,
    val collateral: Double,
    val fee: Double,
    val placedAtMillis: Long,
    val resolved: Boolean,
    val realizedOutcome: Double?,
    val realizedPnl: Double?,
    val txSignatureHex: String?,
    val isOnChain: Boolean,
)

// Phase-1 stubs for the regime-index and perp markets that current main embeds inside
// MainActivity.kt. Phase 4 replaces these with full content-bearing versions and
// extends Payload.kt to parse demo_market.json's `regime_indexes` and `perps` sections.
// The minimal field set here is exactly what WalletSubmitter touches.
data class DemoRegimeIndex(
    val id: String,
    val title: String,
)

data class DemoRegimeQuote(
    val memoPayload: String,
)

data class DemoPerpMarket(
    val symbol: String,
    val title: String,
)

data class DemoPerpQuote(
    val memoPayload: String,
)
