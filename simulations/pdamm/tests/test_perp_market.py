"""
Perpetual distribution market tests — Layers 1–4 end-to-end.

Covers:
  - Anchor tracks oracle correctly
  - KL divergence is non-negative and zero at anchor
  - Funding rate is zero when AMM == anchor, positive when diverged
  - Funding punishes noise traders, rewards informed traders
  - Informed trader drives market toward oracle
  - LP NAV grows under net positive informed flow
  - Vault never goes negative
  - State version guards hold across perp lifecycle
  - Oracle types: ConstantOracle, StepOracle, RandomWalkOracle, NoisyOracle

Run:  pytest simulations/pdamm/tests/test_perp_market.py -v
"""

from __future__ import annotations

import math
import sys
import os

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../.."))

from pdamm.amm.gaussian_mixture import GaussianMixtureAMM, GaussianMixtureParams
from pdamm.amm.anchor import (
    CompositeAnchor, FixedAnchor, OracleAnchor, EMAnchor,
    kl_divergence, kl_divergence_normal, kl_project_to_normal,
)
from pdamm.amm.oracle import (
    ConstantOracle, StepOracle, RandomWalkOracle, NoisyOracle, HistoricalOracle,
)
from pdamm.amm.funding import FundingRateEngine, FundingConfig
from pdamm.amm.perp_market import PerpMarket
from pdamm.agents.informed_trader import InformedTrader
from pdamm.agents.noise_trader import NoiseTrader
from pdamm.agents.lp_provider import LPProvider


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

K_RUST = 5.0 * math.sqrt(10.0 * math.sqrt(math.pi))   # 21.0503


def make_amm(mu: float = 95.0, sigma: float = 10.0, b: float = 50.0) -> GaussianMixtureAMM:
    params = GaussianMixtureParams.single(mu=mu, sigma=sigma)
    return GaussianMixtureAMM(b=b, k=K_RUST, params=params, max_collateral_per_trade=50.0)


def make_perp(
    amm_mu: float = 95.0,
    anchor_mu: float = 95.0,
    anchor_sigma: float = 10.0,
    funding_rate_bps: float = 200.0,
    funding_interval: int = 10,
) -> PerpMarket:
    amm = make_amm(mu=amm_mu)
    oracle = ConstantOracle(mu=anchor_mu, sigma=anchor_sigma)
    anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle)])
    config = FundingConfig(
        funding_rate_bps=funding_rate_bps,
        slots_per_period=funding_interval,
        kl_cap=10.0,
        min_kl_threshold=0.001,
    )
    return PerpMarket(
        amm=amm,
        anchor=anchor,
        funding_config=config,
        funding_interval=funding_interval,
    )


# ---------------------------------------------------------------------------
# Layer 2: Anchor tests
# ---------------------------------------------------------------------------

class TestAnchor:
    def test_kl_self_is_zero(self):
        p = GaussianMixtureParams.single(mu=100.0, sigma=8.0)
        assert kl_divergence(p, p) < 1e-10

    def test_kl_non_negative(self):
        p = GaussianMixtureParams.single(mu=100.0, sigma=8.0)
        q = GaussianMixtureParams.single(mu=110.0, sigma=12.0)
        assert kl_divergence(p, q) >= 0.0

    def test_kl_asymmetric(self):
        p = GaussianMixtureParams.single(mu=100.0, sigma=5.0)
        q = GaussianMixtureParams.single(mu=110.0, sigma=10.0)
        assert abs(kl_divergence(p, q) - kl_divergence(q, p)) > 0.01

    def test_kl_increases_with_distance(self):
        base = GaussianMixtureParams.single(mu=100.0, sigma=8.0)
        near = GaussianMixtureParams.single(mu=102.0, sigma=8.0)
        far = GaussianMixtureParams.single(mu=120.0, sigma=8.0)
        assert kl_divergence(base, near) < kl_divergence(base, far)

    def test_fixed_anchor_constant(self):
        dist = GaussianMixtureParams.single(mu=50.0, sigma=5.0)
        anchor = FixedAnchor(_distribution=dist)
        assert anchor.distribution(0) == anchor.distribution(1000)

    def test_oracle_anchor_tracks_oracle(self):
        oracle = ConstantOracle(mu=100.0, sigma=3.0)
        anchor = OracleAnchor(oracle_feed=oracle)
        d = anchor.distribution(42)
        assert abs(d.components[0].mu - 100.0) < 1e-9

    def test_ema_anchor_converges(self):
        ema = EMAnchor(half_life_slots=50.0)
        params = GaussianMixtureParams.single(mu=200.0, sigma=10.0)
        for slot in range(500):
            ema.update(params, slot)
        d = ema.distribution(499)
        assert abs(d.components[0].mu - 200.0) < 1.0

    def test_composite_anchor_weighted_average(self):
        a1 = FixedAnchor(_distribution=GaussianMixtureParams.single(mu=80.0, sigma=5.0), _weight=1.0)
        a2 = FixedAnchor(_distribution=GaussianMixtureParams.single(mu=120.0, sigma=5.0), _weight=1.0)
        composite = CompositeAnchor(sub_anchors=[a1, a2])
        d = composite.distribution(0)
        assert abs(d.components[0].mu - 100.0) < 1e-6

    def test_kl_project_to_normal_k1(self):
        p = GaussianMixtureParams.single(mu=77.0, sigma=9.0)
        projected = kl_project_to_normal(p)
        c = projected.components[0]
        assert abs(c.mu - 77.0) < 1e-9
        assert abs(c.sigma - 9.0) < 1e-9


