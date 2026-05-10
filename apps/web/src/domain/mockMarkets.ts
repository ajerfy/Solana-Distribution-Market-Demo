/**
 * Port of Kotlin `MockMarkets` / `MockData.kt` — builds listings from live payload + sandbox mocks.
 */

import type {
  DemoMarket,
  DemoPayload,
  DemoPerpMarket,
  DemoRegimeIndex,
  LiveEventStats,
  MarketCategory,
  MarketListing,
} from "./types";

function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a += 0x6d2b79f5;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function synthHistory(center: number, jitter: number, seed: number, points = 48): number[] {
  const rng = mulberry32(seed);
  const drift = (rng() - 0.5) * jitter * 0.4;
  return Array.from({ length: points }, (_, i) => {
    const t = i / (points - 1);
    const wave = Math.sin(t * 4.0 + seed) * jitter * 0.6;
    const noise = (rng() - 0.5) * jitter;
    return center + drift * t * (points - 1) + wave + noise;
  });
}

function categoryFromLabel(label: string | undefined): Exclude<MarketCategory, "All"> | null {
  const x = label?.trim().toLowerCase();
  const map: Record<string, Exclude<MarketCategory, "All">> = {
    events: "Events",
    weather: "Weather",
    crypto: "Crypto",
    sports: "Sports",
    pop: "PopCulture",
    popculture: "PopCulture",
    "pop culture": "PopCulture",
    climate: "Climate",
    macro: "Macro",
    equities: "Equities",
    politics: "Politics",
  };
  return x ? map[x] ?? null : null;
}

function regimeToListing(r: DemoRegimeIndex): MarketListing {
  const level = Number.parseFloat(r.level_display) || 0;
  const muSpread = level * 0.6;
  const sigma = Math.max(level * 0.18, 1.0);
  const historyDoubles = r.history
    .map((h) => Number.parseFloat(h.level_display))
    .filter((n) => !Number.isNaN(n));
  const resolvedAt =
    r.next_rebalance_slot > 0
      ? `Rebalance ~slot ${r.next_rebalance_slot}`
      : "Continuous";
  return {
    id: `regime-${r.id}`,
    title: r.title,
    subtitle:
      r.thesis.trim() ||
      "Theme basket — long if this regime plays out, short if it doesn't",
    category: "Macro",
    unit: "level",
    resolvesAt: resolvedAt,
    crowdMu: level,
    crowdSigma: sigma,
    muMin: Math.max(level - muSpread, 0),
    muMax: level + muSpread,
    sigmaMin: sigma * 0.4,
    sigmaMax: sigma * 2.5,
    volumeUsd: 220_000 + level * 1_500,
    bettorCount: r.constituents.length * 80 + 60,
    crowdHistory:
      historyDoubles.length >= 4
        ? historyDoubles
        : synthHistory(level, sigma * 0.15, 91),
    isOnChain: false,
    resolutionSource: `Index of ${r.constituents
      .slice(0, 3)
      .map((c) => c.label)
      .join(" / ")}`,
    resolutionRule:
      "Index level recomputes as a weighted basket of yes/no constituent probabilities at each rebalance slot.",
    marketType: "RegimeIndex",
    sourceBadge: "REGIME",
    isFeaturedLive: false,
    liveEventStats: null,
    regime: r,
  };
}

