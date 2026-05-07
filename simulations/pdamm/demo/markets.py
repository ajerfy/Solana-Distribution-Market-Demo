"""
Pre-configured example markets for the hackathon demo.

Each market has a real-world narrative, sensible parameters, and a
scripted scenario (oracle path + informed trader beliefs) that produces
an interesting simulation within ~30 seconds.

Resolving markets  (expiry → single outcome):
  sol_price_expiry      SOL/USD price in 7 days
  eth_merge_vote        Community distribution over ETH upgrade timing
  fed_rate_decision     Fed funds rate after next FOMC meeting

Live Polymarket markets  (seeded from real Polymarket data, UFC 328 May 9 2026):
  ufc328_chimaev_win    Chimaev win probability  — Polymarket: 83¢ ($919K vol)
  ufc328_goes_distance  Fight goes to decision   — Polymarket: 28¢ ($19.4K vol)
  ufc328_submission     Fight ends by submission — Polymarket: 53¢ ($4.2K vol)

Perpetual markets  (no expiry, funding-rate anchored):
  sol_usd_perp          SOL/USD spot perpetual (Pyth-style oracle)
  btc_dominance_perp    BTC market-cap dominance perpetual
  eth_gas_perp          ETH base fee perpetual (volatile, fat-tailed)
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Optional

from ..amm.gaussian_mixture import GaussianMixtureAMM, GaussianMixtureParams, GaussianComponent
from ..amm.anchor import CompositeAnchor, FixedAnchor, OracleAnchor
from ..amm.oracle import (
    ConstantOracle, StepOracle, RandomWalkOracle, NoisyOracle, OracleFeed
)
from ..amm.funding import FundingConfig
from ..amm.perp_market import PerpMarket

K_RUST = 5.0 * math.sqrt(10.0 * math.sqrt(math.pi))   # 21.0503


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _amm(mu: float, sigma: float, b: float = 50.0, k_scale: float = 1.0) -> GaussianMixtureAMM:
    """Build an AMM with k chosen to give ~10% solvency headroom."""
    k = k_scale * b / (10.0 * sigma) * math.sqrt(sigma * math.sqrt(math.pi))
    return GaussianMixtureAMM(
        b=b, k=k,
        params=GaussianMixtureParams.single(mu=mu, sigma=sigma),
        max_collateral_per_trade=b * 0.5,
    )


def _perp(
    amm: GaussianMixtureAMM,
    oracle: OracleFeed,
    funding_rate_bps: float = 200.0,
    funding_interval: int = 20,
    fixed_weight: float = 0.0,
) -> PerpMarket:
    """Wrap an AMM in a PerpMarket with an oracle anchor."""
    sub_anchors = [OracleAnchor(oracle_feed=oracle, _weight=1.0)]
    if fixed_weight > 0:
        # Add a fixed prior at the oracle's initial mu for stability
        obs = oracle.observe(0)
        prior = FixedAnchor(
            _distribution=GaussianMixtureParams.single(mu=obs.mu, sigma=obs.sigma * 2),
            _weight=fixed_weight,
        )
        sub_anchors.append(prior)
    anchor = CompositeAnchor(sub_anchors=sub_anchors)
    config = FundingConfig(
        funding_rate_bps=funding_rate_bps,
        slots_per_period=funding_interval,
        kl_cap=8.0,
        min_kl_threshold=0.005,
    )
    return PerpMarket(
        amm=amm, anchor=anchor,
        funding_config=config,
        funding_interval=funding_interval,
    )


# ---------------------------------------------------------------------------
# Resolving market definitions
# ---------------------------------------------------------------------------

@dataclass
class ResolvingMarketDef:
    id: str
    title: str
    description: str
    unit: str
    initial_mu: float
    initial_sigma: float
    b: float
    true_outcome: float
    oracle: OracleFeed               # used to script informed traders
    presets: list[dict]              # [(label, target_mu, target_sigma)]

    def build_amm(self) -> GaussianMixtureAMM:
        return _amm(self.initial_mu, self.initial_sigma, self.b)


SOL_PRICE_EXPIRY = ResolvingMarketDef(
    id="sol_price_expiry",
    title="SOL/USD — 7-Day Expiry",
    description=(
        "What will the SOL/USD price be at market expiry?\n"
        "Traders post collateral to shift the market's belief distribution.\n"
        "At expiry the oracle resolves the outcome and payouts are deterministic."
    ),
    unit="USD",
    initial_mu=155.0,
    initial_sigma=20.0,
    b=50.0,
    true_outcome=172.0,
    oracle=NoisyOracle(
        underlying=StepOracle(
            initial_mu=155.0,
            steps=[(30, 165.0), (70, 172.0)],
            sigma=5.0,
        ),
        noise_sigma=2.0,
        seed=11,
    ),
    presets=[
        {"label": "Mild bull (+10)", "target_mu": 165.0, "target_sigma": 18.0},
        {"label": "Strong bull (+25)", "target_mu": 180.0, "target_sigma": 15.0},
        {"label": "Bear scenario (-15)", "target_mu": 140.0, "target_sigma": 22.0},
        {"label": "Tighter conviction", "target_mu": 155.0, "target_sigma": 10.0},
        {"label": "Fat tail / uncertainty", "target_mu": 155.0, "target_sigma": 35.0},
    ],
)

ETH_MERGE_VOTE = ResolvingMarketDef(
    id="eth_merge_vote",
    title="ETH Upgrade — Community Confidence Score",
    description=(
        "Traders express their belief over a 0–100 community confidence score\n"
        "for a proposed Ethereum protocol upgrade passing safely.\n"
        "Distribution shape captures both point estimate and uncertainty."
    ),
    unit="confidence (0–100)",
    initial_mu=62.0,
    initial_sigma=12.0,
    b=50.0,
    true_outcome=78.0,
    oracle=StepOracle(
        initial_mu=62.0,
        steps=[(25, 72.0), (60, 78.0)],
        sigma=4.0,
    ),
    presets=[
        {"label": "Optimistic", "target_mu": 75.0, "target_sigma": 10.0},
        {"label": "Pessimistic", "target_mu": 50.0, "target_sigma": 15.0},
        {"label": "High conviction bull", "target_mu": 80.0, "target_sigma": 6.0},
        {"label": "Bimodal uncertainty", "target_mu": 62.0, "target_sigma": 20.0},
    ],
)

FED_RATE_DECISION = ResolvingMarketDef(
    id="fed_rate_decision",
    title="Fed Funds Rate — Post-FOMC (bps)",
    description=(
        "What will the Federal Funds Rate be after the next FOMC meeting?\n"
        "Current rate: 525 bps.  Market prices in probability of cut vs hold.\n"
        "Sigma captures disagreement; mu captures consensus expectation."
    ),
    unit="basis points",
    initial_mu=525.0,
    initial_sigma=15.0,
    b=50.0,
    true_outcome=500.0,
    oracle=StepOracle(
        initial_mu=525.0,
        steps=[(40, 510.0), (80, 500.0)],
        sigma=5.0,
    ),
    presets=[
        {"label": "No cut (hold)", "target_mu": 525.0, "target_sigma": 8.0},
        {"label": "25 bps cut", "target_mu": 500.0, "target_sigma": 10.0},
        {"label": "50 bps cut", "target_mu": 475.0, "target_sigma": 12.0},
        {"label": "Uncertainty widens", "target_mu": 510.0, "target_sigma": 25.0},
    ],
)

# ---------------------------------------------------------------------------
# Live Polymarket markets — UFC 328: Chimaev vs Strickland (May 9, 2026)
# Data snapshot: May 7 2026 | source: polymarket.com/sports/ufc/ufc-sea2-kha7-2026-05-09
# ---------------------------------------------------------------------------

UFC328_CHIMAEV_WIN = ResolvingMarketDef(
    id="ufc328_chimaev_win",
    title="UFC 328 — Chimaev Win Probability",
    description=(
        "Will Khamzat Chimaev defeat Sean Strickland at UFC 328 (May 9, 2026)?\n"
        "Distribution market over the win probability (0–100 scale).\n"
        "Seeded from live Polymarket: Chimaev 83¢, Strickland 18¢ (~$919K volume).\n"
        "Oracle path simulates pre-fight odds drift as fight approaches."
    ),
    unit="probability (0–100)",
    initial_mu=83.0,
    initial_sigma=10.0,
    b=50.0,
    true_outcome=100.0,   # demo scenario: Chimaev finishes Strickland
    oracle=NoisyOracle(
        underlying=StepOracle(
            initial_mu=83.0,
            steps=[(20, 86.0), (55, 91.0), (85, 100.0)],
            sigma=4.0,
        ),
        noise_sigma=2.5,
        seed=328,
    ),
    presets=[
        {"label": "Polymarket consensus (83%)", "target_mu": 83.0, "target_sigma": 10.0},
        {"label": "Chimaev dominant (92%)",     "target_mu": 92.0, "target_sigma": 6.0},
        {"label": "Strickland upset (40%)",     "target_mu": 40.0, "target_sigma": 18.0},
        {"label": "Pick'em (50%)",              "target_mu": 50.0, "target_sigma": 15.0},
        {"label": "High conviction bull (96%)", "target_mu": 96.0, "target_sigma": 4.0},
    ],
)

UFC328_GOES_DISTANCE = ResolvingMarketDef(
    id="ufc328_goes_distance",
    title="UFC 328 — Fight Goes to Decision",
    description=(
        "Will Chimaev vs Strickland go all 5 rounds to a judges' decision?\n"
        "Distribution market over the 'goes distance' probability (0–100 scale).\n"
        "Polymarket: 28¢ Yes ($19.4K volume). Chimaev KO/TKO 24%, sub 53%.\n"
        "Oracle drifts lower as Chimaev's finishing ability is priced in."
    ),
    unit="probability (0–100)",
    initial_mu=28.0,
    initial_sigma=12.0,
    b=50.0,
    true_outcome=0.0,   # demo scenario: fight ends before the final bell
    oracle=NoisyOracle(
        underlying=StepOracle(
            initial_mu=28.0,
            steps=[(25, 24.0), (60, 18.0), (90, 5.0)],
            sigma=5.0,
        ),
        noise_sigma=3.0,
        seed=329,
    ),
    presets=[
        {"label": "Polymarket (28%)",       "target_mu": 28.0, "target_sigma": 12.0},
        {"label": "Likely finish (<20%)",   "target_mu": 18.0, "target_sigma": 8.0},
        {"label": "Goes long (55%)",        "target_mu": 55.0, "target_sigma": 14.0},
        {"label": "Early finish (<10%)",    "target_mu": 9.0,  "target_sigma": 6.0},
    ],
)

UFC328_SUBMISSION = ResolvingMarketDef(
    id="ufc328_submission",
    title="UFC 328 — Fight Ends by Submission",
    description=(
        "Will the Chimaev vs Strickland fight end by submission?\n"
        "Distribution market over submission-finish probability (0–100 scale).\n"
        "Polymarket: 53¢ Yes ($4.2K volume) — driven by Chimaev's elite grappling.\n"
        "Sigma captures uncertainty between sub finish vs striking TKO."
    ),
    unit="probability (0–100)",
    initial_mu=53.0,
    initial_sigma=14.0,
    b=50.0,
    true_outcome=100.0,   # demo scenario: Chimaev submits Strickland
    oracle=NoisyOracle(
        underlying=StepOracle(
            initial_mu=53.0,
            steps=[(30, 57.0), (70, 65.0)],
            sigma=5.0,
        ),
        noise_sigma=4.0,
        seed=330,
    ),
    presets=[
        {"label": "Polymarket (53%)",             "target_mu": 53.0, "target_sigma": 14.0},
        {"label": "Sub likely (70%)",             "target_mu": 70.0, "target_sigma": 10.0},
        {"label": "Striking finish favored (25%)", "target_mu": 25.0, "target_sigma": 12.0},
        {"label": "Toss-up (50%)",                "target_mu": 50.0, "target_sigma": 16.0},
    ],
)

RESOLVING_MARKETS: dict[str, ResolvingMarketDef] = {
    m.id: m for m in [
        SOL_PRICE_EXPIRY, ETH_MERGE_VOTE, FED_RATE_DECISION,
        UFC328_CHIMAEV_WIN, UFC328_GOES_DISTANCE, UFC328_SUBMISSION,
    ]
}


# ---------------------------------------------------------------------------
# Perpetual market definitions
# ---------------------------------------------------------------------------

@dataclass
class PerpMarketDef:
    id: str
    title: str
    description: str
    unit: str
    initial_mu: float
    initial_sigma: float
    b: float
    oracle: OracleFeed
    funding_rate_bps: float
    funding_interval: int
    fixed_prior_weight: float = 0.3    # blended fixed prior for stability

    def build(self) -> PerpMarket:
        amm = _amm(self.initial_mu, self.initial_sigma, self.b)
        return _perp(
            amm, self.oracle,
            funding_rate_bps=self.funding_rate_bps,
            funding_interval=self.funding_interval,
            fixed_weight=self.fixed_prior_weight,
        )


SOL_USD_PERP = PerpMarketDef(
    id="sol_usd_perp",
    title="SOL/USD — Perpetual",
    description=(
        "Perpetual distribution market tracking SOL/USD spot price.\n"
        "Oracle: synthetic Pyth feed (mean-reverting random walk, σ=0.8/slot).\n"
        "Funding rate: 200 bps × KL(market || oracle anchor) per period.\n"
        "Positions that push the market away from the oracle pay funding."
    ),
    unit="USD",
    initial_mu=155.0,
    initial_sigma=18.0,
    b=100.0,
    oracle=NoisyOracle(
        underlying=RandomWalkOracle(
            initial_mu=155.0, vol=0.8,
            mean_reversion=0.05, long_run_mu=155.0,
            sigma=5.0, seed=42,
        ),
        noise_sigma=1.5, seed=7,
    ),
    funding_rate_bps=200.0,
    funding_interval=20,
)

BTC_DOMINANCE_PERP = PerpMarketDef(
    id="btc_dominance_perp",
    title="BTC Dominance — Perpetual",
    description=(
        "Perpetual distribution market over Bitcoin's % share of total crypto market cap.\n"
        "Slower-moving, mean-reverting oracle (vol=0.2/slot around 52%).\n"
        "Useful for macro positioning; sigma captures regime uncertainty."
    ),
    unit="% market cap",
    initial_mu=52.0,
    initial_sigma=3.0,
    b=50.0,
    oracle=RandomWalkOracle(
        initial_mu=52.0, vol=0.2,
        mean_reversion=0.1, long_run_mu=52.0,
        sigma=2.0, seed=13,
    ),
    funding_rate_bps=150.0,
    funding_interval=20,
    fixed_prior_weight=0.5,
)

ETH_GAS_PERP = PerpMarketDef(
    id="eth_gas_perp",
    title="ETH Base Fee — Perpetual",
    description=(
        "Perpetual distribution market over Ethereum base fee (Gwei).\n"
        "Highly volatile oracle (vol=2.0/slot); fat-tailed beliefs natural here.\n"
        "K=3 mixture posteriors used by informed traders to capture bimodal\n"
        "low-gas / high-gas congestion regimes."
    ),
    unit="Gwei",
    initial_mu=25.0,
    initial_sigma=8.0,
    b=50.0,
    oracle=NoisyOracle(
        underlying=RandomWalkOracle(
            initial_mu=25.0, vol=2.0,
            mean_reversion=0.08, long_run_mu=25.0,
            sigma=6.0, seed=99,
        ),
        noise_sigma=3.0, seed=55,
    ),
    funding_rate_bps=300.0,
    funding_interval=15,
    fixed_prior_weight=0.2,
)

PERP_MARKETS: dict[str, PerpMarketDef] = {
    m.id: m for m in [SOL_USD_PERP, BTC_DOMINANCE_PERP, ETH_GAS_PERP]
}


def list_markets() -> None:
    print("\n=== Resolving Markets ===")
    for m in RESOLVING_MARKETS.values():
        print(f"  {m.id:30s}  {m.title}")
    print("\n=== Perpetual Markets ===")
    for m in PERP_MARKETS.values():
        print(f"  {m.id:30s}  {m.title}")
    print()