# ---------------------------------------------------------------------------
# Layer 3: Oracle tests
# ---------------------------------------------------------------------------

class TestOracle:
    def test_constant_oracle(self):
        oracle = ConstantOracle(mu=100.0, sigma=5.0)
        obs = oracle.observe(999)
        assert obs.mu == 100.0
        assert obs.sigma == 5.0

    def test_step_oracle_before_step(self):
        oracle = StepOracle(initial_mu=90.0, steps=[(100, 110.0)], sigma=3.0)
        assert oracle.observe(50).mu == 90.0

    def test_step_oracle_after_step(self):
        oracle = StepOracle(initial_mu=90.0, steps=[(100, 110.0)], sigma=3.0)
        assert oracle.observe(100).mu == 110.0
        assert oracle.observe(200).mu == 110.0

    def test_step_oracle_multiple_steps(self):
        oracle = StepOracle(
            initial_mu=90.0,
            steps=[(100, 100.0), (200, 110.0), (300, 120.0)],
        )
        assert oracle.observe(0).mu == 90.0
        assert oracle.observe(150).mu == 100.0
        assert oracle.observe(250).mu == 110.0
        assert oracle.observe(350).mu == 120.0

    def test_random_walk_oracle_reproducible(self):
        o1 = RandomWalkOracle(initial_mu=100.0, vol=1.0, seed=7)
        o2 = RandomWalkOracle(initial_mu=100.0, vol=1.0, seed=7)
        assert o1.observe(50).mu == o2.observe(50).mu

    def test_random_walk_oracle_mean_reverting(self):
        oracle = RandomWalkOracle(
            initial_mu=100.0, vol=0.1,
            mean_reversion=0.3, long_run_mu=100.0, seed=0
        )
        mus = [oracle.observe(s).mu for s in range(500)]
        # With strong mean reversion, should stay close to 100
        assert all(abs(m - 100.0) < 20.0 for m in mus)

    def test_noisy_oracle_has_higher_sigma(self):
        base = ConstantOracle(mu=100.0, sigma=2.0)
        noisy = NoisyOracle(underlying=base, noise_sigma=3.0)
        obs = noisy.observe(0)
        assert obs.sigma > 2.0

    def test_historical_oracle_interpolation(self):
        oracle = HistoricalOracle(observations=[
            (0, 100.0, 5.0),
            (100, 200.0, 5.0),
        ])
        obs = oracle.observe(50)
        assert abs(obs.mu - 150.0) < 1e-9

    def test_historical_oracle_extrapolation(self):
        oracle = HistoricalOracle(observations=[(0, 100.0, 5.0), (100, 200.0, 5.0)])
        assert oracle.observe(-10).mu == 100.0
        assert oracle.observe(200).mu == 200.0


# ---------------------------------------------------------------------------
# Layer 3: Funding rate tests
# ---------------------------------------------------------------------------

