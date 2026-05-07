package com.solanadistributionmarketdemo.data

import org.json.JSONArray
import org.json.JSONObject

fun loadDemoPayload(json: String): DemoPayload {
    val root = JSONObject(json)
    val marketObject = root.getJSONObject("market")
    val presetsArray = root.getJSONArray("presets")
    val quoteGridArray = root.getJSONArray("quote_grid")
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
        regimeIndexes = root.optJSONArray("regime_indexes")?.toRegimeIndexList().orEmpty(),
        perp = root.optJSONObject("perps")?.toPerpMarket(),
        liveFeed = root.optJSONObject("live_feed")?.toLiveFeed(),
    )
}

private fun JSONObject.toLiveFeed(): DemoLiveFeed = DemoLiveFeed(
    mode = optString("mode"),
    source = optString("source"),
    symbol = optString("symbol"),
    status = optString("status"),
    endpoint = optString("endpoint"),
    chain = optString("chain"),
    feedId = optString("feed_id").ifEmpty { null },
    lastUpdateUnixMs = optLong("last_update_unix_ms"),
    executionMode = optString("execution_mode").ifEmpty { null },
    message = optString("message").ifEmpty { null },
)

private fun JSONArray.toRegimeIndexList(): List<DemoRegimeIndex> {
    val out = mutableListOf<DemoRegimeIndex>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += DemoRegimeIndex(
            id = o.getString("id"),
            symbol = o.getString("symbol"),
            title = o.getString("title"),
            thesis = o.optString("thesis"),
            status = o.optString("status"),
            levelDisplay = o.getString("level_display"),
            previousLevelDisplay = o.optString("previous_level_display"),
            changeDisplay = o.optString("change_display"),
            rebalanceSlot = o.optLong("rebalance_slot"),
            nextRebalanceSlot = o.optLong("next_rebalance_slot"),
            quoteExpirySlot = o.optLong("quote_expiry_slot"),
            constituents = o.optJSONArray("constituents")?.toConstituentList().orEmpty(),
            history = o.optJSONArray("history")?.toRegimeHistory().orEmpty(),
            longQuote = o.getJSONObject("long_quote").toRegimeQuote(),
            shortQuote = o.getJSONObject("short_quote").toRegimeQuote(),
        )
    }
    return out
}

private fun JSONArray.toConstituentList(): List<DemoRegimeConstituent> {
    val out = mutableListOf<DemoRegimeConstituent>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += DemoRegimeConstituent(
            id = o.getString("id"),
            label = o.getString("label"),
            side = o.optString("side"),
            weightBps = o.optInt("weight_bps"),
            probabilityDisplay = o.optString("probability_display"),
            previousProbabilityDisplay = o.optString("previous_probability_display"),
            levelContributionDisplay = o.optString("level_contribution_display"),
            signedPressureDisplay = o.optString("signed_pressure_display"),
            status = o.optString("status"),
            expirySlot = o.optLong("expiry_slot"),
        )
    }
    return out
}

private fun JSONArray.toRegimeHistory(): List<DemoRegimeHistoryPoint> {
    val out = mutableListOf<DemoRegimeHistoryPoint>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += DemoRegimeHistoryPoint(
            slot = o.getLong("slot"),
            levelDisplay = o.getString("level_display"),
        )
    }
    return out
}

private fun JSONObject.toRegimeQuote(): DemoRegimeQuote = DemoRegimeQuote(
    side = optString("side"),
    sizeDisplay = optString("size_display"),
    entryLevelDisplay = optString("entry_level_display"),
    tokenPriceDisplay = optString("token_price_display"),
    collateralRequiredDisplay = optString("collateral_required_display"),
    feePaidDisplay = optString("fee_paid_display"),
    totalDebitDisplay = optString("total_debit_display"),
    memoPayload = optString("memo_payload"),
)

private fun JSONObject.toPerpMarket(): DemoPerpMarket = DemoPerpMarket(
    symbol = getString("symbol"),
    title = getString("title"),
    status = optString("status"),
    slot = optLong("slot"),
    nextFundingSlot = optLong("next_funding_slot"),
    fundingInterval = optLong("funding_interval"),
    markPriceDisplay = getString("mark_price_display"),
    anchorMuDisplay = optString("anchor_mu_display"),
    anchorSigmaDisplay = optString("anchor_sigma_display"),
    ammMuDisplay = optString("amm_mu_display"),
    ammSigmaDisplay = optString("amm_sigma_display"),
    klDisplay = optString("kl_display"),
    spotFundingRateDisplay = optString("spot_funding_rate_display"),
    vaultCashDisplay = optString("vault_cash_display"),
    lpNavDisplay = optString("lp_nav_display"),
    availableLpCashDisplay = optString("available_lp_cash_display"),
    openPositions = optInt("open_positions"),
    totalLpSharesDisplay = optString("total_lp_shares_display"),
    curvePoints = optJSONArray("curve_points")?.toPerpCurve().orEmpty(),
    fundingPath = optJSONArray("funding_path")?.toPerpFundingPath().orEmpty(),
    longQuote = getJSONObject("long_quote").toPerpQuote(),
    shortQuote = getJSONObject("short_quote").toPerpQuote(),
    positions = optJSONArray("positions")?.toPerpPositions().orEmpty(),
)

private fun JSONArray.toPerpCurve(): List<DemoPerpCurvePoint> {
    val out = mutableListOf<DemoPerpCurvePoint>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += DemoPerpCurvePoint(
            x = o.getString("x").toDouble(),
            amm = o.getString("amm").toDouble(),
            anchor = o.getString("anchor").toDouble(),
            edge = o.getString("edge").toDouble(),
        )
    }
    return out
}

private fun JSONArray.toPerpFundingPath(): List<DemoPerpFundingPoint> {
    val out = mutableListOf<DemoPerpFundingPoint>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += DemoPerpFundingPoint(
            slot = o.getLong("slot"),
            ammMuDisplay = o.optString("amm_mu_display"),
            anchorMuDisplay = o.optString("anchor_mu_display"),
            klDisplay = o.optString("kl_display"),
            fundingRateDisplay = o.optString("funding_rate_display"),
        )
    }
    return out
}

private fun JSONObject.toPerpQuote(): DemoPerpQuote = DemoPerpQuote(
    side = optString("side"),
    targetMuDisplay = optString("target_mu_display"),
    targetSigmaDisplay = optString("target_sigma_display"),
    collateralRequiredDisplay = optString("collateral_required_display"),
    feePaidDisplay = optString("fee_paid_display"),
    totalDebitDisplay = optString("total_debit_display"),
    estimatedFundingDisplay = optString("estimated_funding_display"),
    closeMarkDisplay = optString("close_mark_display"),
    memoPayload = optString("memo_payload"),
)

private fun JSONArray.toPerpPositions(): List<DemoPerpPosition> {
    val out = mutableListOf<DemoPerpPosition>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += DemoPerpPosition(
            id = o.getString("id"),
            side = o.optString("side"),
            entryMuDisplay = o.optString("entry_mu_display"),
            collateralDisplay = o.optString("collateral_display"),
            fundingPaidDisplay = o.optString("funding_paid_display"),
            fundingReceivedDisplay = o.optString("funding_received_display"),
            markPayoutDisplay = o.optString("mark_payout_display"),
            status = o.optString("status"),
        )
    }
    return out
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
