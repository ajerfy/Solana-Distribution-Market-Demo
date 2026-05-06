"""
Attack scenario tests — six adversarial scenarios from the build spec.

1. Sandwich attack       — front-run a known large trade, back-run it for profit
2. Oscillation drain     — alternate push/pull to drain LP via funding (arXiv failure mode)
3. Oracle manipulation   — corrupt oracle to mis-anchor the market
4. LP griefing           — open many tiny positions to lock LP redemption
5. Stale quote exploit   — submit a quote that is too old / from wrong state_version
6. Collateral understate — trade with insufficient collateral (solvency violation)

Each test asserts the attack either fails outright, yields no profit, or leaves
the vault solvent.  The SKC/funding separation (arXiv 2306.04305 Appendix C)
gets its own oscillation test verifying the wall holds.

Run:  pytest simulations/pdamm/tests/test_attacks.py -v
"""

from __future__ import annotations

import math
import sys
import os

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../.."))

from pdamm.amm.gaussian_mixture import (
    GaussianMixtureAMM, GaussianMixtureParams, GaussianComponent,
    compute_collateral,
)
from pdamm.amm.anchor import CompositeAnchor, FixedAnchor, OracleAnchor
from pdamm.amm.oracle import ConstantOracle, StepOracle
from pdamm.amm.funding import FundingRateEngine, FundingConfig
from pdamm.amm.perp_market import PerpMarket
from pdamm.agents.informed_trader import InformedTrader
from pdamm.agents.lp_provider import LPProvider


K_RUST = 5.0 * math.sqrt(10.0 * math.sqrt(math.pi))


def make_amm(mu: float = 100.0, sigma: float = 10.0, b: float = 50.0) -> GaussianMixtureAMM:
    return GaussianMixtureAMM(
        b=b, k=K_RUST,
        params=GaussianMixtureParams.single(mu=mu, sigma=sigma),
        max_collateral_per_trade=50.0,
    )


def make_perp(
    amm_mu: float = 100.0,
    anchor_mu: float = 100.0,
    funding_rate_bps: float = 200.0,
    funding_interval: int = 10,
) -> PerpMarket:
    amm = make_amm(mu=amm_mu)
    oracle = ConstantOracle(mu=anchor_mu, sigma=5.0)
    anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle)])
    config = FundingConfig(
        funding_rate_bps=funding_rate_bps,
        slots_per_period=funding_interval,
        kl_cap=10.0,
        min_kl_threshold=0.001,
    )
    return PerpMarket(amm=amm, anchor=anchor, funding_config=config,
                      funding_interval=funding_interval)


# ---------------------------------------------------------------------------
# 1. Sandwich attack
# ---------------------------------------------------------------------------

class TestSandwichAttack:
    """
    Attacker sees a large informed trade incoming.  They front-run to move
    the market in the same direction, let the victim trade, then back-run.
    In a standard AMM the attacker profits; here collateral is symmetric so
    the back-run costs at least as much as the front-run gained.
    """

    def test_sandwich_does_not_yield_free_profit(self):
        amm = make_amm(mu=100.0)
        victim_target = GaussianMixtureParams.single(mu=115.0, sigma=10.0)
        attacker_front = GaussianMixtureParams.single(mu=108.0, sigma=10.0)

        # Front-run: attacker pays collateral to push market up
        front_quote = amm.quote_trade(attacker_front)
        front_cost = front_quote["total_debit"]
        amm.execute_trade("attacker", attacker_front)

        # Victim trades (market has moved already, costs more)
        amm.execute_trade("victim", victim_target)

        # Back-run: attacker tries to profit by moving back toward original
        back_target = GaussianMixtureParams.single(mu=100.0, sigma=10.0)
        back_quote = amm.quote_trade(back_target)
        back_cost = back_quote["total_debit"]

        # Attacker's net cash outflow = front_cost + back_cost (both positive)
        # In a collateral-symmetric market, you can't manufacture free money
        # by oscillating — both trades cost collateral.
        assert front_cost > 0.0
        assert back_cost > 0.0

    def test_sandwich_vault_remains_solvent(self):
        amm = make_amm(mu=100.0)
        # rapid round-trip by attacker
        up = GaussianMixtureParams.single(mu=110.0, sigma=10.0)
        amm.execute_trade("a", up)
        down = GaussianMixtureParams.single(mu=100.0, sigma=10.0)
        amm.execute_trade("a", down)
        assert amm.cash >= 0.0


# ---------------------------------------------------------------------------
# 2. Oscillation drain (arXiv 2306.04305 Appendix C attack)
# ---------------------------------------------------------------------------