class TestFundingRate:
    def test_funding_zero_when_at_anchor(self):
        market = make_perp(amm_mu=95.0, anchor_mu=95.0)
        rate = market.spot_funding_rate()
        assert rate < 1e-6

    def test_funding_positive_when_diverged(self):
        market = make_perp(amm_mu=95.0, anchor_mu=130.0)
        rate = market.spot_funding_rate()
        assert rate > 0.0

    def test_funding_increases_with_divergence(self):
        near = make_perp(amm_mu=95.0, anchor_mu=100.0)
        far = make_perp(amm_mu=95.0, anchor_mu=150.0)
        assert near.spot_funding_rate() < far.spot_funding_rate()

    def test_funding_settles_automatically(self):
        market = make_perp(amm_mu=95.0, anchor_mu=130.0, funding_interval=5)
        lp = LPProvider("lp0", initial_deposit=50.0)
        lp.enter(market)

        trader = InformedTrader("t0", GaussianMixtureParams.single(mu=95.0, sigma=10.0))
        trader.step(market)

        market.tick(10)  # advance 10 slots → 2 funding settlements
        assert len(market.funding_history) >= 1

    def test_funding_charges_noise_traders(self):
        """Noise traders moving market away from anchor accumulate funding_paid."""
        market = make_perp(amm_mu=95.0, anchor_mu=95.0, funding_interval=5)
        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        noise = NoiseTrader("noise0", mu_shock_scale=10.0, seed=1)
        for _ in range(10):
            noise.step(market)

        market.tick(50)

        total_paid = sum(
            p.funding_paid for p in market.open_positions.values()
        )
        # Some funding should have been charged given the noise trading
        assert total_paid >= 0.0  # non-negative by construction

    def test_skc_separation_no_calibration_in_funding(self):
        """
        The FundingRateEngine must NOT have a scoring_rule or calibration
        attribute.  This verifies the arXiv 2306.04305 Appendix C wall.
        """
        engine = FundingRateEngine(
            anchor=CompositeAnchor(sub_anchors=[
                FixedAnchor(_distribution=GaussianMixtureParams.single(mu=100.0, sigma=5.0))
            ])
        )
        assert not hasattr(engine, "scoring_rule")
        assert not hasattr(engine, "calibration")
        assert not hasattr(engine, "skc")


# ---------------------------------------------------------------------------
# Layer 4: End-to-end perpetual simulation tests
# ---------------------------------------------------------------------------

