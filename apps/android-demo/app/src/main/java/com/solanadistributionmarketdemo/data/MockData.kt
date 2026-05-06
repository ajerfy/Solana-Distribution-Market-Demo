package com.solanadistributionmarketdemo.data

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object MockMarkets {
    fun build(onChain: DemoMarket): List<MarketListing> {
        val onChainListing = MarketListing(
            id = "onchain-eth-return",
            title = onChain.title,
            subtitle = "Live devnet market · single seeded pool",
            category = MarketCategory.Crypto,
            unit = "%",
            resolvesAt = "Slot ${onChain.expirySlot}",
            crowdMu = onChain.currentMuDisplay.toDouble(),
            crowdSigma = onChain.currentSigmaDisplay.toDouble(),
            muMin = onChain.currentMuDisplay.toDouble() - onChain.currentSigmaDisplay.toDouble() * 2.5,
            muMax = onChain.currentMuDisplay.toDouble() + onChain.currentSigmaDisplay.toDouble() * 2.5,
            sigmaMin = onChain.currentSigmaDisplay.toDouble() * 0.5,
            sigmaMax = onChain.currentSigmaDisplay.toDouble() * 2.0,
            volumeUsd = onChain.backingDisplay.toDouble() * 1_000.0,
            bettorCount = onChain.totalTrades.toInt().coerceAtLeast(1),
            crowdHistory = synthHistory(onChain.currentMuDisplay.toDouble(), onChain.currentSigmaDisplay.toDouble() * 0.15, seed = 11),
            isOnChain = true,
            resolutionSource = "Pyth ETH/USD · settlement slot ${onChain.expirySlot}",
            resolutionRule = "Realized ETH return at expiry slot, scaled to display units.",
        )

        val mocks = listOf(
            mock(
                id = "macro-cpi", category = MarketCategory.Macro,
                title = "US CPI YoY · May 2026",
                subtitle = "BLS print on Jun 11, 8:30 ET",
                unit = "%", crowdMu = 3.2, crowdSigma = 0.35,
                muMin = 1.5, muMax = 5.0, sigmaMin = 0.1, sigmaMax = 1.2,
                volumeUsd = 184_320.0, bettorCount = 412,
                resolvesAt = "Jun 11, 2026", source = "BLS CPI release",
                rule = "Year-over-year all-items CPI, headline number from BLS press release.",
                seed = 1,
            ),
            mock(
                id = "macro-fed", category = MarketCategory.Macro,
                title = "Fed funds upper bound · Jul 2026 FOMC",
                subtitle = "Decision Jul 29, 2026",
                unit = "%", crowdMu = 4.25, crowdSigma = 0.18,
                muMin = 3.5, muMax = 5.25, sigmaMin = 0.05, sigmaMax = 0.6,
                volumeUsd = 612_400.0, bettorCount = 1_204,
                resolvesAt = "Jul 29, 2026", source = "FOMC statement",
                rule = "Upper bound of the target range as stated in the FOMC press release.",
                seed = 2,
            ),
            mock(
                id = "crypto-btc", category = MarketCategory.Crypto,
                title = "BTC close · May 31, 2026",
                subtitle = "Coinbase BTC-USD daily close",
                unit = "k$", crowdMu = 92.5, crowdSigma = 6.8,
                muMin = 60.0, muMax = 130.0, sigmaMin = 2.0, sigmaMax = 18.0,
                volumeUsd = 1_240_000.0, bettorCount = 3_870,
                resolvesAt = "May 31, 2026", source = "Coinbase BTC-USD",
                rule = "UTC daily close on the resolution date.",
                seed = 3,
            ),
            mock(
                id = "crypto-eth-supply", category = MarketCategory.Crypto,
                title = "ETH net issuance · Q2 2026",
                subtitle = "ultrasound.money tally",
                unit = "k ETH", crowdMu = -120.0, crowdSigma = 80.0,
                muMin = -400.0, muMax = 200.0, sigmaMin = 20.0, sigmaMax = 200.0,
                volumeUsd = 88_000.0, bettorCount = 192,
                resolvesAt = "Jul 1, 2026", source = "ultrasound.money",
                rule = "Net ETH supply change over Q2 2026 from ultrasound.money's daily series.",
                seed = 4,
            ),
            mock(
                id = "eq-spx", category = MarketCategory.Equities,
                title = "S&P 500 close · year-end 2026",
                subtitle = "SPX last print, Dec 31",
                unit = "pts", crowdMu = 5_840.0, crowdSigma = 320.0,
                muMin = 4_500.0, muMax = 7_500.0, sigmaMin = 80.0, sigmaMax = 800.0,
                volumeUsd = 2_410_000.0, bettorCount = 5_412,
                resolvesAt = "Dec 31, 2026", source = "CBOE SPX index",
                rule = "Final 2026 calendar-year close of the SPX index.",
                seed = 5,
            ),
            mock(
                id = "climate-temp", category = MarketCategory.Climate,
                title = "Global temp anomaly · 2026",
                subtitle = "NASA GISTEMP annual",
                unit = "°C", crowdMu = 1.42, crowdSigma = 0.11,
                muMin = 0.9, muMax = 2.0, sigmaMin = 0.04, sigmaMax = 0.4,
                volumeUsd = 56_300.0, bettorCount = 88,
                resolvesAt = "Jan 15, 2027", source = "NASA GISTEMP v4",
                rule = "Annual mean land+ocean anomaly vs 1951–1980 base period.",
                seed = 6,
            ),
            mock(
                id = "climate-arctic", category = MarketCategory.Climate,
                title = "Arctic sea ice min · 2026",
                subtitle = "NSIDC September minimum",
                unit = "M km²", crowdMu = 4.35, crowdSigma = 0.42,
                muMin = 3.0, muMax = 6.0, sigmaMin = 0.1, sigmaMax = 1.0,
                volumeUsd = 22_500.0, bettorCount = 47,
                resolvesAt = "Sep 30, 2026", source = "NSIDC",
                rule = "5-day average minimum extent reported by NSIDC for the 2026 melt season.",
                seed = 7,
            ),
            mock(
                id = "sport-nba", category = MarketCategory.Sports,
                title = "NBA Finals winner margin · 2026",
                subtitle = "Series differential, +/- games",
                unit = "games", crowdMu = 1.6, crowdSigma = 1.4,
                muMin = -3.0, muMax = 3.0, sigmaMin = 0.4, sigmaMax = 2.5,
                volumeUsd = 312_000.0, bettorCount = 921,
                resolvesAt = "Jun 22, 2026", source = "Official NBA",
                rule = "Series margin (winner games minus loser games) at series end.",
                seed = 8,
            ),
            mock(
                id = "sport-elo", category = MarketCategory.Sports,
                title = "Magnus Carlsen Elo · year-end",
                subtitle = "FIDE classical rating Dec 2026",
                unit = "Elo", crowdMu = 2_842.0, crowdSigma = 14.0,
                muMin = 2_780.0, muMax = 2_900.0, sigmaMin = 4.0, sigmaMax = 35.0,
                volumeUsd = 14_700.0, bettorCount = 36,
                resolvesAt = "Dec 1, 2026", source = "FIDE",
                rule = "Published FIDE classical rating in the December 2026 list.",
                seed = 9,
            ),
            mock(
                id = "pol-approval", category = MarketCategory.Politics,
                title = "POTUS approval · Jul 4, 2026",
                subtitle = "538 average on Independence Day",
                unit = "%", crowdMu = 41.2, crowdSigma = 2.8,
                muMin = 30.0, muMax = 55.0, sigmaMin = 1.0, sigmaMax = 6.0,
                volumeUsd = 504_000.0, bettorCount = 1_840,
                resolvesAt = "Jul 4, 2026", source = "FiveThirtyEight",
                rule = "538 polling average for presidential approval on the resolution date.",
                seed = 10,
            ),
            mock(
                id = "macro-unemp", category = MarketCategory.Macro,
                title = "US unemployment rate · Jun 2026",
                subtitle = "BLS Employment Situation",
                unit = "%", crowdMu = 4.1, crowdSigma = 0.22,
                muMin = 3.0, muMax = 6.0, sigmaMin = 0.05, sigmaMax = 0.7,
                volumeUsd = 96_000.0, bettorCount = 244,
                resolvesAt = "Jul 3, 2026", source = "BLS",
                rule = "U-3 headline unemployment rate from the BLS Employment Situation release.",
                seed = 12,
            ),
        )

        return listOf(onChainListing) + mocks
    }

    private fun mock(
        id: String, category: MarketCategory, title: String, subtitle: String,
        unit: String, crowdMu: Double, crowdSigma: Double,
        muMin: Double, muMax: Double, sigmaMin: Double, sigmaMax: Double,
        volumeUsd: Double, bettorCount: Int,
        resolvesAt: String, source: String, rule: String, seed: Int,
    ): MarketListing = MarketListing(
        id = id, title = title, subtitle = subtitle, category = category,
        unit = unit, resolvesAt = resolvesAt,
        crowdMu = crowdMu, crowdSigma = crowdSigma,
        muMin = muMin, muMax = muMax, sigmaMin = sigmaMin, sigmaMax = sigmaMax,
        volumeUsd = volumeUsd, bettorCount = bettorCount,
        crowdHistory = synthHistory(crowdMu, crowdSigma * 0.18, seed = seed),
        isOnChain = false,
        resolutionSource = source, resolutionRule = rule,
    )

    private fun synthHistory(center: Double, jitter: Double, seed: Int, points: Int = 48): List<Double> {
        val rng = Random(seed)
        val drift = (rng.nextDouble() - 0.5) * jitter * 0.4
        return List(points) { i ->
            val t = i.toDouble() / (points - 1)
            val wave = sin(t * 4.0 + seed) * jitter * 0.6
            val noise = (rng.nextDouble() - 0.5) * jitter
            center + drift * t * (points - 1) + wave + noise
        }
    }
}

object MockActivity {
    private val handles = listOf(
        "0xq…7ba", "anon_4291", "0x4f…12c", "vol_hawk", "edge_finder",
        "tail_picker", "0xa1…9de", "kelly_only", "0x77…f02", "anon_8810",
        "phi_trader", "z_score_zoe", "0xc0…ffe", "anon_2207", "kurtosis_kid",
    )

    fun feedFor(market: MarketListing, count: Int = 24): List<ActivityEvent> {
        val rng = Random(market.id.hashCode())
        return List(count) { i ->
            val muSpread = (market.muMax - market.muMin) * 0.5
            val mu = market.crowdMu + (rng.nextDouble() - 0.5) * muSpread * 0.4
            val sigma = market.crowdSigma * (0.6 + rng.nextDouble() * 1.4)
            val stake = listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0).random(rng)
            ActivityEvent(
                marketId = market.id,
                anonHandle = handles[(i + market.id.hashCode().mod(handles.size).coerceAtLeast(0)) % handles.size],
                mu = mu, sigma = sigma, stake = stake,
                ageMinutes = (i * 7) + rng.nextInt(0, 6),
            )
        }
    }
}