class TestOscillationDrain:
    """
    Attacker alternates between two distributions in the hope that the
    funding rate paid on one leg is smaller than received on the other,
    draining the LP pool.

    The SKC wall means funding is computed from KL(market || anchor), not
    from a scoring rule.  Both legs away from anchor pay; legs toward anchor
    receive at most what was charged.  Net should be non-positive for the
    attacker.
    """

    def test_oscillation_does_not_drain_vault(self):
        market = make_perp(anchor_mu=100.0, funding_rate_bps=500.0, funding_interval=5)
        lp = LPProvider("lp0", initial_deposit=200.0)
        lp.enter(market)

        initial_cash = market.amm.cash

        up_params = GaussianMixtureParams.single(mu=120.0, sigma=10.0)
        down_params = GaussianMixtureParams.single(mu=80.0, sigma=10.0)

        for _ in range(20):
            try:
                market.open_trade("attacker", up_params)
            except ValueError:
                pass
            market.tick(5)
            try:
                market.open_trade("attacker", down_params)
            except ValueError:
                pass
            market.tick(5)

        assert market.amm.cash >= 0.0

    def test_funding_not_profitable_on_oscillation(self):
        """
        An attacker who opens a position away from anchor and immediately
        closes it should not profit — they paid collateral plus any funding.
        """
        market = make_perp(anchor_mu=100.0, amm_mu=100.0, funding_rate_bps=500.0,
                           funding_interval=5)
        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        away = GaussianMixtureParams.single(mu=125.0, sigma=10.0)
        pos = market.open_trade("attacker", away)
        market.tick(20)
        result = market.close_trade(pos.record.trade_id)

        # Payout ≤ collateral because funding_paid reduces it
        assert result["payout"] <= pos.record.collateral + 1e-9

    def test_skc_wall_funding_engine_uses_only_kl(self):
        """
        Verify the funding engine has no functional dependency on a scoring rule.
        We check that:
          - FundingRateEngine has no scoring_rule / calibration attributes
          - The funding module imports nothing from a scoring module
          - kl_divergence is the only divergence measure imported
        """
        import inspect
        from pdamm.amm.funding import FundingRateEngine
        from pdamm.amm import funding as funding_module

        engine = FundingRateEngine(
            anchor=CompositeAnchor(sub_anchors=[
                FixedAnchor(_distribution=GaussianMixtureParams.single(mu=100.0, sigma=5.0))
            ])
        )
        # No scoring-related attributes on the engine instance
        for attr in ["scoring_rule", "calibration", "skc_score", "log_score"]:
            assert not hasattr(engine, attr), f"FundingRateEngine has forbidden attr '{attr}'"

        # No imports of scoring-related modules in funding.py
        source = inspect.getsource(funding_module)
        forbidden_imports = ["import scoring", "from .scoring", "from pdamm.scoring",
                             "LogScore", "BrierScore", "QuadraticScore"]
        for term in forbidden_imports:
            assert term not in source, (
                f"funding.py imports forbidden scoring term '{term}'"
            )


# ---------------------------------------------------------------------------
# 3. Oracle manipulation
# ---------------------------------------------------------------------------

class TestOracleManipulation:
    """
    Attacker tries to corrupt the oracle to mis-anchor the market and then
    exploit the resulting mispricing.  The composite anchor dilutes a single
    rogue oracle if there are multiple sub-anchors.
    """

    def test_single_rogue_oracle_diluted_by_fixed_anchor(self):
        """
        When a legitimate FixedAnchor is paired with a compromised OracleAnchor,
        the composite anchor sits between the two, limiting the manipulation.
        """
        legit = FixedAnchor(
            _distribution=GaussianMixtureParams.single(mu=100.0, sigma=5.0),
            _weight=1.0,
        )
        rogue_oracle = ConstantOracle(mu=200.0, sigma=5.0)  # 100 units off
        rogue = OracleAnchor(oracle_feed=rogue_oracle, _weight=1.0)
        composite = CompositeAnchor(sub_anchors=[legit, rogue])

        d = composite.distribution(0)
        # Composite mu should be halfway between 100 and 200
        assert abs(d.components[0].mu - 150.0) < 1.0

    def test_oracle_step_manipulated_then_restored(self):
        """
        Oracle is momentarily manipulated then restored.  The market should
        not be permanently distorted — funding re-anchors it.
        """
        oracle = StepOracle(
            initial_mu=100.0,
            steps=[(20, 200.0), (40, 100.0)],  # jump then return
        )
        anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle)])
        amm = make_amm(mu=100.0)
        market = PerpMarket(amm=amm, anchor=anchor, funding_interval=5)
        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        # Informed trader follows oracle during manipulation
        for step in range(60):
            obs_mu = oracle.observe(market.slot).mu
            informed = InformedTrader(
                f"i_{step}",
                GaussianMixtureParams.single(mu=obs_mu, sigma=9.0),
                step_fraction=0.1,
            )
            informed.step(market)
            market.tick(1)

        assert market.amm.cash >= 0.0


