"""
K-component Gaussian Mixture AMM — Layer 1.

State: weights w_i, means mu_i, sigmas sigma_i, liquidity b, scaling k.
The position function f(x) = k * sum_i w_i * N(x; mu_i, sigma_i).
Collateral for a trade is -min_x( f_new(x) - f_old(x) ).
The solvency invariant is max_x f(x) <= b, enforced on every state change.
"""

from __future__ import annotations

import math
import numpy as np
from dataclasses import dataclass, field
from typing import Optional

from .numerical import find_global_minimum, MinimumResult


# ---------------------------------------------------------------------------
# Distribution types
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class GaussianComponent:
    weight: float   # unnormalized; normalised internally
    mu: float
    sigma: float

    def __post_init__(self) -> None:
        if self.sigma <= 0:
            raise ValueError(f"sigma must be positive, got {self.sigma}")
        if self.weight < 0:
            raise ValueError(f"weight must be non-negative, got {self.weight}")


@dataclass(frozen=True)
class GaussianMixtureParams:
    """Immutable mixture parameterization."""
    components: tuple[GaussianComponent, ...]

    def __post_init__(self) -> None:
        if not self.components:
            raise ValueError("at least one component required")
        total = sum(c.weight for c in self.components)
        if total <= 0:
            raise ValueError("weights must sum to a positive value")

    @staticmethod
    def single(mu: float, sigma: float) -> "GaussianMixtureParams":
        return GaussianMixtureParams(components=(GaussianComponent(1.0, mu, sigma),))

    @property
    def normalized_weights(self) -> tuple[float, ...]:
        total = sum(c.weight for c in self.components)
        return tuple(c.weight / total for c in self.components)

    def pdf(self, x: float) -> float:
        weights = self.normalized_weights
        total = 0.0
        for w, c in zip(weights, self.components):
            z = (x - c.mu) / c.sigma
            total += w * math.exp(-0.5 * z * z) / (c.sigma * math.sqrt(2.0 * math.pi))
        return total

    def pdf_array(self, xs: np.ndarray) -> np.ndarray:
        weights = self.normalized_weights
        out = np.zeros_like(xs, dtype=float)
        for w, c in zip(weights, self.components):
            z = (xs - c.mu) / c.sigma
            out += w * np.exp(-0.5 * z * z) / (c.sigma * math.sqrt(2.0 * math.pi))
        return out

    def search_bounds(self, tail_sigmas: float = 6.0) -> tuple[float, float]:
        mus = [c.mu for c in self.components]
        sigs = [c.sigma for c in self.components]
        lo = min(m - tail_sigmas * s for m, s in zip(mus, sigs))
        hi = max(m + tail_sigmas * s for m, s in zip(mus, sigs))
        return lo, hi


# ---------------------------------------------------------------------------
# Lambda (scaling factor) — mirrors Rust fixed_calculate_lambda
#
# For a K=1 Normal:  lambda = k * sqrt(2 * sigma * sqrt(pi))
# For a K>1 Gaussian mixture with pdf p(x):
#   lambda = k / l2_norm(p)  where l2_norm^2 = ∫ p(x)^2 dx
#   ∫ p(x)^2 dx = Σ_i Σ_j w_i w_j * N(mu_i; mu_j, sqrt(sigma_i^2+sigma_j^2))
# For K=1 both formulae agree: sqrt(1/(2*sigma*sqrt(pi))) = 1/sqrt(2*sigma*sqrt(pi))
# ---------------------------------------------------------------------------

def _mixture_l2_norm_sq(params: GaussianMixtureParams) -> float:
    """∫ pdf(x)^2 dx for a Gaussian mixture (closed form via product-of-Gaussians)."""
    weights = params.normalized_weights
    components = params.components
    result = 0.0
    for i, (wi, ci) in enumerate(zip(weights, components)):
        for j, (wj, cj) in enumerate(zip(weights, components)):
            s_ij = math.sqrt(ci.sigma ** 2 + cj.sigma ** 2)
            z = (ci.mu - cj.mu) / s_ij
            cross = math.exp(-0.5 * z * z) / (s_ij * math.sqrt(2.0 * math.pi))
            result += wi * wj * cross
    return result