function perpToListing(p: DemoPerpMarket): MarketListing {
  const mark = Number.parseFloat(p.mark_price_display) || 100;
  const sigma =
    Number.parseFloat(p.amm_sigma_display) ||
    Number.parseFloat(p.anchor_sigma_display) ||
    8;
  const historyDoubles = p.funding_path
    .map((fp) => Number.parseFloat(fp.amm_mu_display))
    .filter((n) => !Number.isNaN(n));
  const id = `perp-${p.symbol.toLowerCase().replace(/\s+/g, "-").replace(/\//g, "-")}`;
  return {
    id,
    title: p.title,
    subtitle: `Perpetual market · funding ${p.spot_funding_rate_display.slice(0, 8)}`,
    category: "Crypto",
    unit: "$",
    resolvesAt: "Continuous",
    crowdMu: mark,
    crowdSigma: sigma,
    muMin: mark - sigma * 4,
    muMax: mark + sigma * 4,
    sigmaMin: sigma * 0.4,
    sigmaMax: sigma * 2.5,
    volumeUsd:
      (Number.parseFloat(p.vault_cash_display) || 0) * 1_500 + 380_000,
    bettorCount: Math.max(p.open_positions, 8) * 110,
    crowdHistory:
      historyDoubles.length >= 4
        ? historyDoubles
        : synthHistory(mark, sigma * 0.1, 95),
    isOnChain: false,
    resolutionSource: `${p.symbol} mark price · continuous`,
    resolutionRule:
      "Perpetual market: long pays funding when AMM curve is above anchor, short pays when below.",
    marketType: "Perp",
    sourceBadge: "PYTH",
    isFeaturedLive: true,
    liveEventStats: null,
    perp: p,
  };
}

interface MockRow {
  id: string;
  category: Exclude<MarketCategory, "All">;
  title: string;
  subtitle: string;
  unit: string;
  crowdMu: number;
  crowdSigma: number;
  muMin: number;
  muMax: number;
  sigmaMin: number;
  sigmaMax: number;
  volumeUsd: number;
  bettorCount: number;
  resolvesAt: string;
  source: string;
  rule: string;
  seed: number;
}

function mockRow(m: MockRow): MarketListing {
  return {
    id: m.id,
    title: m.title,
    subtitle: m.subtitle,
    category: m.category,
    unit: m.unit,
    resolvesAt: m.resolvesAt,
    crowdMu: m.crowdMu,
    crowdSigma: m.crowdSigma,
    muMin: m.muMin,
    muMax: m.muMax,
    sigmaMin: m.sigmaMin,
    sigmaMax: m.sigmaMax,
    volumeUsd: m.volumeUsd,
    bettorCount: m.bettorCount,
    crowdHistory: synthHistory(m.crowdMu, m.crowdSigma * 0.18, m.seed),
    isOnChain: false,
    resolutionSource: m.source,
    resolutionRule: m.rule,
    marketType: "Estimation",
    isFeaturedLive: false,
    liveEventStats: null,
  };
}