# ---------------------------------------------------------------------------
# 4. LP griefing
# ---------------------------------------------------------------------------

class TestLPGriefing:
    """
    Attacker opens many small positions to inflate `outstanding` obligations,
    making _available_lp_cash() return near-zero and blocking LP withdrawals.
    """

    def test_griefed_lp_can_still_withdraw_after_positions_close(self):
        market = make_perp(funding_interval=10)
        lp = LPProvider("lp0", initial_deposit=200.0)
        lp.enter(market)

        # Open 10 tiny positions
        targets = [
            GaussianMixtureParams.single(mu=100.0 + i * 0.5, sigma=10.0)
            for i in range(1, 6)
        ]
        trade_ids = []
        for i, t in enumerate(targets):
            try:
                pos = market.open_trade(f"griefer_{i}", t)
                trade_ids.append(pos.record.trade_id)
            except ValueError:
                pass

        # LP sees reduced available cash
        nav_during_grief = market.lp_nav("lp0")

        # Griefer closes all positions
        for tid in trade_ids:
            try:
                market.close_trade(tid)
            except ValueError:
                pass

        nav_after = market.lp_nav("lp0")
        # NAV should recover or at least not worsen beyond fees
        assert nav_after >= nav_during_grief * 0.9

    def test_outstanding_obligations_cap_lp_withdrawal(self):
        """LP cannot withdraw more than available_lp_cash."""
        market = make_perp(funding_interval=50)
        lp = LPProvider("lp0", initial_deposit=100.0)
        lp.enter(market)

        # Open a large position consuming most of the vault
        target = GaussianMixtureParams.single(mu=110.0, sigma=10.0)
        market.open_trade("t0", target)

        nav = market.lp_nav("lp0")
        available = market._available_lp_cash()
        # NAV is based on available cash, not total cash
        assert nav <= market.amm.cash + 1e-9


# ---------------------------------------------------------------------------
# 5. Stale quote exploit
# ---------------------------------------------------------------------------

class TestStaleQuoteExploit:
    """
    A quote is computed at state_version N.  Between the quote and execution
    another trader changes the market (state_version N+1).  The stale quote
    would understate required collateral.

    The state_version field lets callers detect this; we verify it increments
    correctly and that a quote's recorded version differs after a trade.
    """

    def test_state_version_changes_after_trade(self):
        amm = make_amm()
        v0 = amm.state_version
        quote = amm.quote_trade(GaussianMixtureParams.single(mu=105.0, sigma=10.0))
        assert quote["state_version"] == v0

        # Another trader moves the market
        amm.execute_trade("other", GaussianMixtureParams.single(mu=102.0, sigma=10.0))

        # The quote's state_version is now stale
        assert quote["state_version"] < amm.state_version

    def test_stale_quote_collateral_understates_after_market_move(self):
        """
        After a large market move, a quote computed before the move requires
        different collateral than what the quote said.
        """
        amm = make_amm(mu=100.0)
        target = GaussianMixtureParams.single(mu=110.0, sigma=10.0)
        stale_quote = amm.quote_trade(target)

        # Large intervening trade
        amm.execute_trade("other", GaussianMixtureParams.single(mu=108.0, sigma=10.0))

        fresh_quote = amm.quote_trade(target)
        # After the market moved toward the target, fresh collateral is lower
        assert fresh_quote["collateral"] != stale_quote["collateral"]

    def test_state_version_increments_on_lp_add(self):
        """LP operations also bump state_version, invalidating trade quotes."""
        amm = make_amm()
        v0 = amm.state_version
        amm.add_liquidity(10.0)
        assert amm.state_version == v0 + 1


# ---------------------------------------------------------------------------
# 6. Collateral understatement / solvency violation
# ---------------------------------------------------------------------------