def _compute_lambda(params: GaussianMixtureParams, k: float) -> float:
    """Scale factor: k / l2_norm(mixture pdf).  Matches Rust fixed_calculate_lambda."""
    l2_norm = math.sqrt(_mixture_l2_norm_sq(params))
    return k / l2_norm


# ---------------------------------------------------------------------------
# Position function: f(x) = lambda(params, k) * pdf(x)
# ---------------------------------------------------------------------------

def position_value(x: float, params: GaussianMixtureParams, k: float) -> float:
    lam = _compute_lambda(params, k)
    return lam * params.pdf(x)


def position_difference(x: float, old_p: GaussianMixtureParams, new_p: GaussianMixtureParams, k: float) -> float:
    return position_value(x, new_p, k) - position_value(x, old_p, k)


# ---------------------------------------------------------------------------
# Collateral kernel
# ---------------------------------------------------------------------------

def compute_collateral(
    old_params: GaussianMixtureParams,
    new_params: GaussianMixtureParams,
    k: float,
    coarse_samples: int = 512,
) -> float:
    """
    Collateral = -min_x( f_new(x) - f_old(x) )
    If the minimum is non-negative, the trade improves everyone → zero collateral.
    """
    lo_old, hi_old = old_params.search_bounds()
    lo_new, hi_new = new_params.search_bounds()
    lo, hi = min(lo_old, lo_new), max(hi_old, hi_new)

    def diff(x: float) -> float:
        return position_difference(x, old_params, new_params, k)

    result = find_global_minimum(diff, lo, hi, coarse_samples=coarse_samples)
    return max(0.0, -result.value)


def max_position_value(params: GaussianMixtureParams, k: float, coarse_samples: int = 512) -> float:
    """
    max_x f(x) — used to verify the solvency invariant (max f ≤ b).
    For K=1 Normal this is lambda/(sigma*sqrt(2π)); for mixtures we maximise numerically.
    """
    lam = _compute_lambda(params, k)
    components = params.components
    if len(components) == 1:
        # Closed form for K=1 Normal
        c = components[0]
        return lam / (c.sigma * math.sqrt(2.0 * math.pi))

    lo, hi = params.search_bounds()

    def neg_f(x: float) -> float:
        return -position_value(x, params, k)

    result = find_global_minimum(neg_f, lo, hi, coarse_samples=coarse_samples)
    return -result.value


# ---------------------------------------------------------------------------
# Trade record
# ---------------------------------------------------------------------------

@dataclass
class TradeRecord:
    trade_id: int
    trader_id: str
    old_params: GaussianMixtureParams
    new_params: GaussianMixtureParams
    k_at_trade: float
    collateral: float
    fee: float
    state_version_before: int


# ---------------------------------------------------------------------------
# AMM state
# ---------------------------------------------------------------------------