const MOCK_ROWS: MockRow[] = [
  {
    id: "macro-cpi",
    category: "Macro",
    title: "US CPI YoY · May 2026",
    subtitle: "BLS print on Jun 11, 8:30 ET",
    unit: "%",
    crowdMu: 3.2,
    crowdSigma: 0.35,
    muMin: 1.5,
    muMax: 5.0,
    sigmaMin: 0.1,
    sigmaMax: 1.2,
    volumeUsd: 184_320,
    bettorCount: 412,
    resolvesAt: "Jun 11, 2026",
    source: "BLS CPI release",
    rule: "Year-over-year all-items CPI, headline number from BLS press release.",
    seed: 1,
  },
  {
    id: "macro-fed",
    category: "Macro",
    title: "Fed funds upper bound · Jul 2026 FOMC",
    subtitle: "Decision Jul 29, 2026",
    unit: "%",
    crowdMu: 4.25,
    crowdSigma: 0.18,
    muMin: 3.5,
    muMax: 5.25,
    sigmaMin: 0.05,
    sigmaMax: 0.6,
    volumeUsd: 612_400,
    bettorCount: 1204,
    resolvesAt: "Jul 29, 2026",
    source: "FOMC statement",
    rule: "Upper bound of the target range as stated in the FOMC press release.",
    seed: 2,
  },
  {
    id: "crypto-btc",
    category: "Crypto",
    title: "BTC close · May 31, 2026",
    subtitle: "Coinbase BTC-USD daily close",
    unit: "k$",
    crowdMu: 92.5,
    crowdSigma: 6.8,
    muMin: 60,
    muMax: 130,
    sigmaMin: 2,
    sigmaMax: 18,
    volumeUsd: 1_240_000,
    bettorCount: 3870,
    resolvesAt: "May 31, 2026",
    source: "Coinbase BTC-USD",
    rule: "UTC daily close on the resolution date.",
    seed: 3,
  },
  {
    id: "crypto-eth-supply",
    category: "Crypto",
    title: "ETH net issuance · Q2 2026",
    subtitle: "ultrasound.money tally",
    unit: "k ETH",
    crowdMu: -120,
    crowdSigma: 80,
    muMin: -400,
    muMax: 200,
    sigmaMin: 20,
    sigmaMax: 200,
    volumeUsd: 88_000,
    bettorCount: 192,
    resolvesAt: "Jul 1, 2026",
    source: "ultrasound.money",
    rule: "Net ETH supply change over Q2 2026 from ultrasound.money's daily series.",
    seed: 4,
  },
  {
    id: "eq-spx",
    category: "Equities",
    title: "S&P 500 close · year-end 2026",
    subtitle: "SPX last print, Dec 31",
    unit: "pts",
    crowdMu: 5840,
    crowdSigma: 320,
    muMin: 4500,
    muMax: 7500,
    sigmaMin: 80,
    sigmaMax: 800,
    volumeUsd: 2_410_000,
    bettorCount: 5412,
    resolvesAt: "Dec 31, 2026",
    source: "CBOE SPX index",
    rule: "Final 2026 calendar-year close of the SPX index.",
    seed: 5,
  },
  {
    id: "climate-temp",
    category: "Climate",
    title: "Global temp anomaly · 2026",
    subtitle: "NASA GISTEMP annual",
    unit: "°C",
    crowdMu: 1.42,
    crowdSigma: 0.11,
    muMin: 0.9,
    muMax: 2.0,
    sigmaMin: 0.04,
    sigmaMax: 0.4,
    volumeUsd: 56_300,
    bettorCount: 88,
    resolvesAt: "Jan 15, 2027",
    source: "NASA GISTEMP v4",
    rule: "Annual mean land+ocean anomaly vs 1951–1980 base period.",
    seed: 6,
  },
  {
    id: "climate-arctic",
    category: "Climate",
    title: "Arctic sea ice min · 2026",
    subtitle: "NSIDC September minimum",
    unit: "M km²",
    crowdMu: 4.35,
    crowdSigma: 0.42,
    muMin: 3.0,
    muMax: 6.0,
    sigmaMin: 0.1,
    sigmaMax: 1.0,
    volumeUsd: 22_500,
    bettorCount: 47,
    resolvesAt: "Sep 30, 2026",
    source: "NSIDC",
    rule:
      "5-day average minimum extent reported by NSIDC for the 2026 melt season.",
    seed: 7,
  },
  {
    id: "sport-nba",
    category: "Sports",
    title: "NBA Finals winner margin · 2026",
    subtitle: "Series differential, +/- games",
    unit: "games",
    crowdMu: 1.6,
    crowdSigma: 1.4,
    muMin: -3.0,
    muMax: 3.0,
    sigmaMin: 0.4,
    sigmaMax: 2.5,
    volumeUsd: 312_000,
    bettorCount: 921,
    resolvesAt: "Jun 22, 2026",
    source: "Official NBA",
    rule:
      "Series margin (winner games minus loser games) at series end.",
    seed: 8,
  },
  {
    id: "sport-elo",
    category: "Sports",
    title: "Magnus Carlsen Elo · year-end",
    subtitle: "FIDE classical rating Dec 2026",
    unit: "Elo",
    crowdMu: 2842,
    crowdSigma: 14,
    muMin: 2780,
    muMax: 2900,
    sigmaMin: 4,
    sigmaMax: 35,
    volumeUsd: 14_700,
    bettorCount: 36,
    resolvesAt: "Dec 1, 2026",
    source: "FIDE",
    rule: "Published FIDE classical rating in the December 2026 list.",
    seed: 9,
  },
  {
    id: "pol-approval",
    category: "Politics",
    title: "POTUS approval · Jul 4, 2026",
    subtitle: "538 average on Independence Day",
    unit: "%",
    crowdMu: 41.2,
    crowdSigma: 2.8,
    muMin: 30,
    muMax: 55,
    sigmaMin: 1,
    sigmaMax: 6,
    volumeUsd: 504_000,
    bettorCount: 1840,
    resolvesAt: "Jul 4, 2026",
    source: "FiveThirtyEight",
    rule:
      "538 polling average for presidential approval on the resolution date.",
    seed: 10,
  },
  {
    id: "macro-unemp",
    category: "Macro",
    title: "US unemployment rate · Jun 2026",
    subtitle: "BLS Employment Situation",
    unit: "%",
    crowdMu: 4.1,
    crowdSigma: 0.22,
    muMin: 3.0,
    muMax: 6.0,
    sigmaMin: 0.05,
    sigmaMax: 0.7,
    volumeUsd: 96_000,
    bettorCount: 244,
    resolvesAt: "Jul 3, 2026",
    source: "BLS",
    rule:
      "U-3 headline unemployment rate from the BLS Employment Situation release.",
    seed: 12,
  },
  {
    id: "events-consensus-miami",
    category: "Events",
    title: "Consensus Miami 2026 · attendance",
    subtitle: "Total registered badges, day-2 doors",
    unit: "people",
    crowdMu: 18_400,
    crowdSigma: 2300,
    muMin: 9000,
    muMax: 30_000,
    sigmaMin: 600,
    sigmaMax: 5000,
    volumeUsd: 78_300,
    bettorCount: 312,
    resolvesAt: "May 16, 2026",
    source: "CoinDesk official tally",
    rule:
      "Total unique badges scanned at Consensus Miami 2026 by close of day 2, as reported by CoinDesk.",
    seed: 21,
  },
  {
    id: "weather-nyc",
    category: "Weather",
    title: "NYC high temp · tomorrow",
    subtitle: "Central Park, NWS reading",
    unit: "°F",
    crowdMu: 76,
    crowdSigma: 4.5,
    muMin: 55,
    muMax: 100,
    sigmaMin: 1,
    sigmaMax: 12,
    volumeUsd: 42_100,
    bettorCount: 588,
    resolvesAt: "Tomorrow, 11:59 PM ET",
    source: "NWS Central Park",
    rule:
      "Daily high recorded at NWS Central Park station, tomorrow's calendar day in ET.",
    seed: 22,
  },
  {
    id: "weather-hurricanes",
    category: "Weather",
    title: "Atlantic named storms · 2026 season",
    subtitle: "NOAA tally, Jun 1 → Nov 30",
    unit: "storms",
    crowdMu: 16.5,
    crowdSigma: 4.2,
    muMin: 4,
    muMax: 32,
    sigmaMin: 1,
    sigmaMax: 9,
    volumeUsd: 31_000,
    bettorCount: 92,
    resolvesAt: "Dec 1, 2026",
    source: "NOAA NHC",
    rule:
      "Total named storms (≥39 mph sustained winds) in the Atlantic basin during the 2026 hurricane season.",
    seed: 23,
  },
  {
    id: "weather-tahoe-snow",
    category: "Weather",
    title: "Tahoe season snowfall · 2026/27",
    subtitle: "UC Berkeley CSSL Donner Pass",
    unit: "in",
    crowdMu: 380,
    crowdSigma: 95,
    muMin: 100,
    muMax: 750,
    sigmaMin: 25,
    sigmaMax: 200,
    volumeUsd: 18_400,
    bettorCount: 41,
    resolvesAt: "Jun 1, 2027",
    source: "UC Berkeley CSSL",
    rule:
      "Cumulative snowfall reported by the UC Berkeley Central Sierra Snow Lab for the 2026/27 winter season.",
    seed: 24,
  },
  {
    id: "pop-taylor-streams",
    category: "PopCulture",
    title: "Taylor Swift Spotify monthly listeners",
    subtitle: "End of month figure",
    unit: "M",
    crowdMu: 91,
    crowdSigma: 5.5,
    muMin: 60,
    muMax: 130,
    sigmaMin: 1,
    sigmaMax: 14,
    volumeUsd: 64_500,
    bettorCount: 1104,
    resolvesAt: "Last day of month",
    source: "Spotify artist page",
    rule:
      "Monthly listeners count shown on the official Taylor Swift Spotify artist page at end-of-month UTC.",
    seed: 25,
  },
  {
    id: "pop-boxoffice",
    category: "PopCulture",
    title: "Top weekend box office · this Sunday",
    subtitle: "Domestic gross, Fri–Sun",
    unit: "M$",
    crowdMu: 38,
    crowdSigma: 11,
    muMin: 5,
    muMax: 200,
    sigmaMin: 2,
    sigmaMax: 35,
    volumeUsd: 27_900,
    bettorCount: 174,
    resolvesAt: "Mon morning",
    source: "Box Office Mojo",
    rule:
      "Domestic 3-day weekend gross of the #1 film, as published Monday morning by Box Office Mojo.",
    seed: 26,
  },
  {
    id: "events-coachella",
    category: "Events",
    title: "Coachella weekend-1 · attendance",
    subtitle: "Festival promoter announced figure",
    unit: "people",
    crowdMu: 125_000,
    crowdSigma: 9000,
    muMin: 90_000,
    muMax: 160_000,
    sigmaMin: 2500,
    sigmaMax: 20_000,
    volumeUsd: 51_300,
    bettorCount: 233,
    resolvesAt: "Apr 19, 2026",
    source: "Goldenvoice",
    rule:
      "Per-day average attendance announced by Goldenvoice for Coachella weekend 1, 2026.",
    seed: 27,
  },
  {
    id: "sports-superbowl-total",
    category: "Sports",
    title: "Super Bowl LX · combined points",
    subtitle: "Final score, both teams",
    unit: "pts",
    crowdMu: 49.5,
    crowdSigma: 9.5,
    muMin: 24,
    muMax: 90,
    sigmaMin: 3,
    sigmaMax: 18,
    volumeUsd: 940_000,
    bettorCount: 6220,
    resolvesAt: "Feb 8, 2026",
    source: "NFL official",
    rule:
      "Combined regulation-and-overtime points scored by both teams in Super Bowl LX, as published by the NFL.",
    seed: 28,
  },
];

