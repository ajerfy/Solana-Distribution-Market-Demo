package com.solanadistributionmarketdemo.data

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object MockMarkets {
    fun build(payload: DemoPayload): List<MarketListing> =
        build(payload.market, payload.regimeIndexes, payload.perp, payload.simulation)

    fun build(
        onChain: DemoMarket,
        regimeIndexes: List<DemoRegimeIndex> = emptyList(),
        perp: DemoPerpMarket? = null,
        simulation: DemoSimulation? = null,
    ): List<MarketListing> {
        val category = categoryFromLabel(onChain.categoryLabel) ?: MarketCategory.Crypto
        val unit = onChain.unitLabel ?: "%"
        val marketId = onChain.marketSlug?.let { "event-$it" } ?: "market-${onChain.marketIdHex.take(8)}"
        val comesFromLiveSource = onChain.sourceBadge == "POLYMARKET"
        val currentMu = onChain.currentMuDisplay.toDouble()
        val currentSigma = onChain.currentSigmaDisplay.toDouble()
        val muMin = when {
            unit == "%" && currentMu in 0.0..100.0 -> 0.0
            else -> currentMu - currentSigma * 2.5
        }
        val muMax = when {
            unit == "%" && currentMu in 0.0..100.0 -> 100.0
            else -> currentMu + currentSigma * 2.5
        }
        val resolutionSource = onChain.resolutionSourceLabel
            ?: "Pyth ETH/USD · settlement slot ${onChain.expirySlot}"
        val resolutionRule = onChain.resolutionRuleText
            ?: "Realized ETH return at expiry slot, scaled to display units."
        val liveEventStats = if (
            onChain.outcomeLabel != null ||
            onChain.yesPriceDisplay != null ||
            onChain.bestBidDisplay != null ||
            onChain.bestAskDisplay != null
        ) {
            LiveEventStats(
                outcomeLabel = onChain.outcomeLabel ?: "Live outcome",
                yesPrice = onChain.yesPriceDisplay?.toDoubleOrNull(),
                noPrice = onChain.noPriceDisplay?.toDoubleOrNull(),
                bestBid = onChain.bestBidDisplay?.toDoubleOrNull(),
                bestAsk = onChain.bestAskDisplay?.toDoubleOrNull(),
                spread = onChain.spreadDisplay?.toDoubleOrNull(),
                updatedAtMillis = onChain.updatedAtMillis,
            )
        } else {
            null
        }
        val onChainListing = MarketListing(
            id = marketId,
            title = onChain.title,
            subtitle = onChain.subtitle ?: "Live devnet market · single seeded pool",
            category = category,
            unit = unit,
            resolvesAt = onChain.resolvesAtLabel ?: "Slot ${onChain.expirySlot}",
            crowdMu = currentMu,
            crowdSigma = currentSigma,
            muMin = muMin,
            muMax = muMax,
            sigmaMin = currentSigma * 0.5,
            sigmaMax = currentSigma * 2.0,
            volumeUsd = onChain.volumeUsd ?: onChain.backingDisplay.toDouble() * 1_000.0,
            bettorCount = onChain.bettorCount ?: onChain.totalTrades.toInt().coerceAtLeast(1),
            crowdHistory = simulation?.marketPath
                ?.mapNotNull { it.muDisplay.toDoubleOrNull() }
                ?.takeIf { it.size >= 4 }
                ?: synthHistory(currentMu, currentSigma * 0.15, seed = 11),
            isOnChain = !comesFromLiveSource,
            resolutionSource = resolutionSource,
            resolutionRule = resolutionRule,
            marketType = MarketType.Estimation,
            sourceBadge = onChain.sourceBadge,
            sourceUrl = onChain.sourceUrl,
            isFeaturedLive = onChain.featuredLive,
            liveEventStats = liveEventStats,
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
            // Consumer-friendly markets — added so the app reads like a real betting product
            // for someone who didn't come for the math.
            mock(
                id = "events-consensus-miami", category = MarketCategory.Events,
                title = "Consensus Miami 2026 · attendance",
                subtitle = "Total registered badges, day-2 doors",
                unit = "people", crowdMu = 18_400.0, crowdSigma = 2_300.0,
                muMin = 9_000.0, muMax = 30_000.0, sigmaMin = 600.0, sigmaMax = 5_000.0,
                volumeUsd = 78_300.0, bettorCount = 312,
                resolvesAt = "May 16, 2026", source = "CoinDesk official tally",
                rule = "Total unique badges scanned at Consensus Miami 2026 by close of day 2, as reported by CoinDesk.",
                seed = 21,
            ),
            mock(
                id = "weather-nyc", category = MarketCategory.Weather,
                title = "NYC high temp · tomorrow",
                subtitle = "Central Park, NWS reading",
                unit = "°F", crowdMu = 76.0, crowdSigma = 4.5,
                muMin = 55.0, muMax = 100.0, sigmaMin = 1.0, sigmaMax = 12.0,
                volumeUsd = 42_100.0, bettorCount = 588,
                resolvesAt = "Tomorrow, 11:59 PM ET", source = "NWS Central Park",
                rule = "Daily high recorded at NWS Central Park station, tomorrow's calendar day in ET.",
                seed = 22,
            ),
            mock(
                id = "weather-hurricanes", category = MarketCategory.Weather,
                title = "Atlantic named storms · 2026 season",
                subtitle = "NOAA tally, Jun 1 → Nov 30",
                unit = "storms", crowdMu = 16.5, crowdSigma = 4.2,
                muMin = 4.0, muMax = 32.0, sigmaMin = 1.0, sigmaMax = 9.0,
                volumeUsd = 31_000.0, bettorCount = 92,
                resolvesAt = "Dec 1, 2026", source = "NOAA NHC",
                rule = "Total named storms (≥39 mph sustained winds) in the Atlantic basin during the 2026 hurricane season.",
                seed = 23,
            ),
            mock(
                id = "weather-tahoe-snow", category = MarketCategory.Weather,
                title = "Tahoe season snowfall · 2026/27",
                subtitle = "UC Berkeley CSSL Donner Pass",
                unit = "in", crowdMu = 380.0, crowdSigma = 95.0,
                muMin = 100.0, muMax = 750.0, sigmaMin = 25.0, sigmaMax = 200.0,
                volumeUsd = 18_400.0, bettorCount = 41,
                resolvesAt = "Jun 1, 2027", source = "UC Berkeley CSSL",
                rule = "Cumulative snowfall reported by the UC Berkeley Central Sierra Snow Lab for the 2026/27 winter season.",
                seed = 24,
            ),
            mock(
                id = "pop-taylor-streams", category = MarketCategory.PopCulture,
                title = "Taylor Swift Spotify monthly listeners",
                subtitle = "End of month figure",
                unit = "M", crowdMu = 91.0, crowdSigma = 5.5,
                muMin = 60.0, muMax = 130.0, sigmaMin = 1.0, sigmaMax = 14.0,
                volumeUsd = 64_500.0, bettorCount = 1_104,
                resolvesAt = "Last day of month", source = "Spotify artist page",
                rule = "Monthly listeners count shown on the official Taylor Swift Spotify artist page at end-of-month UTC.",
                seed = 25,
            ),
            mock(
                id = "pop-boxoffice", category = MarketCategory.PopCulture,
                title = "Top weekend box office · this Sunday",
                subtitle = "Domestic gross, Fri–Sun",
                unit = "M$", crowdMu = 38.0, crowdSigma = 11.0,
                muMin = 5.0, muMax = 200.0, sigmaMin = 2.0, sigmaMax = 35.0,
                volumeUsd = 27_900.0, bettorCount = 174,
                resolvesAt = "Mon morning", source = "Box Office Mojo",
                rule = "Domestic 3-day weekend gross of the #1 film, as published Monday morning by Box Office Mojo.",
                seed = 26,
            ),
            mock(
                id = "events-coachella", category = MarketCategory.Events,
                title = "Coachella weekend-1 · attendance",
                subtitle = "Festival promoter announced figure",
                unit = "people", crowdMu = 125_000.0, crowdSigma = 9_000.0,
                muMin = 90_000.0, muMax = 160_000.0, sigmaMin = 2_500.0, sigmaMax = 20_000.0,
                volumeUsd = 51_300.0, bettorCount = 233,
                resolvesAt = "Apr 19, 2026", source = "Goldenvoice",
                rule = "Per-day average attendance announced by Goldenvoice for Coachella weekend 1, 2026.",
                seed = 27,
            ),
            mock(
                id = "sports-superbowl-total", category = MarketCategory.Sports,
                title = "Super Bowl LX · combined points",
                subtitle = "Final score, both teams",
                unit = "pts", crowdMu = 49.5, crowdSigma = 9.5,
                muMin = 24.0, muMax = 90.0, sigmaMin = 3.0, sigmaMax = 18.0,
                volumeUsd = 940_000.0, bettorCount = 6_220,
                resolvesAt = "Feb 8, 2026", source = "NFL official",
                rule = "Combined regulation-and-overtime points scored by both teams in Super Bowl LX, as published by the NFL.",
                seed = 28,
            ),
        )

        val regimeListings = regimeIndexes.map { it.toListing() }
        val perpListing = perp?.toListing()

        return buildList {
            add(onChainListing)
            addAll(regimeListings)
            if (perpListing != null) add(perpListing)
            addAll(mocks)
        }
    }

    private fun DemoRegimeIndex.toListing(): MarketListing {
        val level = levelDisplay.toDoubleOrNull() ?: 0.0
        val muSpread = level * 0.6
        val sigma = (level * 0.18).coerceAtLeast(1.0)
        val historyDoubles = history.mapNotNull { it.levelDisplay.toDoubleOrNull() }
        val resolvedAt = if (nextRebalanceSlot > 0L) "Rebalance ~slot $nextRebalanceSlot" else "Continuous"
        return MarketListing(
            id = "regime-$id",
            title = title,
            subtitle = thesis.ifBlank { "Theme basket — long if this regime plays out, short if it doesn't" },
            category = MarketCategory.Macro,
            unit = "level",
            resolvesAt = resolvedAt,
            crowdMu = level,
            crowdSigma = sigma,
            muMin = (level - muSpread).coerceAtLeast(0.0),
            muMax = level + muSpread,
            sigmaMin = sigma * 0.4,
            sigmaMax = sigma * 2.5,
            volumeUsd = 220_000.0 + level * 1_500.0,
            bettorCount = (constituents.size * 80) + 60,
            crowdHistory = if (historyDoubles.size >= 4) historyDoubles else synthHistory(level, sigma * 0.15, seed = 91),
            isOnChain = false,
            resolutionSource = "Index of " + constituents.take(3).joinToString(" / ") { it.label },
            resolutionRule = "Index level recomputes as a weighted basket of yes/no constituent probabilities at each rebalance slot.",
            marketType = MarketType.RegimeIndex,
            sourceBadge = "REGIME",
            regime = this,
        )
    }

    private fun DemoPerpMarket.toListing(): MarketListing {
        val mark = markPriceDisplay.toDoubleOrNull() ?: 100.0
        val sigma = (ammSigmaDisplay.toDoubleOrNull() ?: anchorSigmaDisplay.toDoubleOrNull() ?: 8.0)
        val historyDoubles = fundingPath.mapNotNull { it.ammMuDisplay.toDoubleOrNull() }
        return MarketListing(
            id = "perp-${symbol.lowercase().replace(' ', '-').replace('/', '-')}",
            title = title,
            subtitle = "Perpetual market · funding " + spotFundingRateDisplay.take(8),
            category = MarketCategory.Crypto,
            unit = "$",
            resolvesAt = "Continuous",
            crowdMu = mark,
            crowdSigma = sigma,
            muMin = mark - sigma * 4.0,
            muMax = mark + sigma * 4.0,
            sigmaMin = sigma * 0.4,
            sigmaMax = sigma * 2.5,
            volumeUsd = (vaultCashDisplay.toDoubleOrNull() ?: 0.0) * 1_500.0 + 380_000.0,
            bettorCount = openPositions.coerceAtLeast(8) * 110,
            crowdHistory = if (historyDoubles.size >= 4) historyDoubles else synthHistory(mark, sigma * 0.1, seed = 95),
            isOnChain = false,
            resolutionSource = "$symbol mark price · continuous",
            resolutionRule = "Perpetual market: long pays funding when AMM curve is above anchor, short pays when below.",
            marketType = MarketType.Perp,
            sourceBadge = "PYTH",
            isFeaturedLive = true,
            perp = this,
        )
    }

    private fun categoryFromLabel(label: String?): MarketCategory? = when (label?.trim()?.lowercase()) {
        "events" -> MarketCategory.Events
        "weather" -> MarketCategory.Weather
        "crypto" -> MarketCategory.Crypto
        "sports" -> MarketCategory.Sports
        "pop", "popculture", "pop culture" -> MarketCategory.PopCulture
        "climate" -> MarketCategory.Climate
        "macro" -> MarketCategory.Macro
        "equities" -> MarketCategory.Equities
        "politics" -> MarketCategory.Politics
        else -> null
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