@dataclass
class GaussianMixtureAMM:
    """
    Single-market Normal/GMM AMM.

    b: liquidity backing (max payout obligation the LP can cover)
    k: scaling factor; position f(x) = k * mixture.pdf(x)
    params: current distribution parameters
    state_version: increments on every trade and LP operation (quote staleness guard)
    taker_fee_bps: fee in basis points
    """

    b: float
    k: float
    params: GaussianMixtureParams
    taker_fee_bps: int = 100
    min_taker_fee: float = 0.001
    max_collateral_per_trade: float = 10.0

    # internal state
    state_version: int = field(default=0, init=False)
    trades: list[TradeRecord] = field(default_factory=list, init=False)
    cash: float = field(default=0.0, init=False)
    fees_accrued: float = field(default=0.0, init=False)

    def __post_init__(self) -> None:
        self._check_solvency(self.params)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def quote_trade(
        self,
        new_params: GaussianMixtureParams,
        coarse_samples: int = 512,
    ) -> dict:
        """
        Compute the collateral and fee for a trade to new_params.
        Returns a quote dict (does not mutate state).
        """
        collateral = compute_collateral(self.params, new_params, self.k, coarse_samples)
        fee = max(collateral * self.taker_fee_bps / 10_000.0, self.min_taker_fee)
        total_debit = collateral + fee
        return {
            "state_version": self.state_version,
            "old_params": self.params,
            "new_params": new_params,
            "collateral": collateral,
            "fee": fee,
            "total_debit": total_debit,
            "k_at_quote": self.k,
        }

    def execute_trade(
        self,
        trader_id: str,
        new_params: GaussianMixtureParams,
        max_total_debit: Optional[float] = None,
        coarse_samples: int = 512,
    ) -> TradeRecord:
        """
        Execute a trade.  Raises ValueError if:
        - collateral exceeds max_collateral_per_trade
        - max_total_debit slippage guard fires
        - new_params would violate solvency (max f(x) > b)
        """
        self._check_solvency(new_params)

        collateral = compute_collateral(self.params, new_params, self.k, coarse_samples)
        if collateral > self.max_collateral_per_trade:
            raise ValueError(
                f"collateral {collateral:.4f} exceeds max_collateral_per_trade "
                f"{self.max_collateral_per_trade:.4f}"
            )

        fee = max(collateral * self.taker_fee_bps / 10_000.0, self.min_taker_fee)
        total_debit = collateral + fee

        if max_total_debit is not None and total_debit > max_total_debit:
            raise ValueError(
                f"total_debit {total_debit:.4f} exceeds slippage guard {max_total_debit:.4f}"
            )

        record = TradeRecord(
            trade_id=len(self.trades),
            trader_id=trader_id,
            old_params=self.params,
            new_params=new_params,
            k_at_trade=self.k,
            collateral=collateral,
            fee=fee,
            state_version_before=self.state_version,
        )

        self.params = new_params
        self.cash += collateral
        self.fees_accrued += fee
        self.state_version += 1
        self.trades.append(record)
        return record

    def settle_trade(self, trade_id: int, outcome: float) -> float:
        """
        Compute trader payout given a resolved outcome.
        payout = collateral + (f_new(outcome) - f_old(outcome))  [using k_at_trade]
        """
        record = self.trades[trade_id]
        f_new = position_value(outcome, record.new_params, record.k_at_trade)
        f_old = position_value(outcome, record.old_params, record.k_at_trade)
        payout = record.collateral + (f_new - f_old)
        return max(0.0, payout)

    def add_liquidity(self, amount: float) -> float:
        """
        Add collateral to the pool, scaling b and k proportionally.
        Returns new LP share quantity.
        """
        if self.b <= 0:
            raise ValueError("b must be positive before adding liquidity")
        ratio = (self.b + amount) / self.b
        self.b *= ratio
        self.k *= ratio
        self.cash += amount
        self.state_version += 1
        return amount  # simplified: 1 share per unit of collateral

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _check_solvency(self, params: GaussianMixtureParams) -> None:
        peak = max_position_value(params, self.k)
        if peak > self.b * (1.0 + 1e-9):
            raise ValueError(
                f"solvency violation: max f(x) = {peak:.6f} > b = {self.b:.6f}"
            )

    @property
    def current_mu(self) -> float:
        """Convenience: weighted mean of current distribution."""
        weights = self.params.normalized_weights
        return sum(w * c.mu for w, c in zip(weights, self.params.components))

    @property
    def current_sigma(self) -> float:
        """Convenience: weighted std-dev (approx for K>1)."""
        weights = self.params.normalized_weights
        mean = self.current_mu
        variance = sum(
            w * (c.sigma ** 2 + (c.mu - mean) ** 2)
            for w, c in zip(weights, self.params.components)
        )
        return math.sqrt(variance)