function liveStatsFromMarket(onChain: DemoMarket): LiveEventStats | null {
  if (
    !onChain.outcome_label &&
    !onChain.yes_price_display &&
    !onChain.best_bid_display &&
    !onChain.best_ask_display
  ) {
    return null;
  }
  return {
    outcomeLabel: onChain.outcome_label ?? "Live outcome",
    yesPrice: onChain.yes_price_display
      ? Number.parseFloat(onChain.yes_price_display)
      : null,
    noPrice: onChain.no_price_display
      ? Number.parseFloat(onChain.no_price_display)
      : null,
    bestBid: onChain.best_bid_display
      ? Number.parseFloat(onChain.best_bid_display)
      : null,
    bestAsk: onChain.best_ask_display
      ? Number.parseFloat(onChain.best_ask_display)
      : null,
    spread: onChain.spread_display
      ? Number.parseFloat(onChain.spread_display)
      : null,
    updatedAtMillis: onChain.updated_at_millis ?? null,
  };
}

export function buildMarketListings(payload: DemoPayload): MarketListing[] {
  const onChain = payload.market;
  const regimeIndexes = payload.regime_indexes ?? [];
  const perp = payload.perps ?? null;
  const simulation = payload.simulation ?? null;

  const category =
    categoryFromLabel(onChain.category_label) ?? "Crypto";
  const unit = onChain.unit_label ?? "%";
  const marketId =
    onChain.market_slug?.length ? `event-${onChain.market_slug}` : `market-${onChain.market_id_hex.slice(0, 8)}`;
  const comesFromLiveSource = onChain.source_badge === "POLYMARKET";
  const currentMu = Number.parseFloat(onChain.current_mu_display);
  const currentSigma = Number.parseFloat(onChain.current_sigma_display);
  const muMin =
    unit === "%" && currentMu >= 0 && currentMu <= 100
      ? 0
      : currentMu - currentSigma * 2.5;
  const muMax =
    unit === "%" && currentMu >= 0 && currentMu <= 100
      ? 100
      : currentMu + currentSigma * 2.5;
  const resolutionSource =
    onChain.resolution_source_label ??
    `Pyth ETH/USD · settlement slot ${onChain.expiry_slot}`;
  const resolutionRule =
    onChain.resolution_rule_text ??
    "Realized ETH return at expiry slot, scaled to display units.";

  const pathMus =
    simulation?.market_path
      ?.map((p) => Number.parseFloat(p.mu_display))
      .filter((n) => !Number.isNaN(n)) ?? [];
  const crowdHistory =
    pathMus.length >= 4
      ? pathMus
      : synthHistory(currentMu, currentSigma * 0.15, 11);

  const onChainListing: MarketListing = {
    id: marketId,
    title: onChain.title,
    subtitle:
      onChain.subtitle ?? "Live devnet market · single seeded pool",
    category,
    unit,
    resolvesAt: onChain.resolves_at_label ?? `Slot ${onChain.expiry_slot}`,
    crowdMu: currentMu,
    crowdSigma: currentSigma,
    muMin,
    muMax,
    sigmaMin: currentSigma * 0.5,
    sigmaMax: currentSigma * 2.0,
    volumeUsd:
      onChain.volume_usd ??
      Number.parseFloat(onChain.backing_display) * 1000,
    bettorCount:
      onChain.bettor_count ??
      Math.max(Number(onChain.total_trades) || 0, 1),
    crowdHistory,
    isOnChain: !comesFromLiveSource,
    resolutionSource,
    resolutionRule,
    marketType: "Estimation",
    sourceBadge: onChain.source_badge,
    sourceUrl: onChain.source_url,
    isFeaturedLive: !!onChain.featured_live,
    liveEventStats: liveStatsFromMarket(onChain),
  };

  const regimeListings = regimeIndexes.map(regimeToListing);
  const perpListing = perp ? perpToListing(perp) : null;
  const mocks = MOCK_ROWS.map(mockRow);

  return [
    onChainListing,
    ...regimeListings,
    ...(perpListing ? [perpListing] : []),
    ...mocks,
  ];
}

export function mockActivityForMarket(
  market: MarketListing,
  count = 24,
): import("./types").ActivityEvent[] {
  const handles = [
    "0xq…7ba",
    "anon_4291",
    "0x4f…12c",
    "vol_hawk",
    "edge_finder",
    "tail_picker",
    "kelly_only",
    "phi_trader",
  ];
  let h = 0;
  for (let i = 0; i < market.id.length; i++) h = (h * 31 + market.id.charCodeAt(i)) >>> 0;
  const rng = mulberry32(h);
  const muSpread = (market.muMax - market.muMin) * 0.5;
  return Array.from({ length: count }, (_, i) => {
    const mu =
      market.crowdMu + (rng() - 0.5) * muSpread * 0.4;
    const sigma =
      market.crowdSigma * (0.6 + rng() * 1.4);
    const stake = [10, 25, 50, 100, 250, 500][
      Math.floor(rng() * 6)
    ]!;
    return {
      marketId: market.id,
      anonHandle: handles[(i + (h % handles.length)) % handles.length]!,
      mu,
      sigma,
      stake,
      ageMinutes: i * 7 + Math.floor(rng() * 6),
    };
  });
}
