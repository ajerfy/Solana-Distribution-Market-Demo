package com.solanadistributionmarketdemo.data

data class DemoPayload(
    val market: DemoMarket,
    val presets: List<DemoPreset>,
    val quoteGrid: List<DemoPreset>,
    val regimeIndexes: List<DemoRegimeIndex> = emptyList(),
    val perp: DemoPerpMarket? = null,
    val liveFeed: DemoLiveFeed? = null,
)

data class DemoLiveFeed(
    val mode: String,
    val source: String,
    val symbol: String,
    val status: String,
    val endpoint: String,
    val chain: String,
    val feedId: String?,
    val lastUpdateUnixMs: Long,
    val executionMode: String?,
    val message: String?,
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
    val subtitle: String? = null,
    val categoryLabel: String? = null,
    val unitLabel: String? = null,
    val resolvesAtLabel: String? = null,
    val volumeUsd: Double? = null,
    val bettorCount: Int? = null,
    val resolutionSourceLabel: String? = null,
    val resolutionRuleText: String? = null,
    val sourceBadge: String? = null,
    val sourceUrl: String? = null,
    val marketSlug: String? = null,
    val outcomeLabel: String? = null,
    val yesPriceDisplay: String? = null,
    val noPriceDisplay: String? = null,
    val bestBidDisplay: String? = null,
    val bestAskDisplay: String? = null,
    val spreadDisplay: String? = null,
    val updatedAtMillis: Long? = null,
    val featuredLive: Boolean = false,
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
    Events("Events", "◆"),
    Weather("Weather", "☁"),
    Crypto("Crypto", "◈"),
    Sports("Sports", "◎"),
    PopCulture("Pop", "♫"),
    Climate("Climate", "◉"),
    Macro("Macro", "▤"),
    Equities("Equities", "◇"),
    Politics("Politics", "◐"),
}

enum class MarketType { Estimation, RegimeIndex, Perp }

enum class MarketTypeFilter(
    val label: String,
    val glyph: String,
    val marketType: MarketType?,
) {
    All("All", "✦", null),
    Estimates("Estimates", "◯", MarketType.Estimation),
    Perps("Perps", "∞", MarketType.Perp),
    RegimeIndexes("Regime indexes", "▦", MarketType.RegimeIndex),
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
    val marketType: MarketType = MarketType.Estimation,
    val sourceBadge: String? = null,
    val sourceUrl: String? = null,
    val isFeaturedLive: Boolean = false,
    val liveEventStats: LiveEventStats? = null,
    val regime: DemoRegimeIndex? = null,
    val perp: DemoPerpMarket? = null,
)

data class LiveEventStats(
    val outcomeLabel: String,
    val yesPrice: Double?,
    val noPrice: Double?,
    val bestBid: Double?,
    val bestAsk: Double?,
    val spread: Double?,
    val updatedAtMillis: Long?,
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

// Regime-index market (basket of yes/no constituents — "themes" in consumer copy).
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

// Perpetual market.
data class DemoPerpMarket(
    val symbol: String,
    val title: String,
    val status: String,
    val slot: Long,
    val nextFundingSlot: Long,
    val fundingInterval: Long,
    val markPriceDisplay: String,
    val anchorMuDisplay: String,
    val anchorSigmaDisplay: String,
    val ammMuDisplay: String,
    val ammSigmaDisplay: String,
    val klDisplay: String,
    val spotFundingRateDisplay: String,
    val vaultCashDisplay: String,
    val lpNavDisplay: String,
    val availableLpCashDisplay: String,
    val openPositions: Int,
    val totalLpSharesDisplay: String,
    val curvePoints: List<DemoPerpCurvePoint>,
    val fundingPath: List<DemoPerpFundingPoint>,
    val longQuote: DemoPerpQuote,
    val shortQuote: DemoPerpQuote,
    val positions: List<DemoPerpPosition>,
)

data class DemoPerpCurvePoint(
    val x: Double,
    val amm: Double,
    val anchor: Double,
    val edge: Double,
)

data class DemoPerpFundingPoint(
    val slot: Long,
    val ammMuDisplay: String,
    val anchorMuDisplay: String,
    val klDisplay: String,
    val fundingRateDisplay: String,
)

data class DemoPerpQuote(
    val side: String,
    val targetMuDisplay: String,
    val targetSigmaDisplay: String,
    val collateralRequiredDisplay: String,
    val feePaidDisplay: String,
    val totalDebitDisplay: String,
    val estimatedFundingDisplay: String,
    val closeMarkDisplay: String,
    val memoPayload: String,
)

data class DemoPerpPosition(
    val id: String,
    val side: String,
    val entryMuDisplay: String,
    val collateralDisplay: String,
    val fundingPaidDisplay: String,
    val fundingReceivedDisplay: String,
    val markPayoutDisplay: String,
    val status: String,
)
