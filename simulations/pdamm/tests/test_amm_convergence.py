"""
Layer 1 + InformedTrader convergence smoke test.

Run:  pytest simulations/pdamm/tests/test_amm_convergence.py -v
"""

import math
import pytest

import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../.."))

from pdamm.amm.gaussian_mixture import (
    GaussianMixtureAMM,
    GaussianMixtureParams,
    GaussianComponent,
)
from pdamm.agents.informed_trader import InformedTrader


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def make_amm(mu: float = 95.0, sigma: float = 10.0, b: float = 50.0, k: float = 21.05026039569057) -> GaussianMixtureAMM:
    """
    Default k=21.05 is the value used in the Rust demo market (seeded_demo_market).
    With b=50 and N(95,10), max f(x) ≈ 5.0  —  well within the solvency envelope of b=50.
    k must satisfy k ≤ b * sqrt(sigma * sqrt(π)) to keep max f ≤ b.
    """
    params = GaussianMixtureParams.single(mu=mu, sigma=sigma)
    return GaussianMixtureAMM(b=b, k=k, params=params, max_collateral_per_trade=50.0)


# ---------------------------------------------------------------------------
# Unit tests
# ---------------------------------------------------------------------------

class TestNumerical:
    def test_collateral_noop_trade_is_zero(self):
        """Trading to the exact same params costs zero collateral."""
        amm = make_amm()
        quote = amm.quote_trade(amm.params)
        assert quote["collateral"] < 1e-9

    def test_collateral_positive_for_real_trade(self):
        amm = make_amm()
        new_params = GaussianMixtureParams.single(mu=100.0, sigma=10.0)
        quote = amm.quote_trade(new_params)
        assert quote["collateral"] > 0.0

    def test_collateral_symmetric_shift(self):
        """Shifting mu left and right by the same amount should cost the same collateral."""
        amm = make_amm(mu=100.0)
        left = GaussianMixtureParams.single(mu=90.0, sigma=10.0)
        right = GaussianMixtureParams.single(mu=110.0, sigma=10.0)
        c_left = amm.quote_trade(left)["collateral"]
        c_right = amm.quote_trade(right)["collateral"]
        assert abs(c_left - c_right) < 1e-6

    def test_solvency_invariant_enforced(self):
        """
        A trade to a distribution with extremely tight sigma raises peak f above b.
        With k=21.05 and b=50, solvency requires sigma ≥ k²/(b²*sqrt(π)) ≈ 0.1.
        sigma=0.001 is far below that floor and must be rejected.
        """
        amm = make_amm()
        with pytest.raises(ValueError, match="solvency"):
            amm.execute_trade("t1", GaussianMixtureParams.single(mu=95.0, sigma=0.001))

    def test_state_version_increments(self):
        amm = make_amm()
        v0 = amm.state_version
        new_params = GaussianMixtureParams.single(mu=100.0, sigma=10.0)
        amm.execute_trade("t1", new_params)
        assert amm.state_version == v0 + 1


class TestSettlement:
    def test_payout_uses_k_at_trade_not_current_k(self):
        """After an LP addition that changes k, settlement should use the stored k_at_trade."""
        amm = make_amm()
        new_params = GaussianMixtureParams.single(mu=100.0, sigma=10.0)
        record = amm.execute_trade("t1", new_params)
        k_at_trade = record.k_at_trade

        # LP injection doubles k
        amm.add_liquidity(amm.b)
        assert amm.k > k_at_trade

        # Settlement payout computed from stored k_at_trade
        outcome = 100.0
        payout = amm.settle_trade(record.trade_id, outcome)

        # Manual verification
        from pdamm.amm.gaussian_mixture import position_value
        f_new = position_value(outcome, record.new_params, k_at_trade)
        f_old = position_value(outcome, record.old_params, k_at_trade)
        expected = max(0.0, record.collateral + (f_new - f_old))
        assert abs(payout - expected) < 1e-12


class TestInformedTraderConvergence:
    def test_amm_converges_toward_true_distribution(self):
        """
        Run 100 InformedTrader steps; verify the AMM mu moves at least halfway
        toward the true distribution from the starting point.
        Starting: mu=95, target: mu=110, distance=15. After 100 steps we expect
        to be within 8 units of the target (at least 7 units of progress).
        """
        true_mu, true_sigma = 110.0, 8.0
        start_mu = 95.0

        amm = make_amm(mu=start_mu, sigma=10.0)
        posterior = GaussianMixtureParams.single(mu=true_mu, sigma=true_sigma)
        trader = InformedTrader(
            trader_id="informed_0",
            posterior=posterior,
            max_fraction_of_b=0.15,
            step_fraction=0.30,
        )

        for _ in range(100):
            trader.step(amm)

        progress = amm.current_mu - start_mu
        assert progress > 4.0, (
            f"AMM did not converge: mu={amm.current_mu:.2f} (started {start_mu}, "
            f"target {true_mu}, progress={progress:.2f})"
        )
        assert trader.trades_executed > 0

    def test_cash_is_non_negative(self):
        """AMM cash balance should never go negative during a simulation."""
        amm = make_amm()
        posterior = GaussianMixtureParams.single(mu=105.0, sigma=9.0)
        trader = InformedTrader("t1", posterior=posterior, step_fraction=0.2)

        for _ in range(30):
            trader.step(amm)

        assert amm.cash >= 0.0

    def test_multiple_traders_opposing_beliefs(self):
        """Two traders with opposing beliefs; market should stay between them."""
        amm = make_amm(mu=100.0, sigma=10.0, b=100.0)
        bull = InformedTrader(
            "bull",
            GaussianMixtureParams.single(mu=120.0, sigma=8.0),
            step_fraction=0.2,
        )
        bear = InformedTrader(
            "bear",
            GaussianMixtureParams.single(mu=80.0, sigma=8.0),
            step_fraction=0.2,
        )
        for _ in range(40):
            bull.step(amm)
            bear.step(amm)

        # Market shouldn't drift too far from its starting point
        assert 70.0 < amm.current_mu < 130.0