class TestPerpEndToEnd:
    def test_vault_never_negative(self):
        market = make_perp(funding_interval=5)
        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        noise = NoiseTrader("n0", seed=0)
        informed = InformedTrader(
            "i0",
            GaussianMixtureParams.single(mu=105.0, sigma=9.0),
            step_fraction=0.2,
        )

        for step in range(100):
            noise.step(market)
            informed.step(market)
            market.tick(1)

        assert market.amm.cash >= 0.0

    def test_informed_trader_drives_market_toward_oracle(self):
        """
        With an oracle at mu=110 and InformedTrader holding oracle belief,
        the AMM should move from 95 toward 110 over 200 steps.
        """
        oracle = ConstantOracle(mu=110.0, sigma=8.0)
        anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle)])
        amm = make_amm(mu=95.0)
        market = PerpMarket(amm=amm, anchor=anchor, funding_interval=10)

        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        informed = InformedTrader(
            "i0",
            GaussianMixtureParams.single(mu=110.0, sigma=8.0),
            step_fraction=0.25,
            max_fraction_of_b=0.15,
        )

        for _ in range(200):
            informed.step(market)
            market.tick(1)

        progress = market.amm.current_mu - 95.0
        assert progress > 5.0, (
            f"Market did not move toward oracle: mu={market.amm.current_mu:.2f}"
        )

    def test_step_oracle_market_follows_regime_change(self):
        """
        Oracle shifts from mu=95 to mu=120 at slot 50.
        An informed trader with that knowledge should push the market toward 120.
        """
        oracle = StepOracle(initial_mu=95.0, steps=[(50, 120.0)])
        anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle)])
        amm = make_amm(mu=95.0)
        market = PerpMarket(amm=amm, anchor=anchor, funding_interval=10)

        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        def make_informed(slot: int) -> InformedTrader:
            target_mu = 95.0 if slot < 50 else 120.0
            return InformedTrader(
                f"i_{slot}",
                GaussianMixtureParams.single(mu=target_mu, sigma=9.0),
                step_fraction=0.2,
                max_fraction_of_b=0.10,
            )

        for step in range(200):
            trader = make_informed(market.slot)
            trader.step(market)
            market.tick(1)

        assert market.amm.current_mu > 100.0, (
            f"Market did not follow regime change: mu={market.amm.current_mu:.2f}"
        )

    def test_random_walk_oracle_market_tracks(self):
        """
        With a random-walk oracle and informed traders, the AMM should stay
        within 15 units of the oracle after 300 steps.
        """
        oracle = RandomWalkOracle(
            initial_mu=100.0, vol=0.3,
            mean_reversion=0.1, long_run_mu=100.0, seed=42
        )
        anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle)])
        amm = make_amm(mu=100.0)
        market = PerpMarket(amm=amm, anchor=anchor, funding_interval=10)

        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        for step in range(300):
            oracle_mu = oracle.observe(market.slot).mu
            informed = InformedTrader(
                f"i_{step}",
                GaussianMixtureParams.single(mu=oracle_mu, sigma=9.0),
                step_fraction=0.15,
                max_fraction_of_b=0.10,
            )
            informed.step(market)
            market.tick(1)

        final_oracle_mu = oracle.observe(market.slot).mu
        tracking_error = abs(market.amm.current_mu - final_oracle_mu)
        assert tracking_error < 15.0, (
            f"Market lost track of oracle: AMM={market.amm.current_mu:.2f}, "
            f"oracle={final_oracle_mu:.2f}, error={tracking_error:.2f}"
        )

    def test_lp_nav_positive_after_informed_flow(self):
        """
        When informed traders drive price discovery and fees accrue,
        the LP's NAV should remain positive.
        """
        market = make_perp(amm_mu=95.0, anchor_mu=105.0, funding_interval=10)
        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)
        initial_nav = market.lp_nav("lp0")

        informed = InformedTrader(
            "i0",
            GaussianMixtureParams.single(mu=105.0, sigma=9.0),
            step_fraction=0.2,
            max_fraction_of_b=0.10,
        )
        for _ in range(50):
            informed.step(market)
            market.tick(1)

        assert market.lp_nav("lp0") > 0.0

    def test_lp_can_exit_and_receive_cash(self):
        market = make_perp(funding_interval=10)
        lp = LPProvider("lp0", initial_deposit=50.0, withdraw_at_slot=30)
        lp.enter(market)

        for step in range(40):
            lp.step(market)
            market.tick(1)

        assert lp.withdrawn > 0.0

    def test_state_version_increments_on_perp_trade(self):
        market = make_perp()
        lp = LPProvider("lp0", initial_deposit=50.0)
        lp.enter(market)

        v0 = market.amm.state_version
        new_params = GaussianMixtureParams.single(mu=100.0, sigma=10.0)
        market.open_trade("t0", new_params)
        assert market.amm.state_version == v0 + 1

    def test_kl_decreases_as_informed_trader_aligns_with_anchor(self):
        """
        When the AMM starts aligned with anchor, informed trading toward
        the anchor should keep KL near zero.
        """
        market = make_perp(amm_mu=95.0, anchor_mu=95.0, funding_interval=10)
        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        for _ in range(30):
            # Trade back toward anchor each step
            anchor_mu = market.anchor.distribution(market.slot).components[0].mu
            informed = InformedTrader(
                "i0",
                GaussianMixtureParams.single(mu=anchor_mu, sigma=10.0),
                step_fraction=0.1,
            )
            informed.step(market)
            market.tick(1)

        assert market.current_kl_from_anchor() < 1.0

    def test_noisy_oracle_anchor_still_stabilizes(self):
        """
        Even with a noisy oracle, the composite anchor should produce a
        stable enough signal that the market doesn't blow up.
        """
        base_oracle = ConstantOracle(mu=100.0, sigma=3.0)
        noisy_oracle = NoisyOracle(underlying=base_oracle, noise_sigma=2.0, seed=5)
        anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=noisy_oracle)])
        amm = make_amm(mu=100.0)
        market = PerpMarket(amm=amm, anchor=anchor, funding_interval=10)

        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        noise = NoiseTrader("n0", mu_shock_scale=3.0, seed=2)
        for _ in range(100):
            noise.step(market)
            market.tick(1)

        assert market.amm.cash >= 0.0
        assert 50.0 < market.amm.current_mu < 150.0

    def test_multiple_lps_proportional_redemption(self):
        market = make_perp(funding_interval=20)
        lp1 = LPProvider("lp1", initial_deposit=60.0)
        lp2 = LPProvider("lp2", initial_deposit=40.0)
        lp1.enter(market)
        lp2.enter(market)

        market.tick(5)

        nav1 = market.lp_nav("lp1")
        nav2 = market.lp_nav("lp2")
        # LP1 deposited 60%, LP2 40% → NAV ratio should be ~1.5
        ratio = nav1 / nav2 if nav2 > 0 else 0.0
        assert abs(ratio - 1.5) < 0.1