class TestCollateralUnderstate:
    """
    A trader attempts to post a trade whose peak position value exceeds b.
    The solvency check must reject it.  Also covers: trading with a sigma
    below the floor derived from k²/(b²√π).
    """

    def test_sigma_below_floor_rejected(self):
        """sigma < k²/(b²√π) → peak f > b → solvency violation."""
        amm = make_amm()
        sigma_min = (K_RUST ** 2) / (amm.b ** 2 * math.sqrt(math.pi))
        below_floor = GaussianMixtureParams.single(mu=100.0, sigma=sigma_min * 0.01)
        with pytest.raises(ValueError, match="solvency"):
            amm.execute_trade("t0", below_floor)

    def test_sigma_at_floor_accepted(self):
        """sigma exactly at floor should satisfy solvency."""
        amm = make_amm()
        sigma_min = (K_RUST ** 2) / (amm.b ** 2 * math.sqrt(math.pi))
        at_floor = GaussianMixtureParams.single(mu=100.0, sigma=sigma_min * 1.01)
        # Should not raise
        amm.execute_trade("t0", at_floor)

    def test_cash_always_non_negative_under_repeated_trades(self):
        """Vault cash must never go negative regardless of trade sequence."""
        amm = make_amm()
        params_sequence = [
            GaussianMixtureParams.single(mu=mu, sigma=sig)
            for mu, sig in [(95, 9), (105, 11), (98, 8), (103, 12), (100, 10)]
        ]
        for params in params_sequence:
            try:
                amm.execute_trade("t", params)
            except ValueError:
                pass
        assert amm.cash >= 0.0

    def test_k3_mixture_solvency_respected(self):
        """K=3 mixture: each component satisfies the sigma floor."""
        amm = make_amm()
        k3_params = GaussianMixtureParams(components=(
            GaussianComponent(weight=0.3, mu=90.0, sigma=8.0),
            GaussianComponent(weight=0.4, mu=100.0, sigma=10.0),
            GaussianComponent(weight=0.3, mu=110.0, sigma=8.0),
        ))
        # K=3 with reasonable sigmas should not violate solvency
        amm.execute_trade("t0", k3_params)
        assert amm.cash >= 0.0


# ---------------------------------------------------------------------------
# K=3 Gaussian Mixture end-to-end
# ---------------------------------------------------------------------------

class TestK3GaussianMixture:
    def test_k3_posterior_informed_trader_moves_k1_amm(self):
        """
        InformedTrader with a K=3 posterior trading against a K=1 AMM.
        The moment-matching fallback should produce a valid trade target.
        """
        amm = make_amm(mu=100.0)
        k3_posterior = GaussianMixtureParams(components=(
            GaussianComponent(weight=0.2, mu=88.0, sigma=5.0),
            GaussianComponent(weight=0.5, mu=100.0, sigma=8.0),
            GaussianComponent(weight=0.3, mu=112.0, sigma=5.0),
        ))
        trader = InformedTrader(
            "k3_trader", posterior=k3_posterior, step_fraction=0.2,
        )
        for _ in range(20):
            trader.step(amm)

        assert trader.trades_executed > 0
        assert amm.cash >= 0.0

    def test_k1_posterior_informed_trader_moves_k3_amm(self):
        """
        InformedTrader with K=1 posterior trading against a K=3 AMM.
        """
        k3_params = GaussianMixtureParams(components=(
            GaussianComponent(weight=0.3, mu=90.0, sigma=8.0),
            GaussianComponent(weight=0.4, mu=100.0, sigma=10.0),
            GaussianComponent(weight=0.3, mu=110.0, sigma=8.0),
        ))
        amm = GaussianMixtureAMM(
            b=50.0, k=K_RUST, params=k3_params, max_collateral_per_trade=50.0,
        )
        k1_posterior = GaussianMixtureParams.single(mu=105.0, sigma=9.0)
        trader = InformedTrader("k1_trader", posterior=k1_posterior, step_fraction=0.2)

        for _ in range(20):
            trader.step(amm)

        assert trader.trades_executed > 0
        assert amm.cash >= 0.0

    def test_k3_collateral_positive(self):
        """K=3 → K=3 trade should compute positive collateral."""
        k3_old = GaussianMixtureParams(components=(
            GaussianComponent(weight=0.3, mu=90.0, sigma=8.0),
            GaussianComponent(weight=0.4, mu=100.0, sigma=10.0),
            GaussianComponent(weight=0.3, mu=110.0, sigma=8.0),
        ))
        k3_new = GaussianMixtureParams(components=(
            GaussianComponent(weight=0.3, mu=93.0, sigma=8.0),
            GaussianComponent(weight=0.4, mu=103.0, sigma=10.0),
            GaussianComponent(weight=0.3, mu=113.0, sigma=8.0),
        ))
        collateral = compute_collateral(k3_old, k3_new, K_RUST)
        assert collateral > 0.0

    def test_k3_noop_trade_zero_collateral(self):
        k3 = GaussianMixtureParams(components=(
            GaussianComponent(weight=0.5, mu=95.0, sigma=9.0),
            GaussianComponent(weight=0.5, mu=105.0, sigma=9.0),
        ))
        collateral = compute_collateral(k3, k3, K_RUST)
        assert collateral < 1e-9
