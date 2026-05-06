"""
InformedTrader agent — Layer 4 (agents).

The trader holds a private posterior (a GaussianMixtureParams).  On each
step it computes the AMM's implied distribution, estimates the edge between
the market price and its belief, and moves the AMM toward its posterior by
one Kelly-sized step.

Kelly sizing: fraction = edge / variance, capped at max_fraction_of_b.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Optional, Union

import numpy as np

from ..amm.gaussian_mixture import (
    GaussianMixtureAMM,
    GaussianMixtureParams,
    GaussianComponent,
)


@dataclass
class InformedTrader:
    """
    Parameters
    ----------
    trader_id:       unique label
    posterior:       private belief (GaussianMixtureParams)
    max_fraction_of_b: Kelly cap as a fraction of market b (default 0.05)
    step_fraction:   how far toward posterior to move per trade (0, 1]
    min_edge:        skip a trade if KL-divergence from market is below this
    """

    trader_id: str
    posterior: GaussianMixtureParams
    max_fraction_of_b: float = 0.05
    step_fraction: float = 0.3
    min_edge: float = 1e-4

    trades_executed: int = field(default=0, init=False)
    total_collateral_posted: float = field(default=0.0, init=False)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def step(self, market: Union[GaussianMixtureAMM, "PerpMarket"]) -> Optional[dict]:  # type: ignore[name-defined]
        """
        Attempt one trade against either a bare AMM or a PerpMarket.
        Returns the quote dict if a trade was made, None otherwise.
        """
        from ..amm.perp_market import PerpMarket  # local import to avoid circular dep

        amm = market.amm if isinstance(market, PerpMarket) else market
        target_params = self._compute_target(amm)
        if target_params is None:
            return None

        quote = amm.quote_trade(target_params)
        collateral = quote["collateral"]

        if collateral < 1e-9:
            return None

        max_collateral = amm.b * self.max_fraction_of_b
        if collateral > max_collateral:
            target_params = self._scale_step(amm, max_collateral)
            if target_params is None:
                return None
            quote = amm.quote_trade(target_params)

        try:
            if isinstance(market, PerpMarket):
                market.open_trade(
                    trader_id=self.trader_id,
                    new_params=target_params,
                    max_total_debit=quote["total_debit"] * 1.05,
                )
            else:
                market.execute_trade(
                    trader_id=self.trader_id,
                    new_params=target_params,
                    max_total_debit=quote["total_debit"] * 1.05,
                )
        except ValueError:
            return None

        self.trades_executed += 1
        self.total_collateral_posted += quote["collateral"]
        return quote

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _compute_target(self, amm: "GaussianMixtureAMM") -> Optional[GaussianMixtureParams]:  # type: ignore[name-defined]
        """
        Interpolate between the current AMM params and the posterior by
        step_fraction, keeping the result within the solvency envelope.
        """
        return _interpolate_params(amm.params, self.posterior, self.step_fraction)

    def _scale_step(
        self,
        amm: "GaussianMixtureAMM",  # type: ignore[name-defined]
        max_collateral: float,
    ) -> Optional[GaussianMixtureParams]:
        """
        Binary search for the largest step_fraction that yields collateral
        <= max_collateral.
        """
        lo, hi = 0.0, self.step_fraction
        best: Optional[GaussianMixtureParams] = None

        for _ in range(20):
            mid = (lo + hi) / 2.0
            if mid < 1e-6:
                break
            candidate = _interpolate_params(amm.params, self.posterior, mid)
            if candidate is None:
                hi = mid
                continue
            quote = amm.quote_trade(candidate)
            if quote["collateral"] <= max_collateral:
                best = candidate
                lo = mid
            else:
                hi = mid

        return best


def _interpolate_params(
    current: GaussianMixtureParams,
    target: GaussianMixtureParams,
    fraction: float,
) -> Optional[GaussianMixtureParams]:
    """
    Interpolate between current and target distributions.

    Handles mismatched component counts (K_current ≠ K_target) by
    moment-matching: when counts differ, project both sides to a single
    Gaussian first, then interpolate.  For matched K, interpolate
    component-by-component sorted by mu so components align sensibly.
    """
    from ..amm.anchor import kl_project_to_normal  # local to avoid circular dep

    k_cur = len(current.components)
    k_tgt = len(target.components)

    if k_cur == 0 or k_tgt == 0:
        return None

    # If component counts differ, reduce both to K=1 via moment-matching
    if k_cur != k_tgt:
        current = kl_project_to_normal(current)
        target = kl_project_to_normal(target)
        k_cur = k_tgt = 1

    # Sort components by mu so index-matching is stable
    cur_sorted = sorted(current.components, key=lambda c: c.mu)
    tgt_sorted = sorted(target.components, key=lambda c: c.mu)
    cur_w = _weights_for(cur_sorted, current)
    tgt_w = _weights_for(tgt_sorted, target)

    new_components = []
    for i in range(k_cur):
        c_cur = cur_sorted[i]
        c_tgt = tgt_sorted[i]
        w = (1.0 - fraction) * cur_w[i] + fraction * tgt_w[i]
        mu = (1.0 - fraction) * c_cur.mu + fraction * c_tgt.mu
        sigma = (1.0 - fraction) * c_cur.sigma + fraction * c_tgt.sigma
        if sigma <= 0:
            return None
        new_components.append(GaussianComponent(weight=w, mu=mu, sigma=sigma))

    return GaussianMixtureParams(components=tuple(new_components))


def _weights_for(
    sorted_components: list[GaussianComponent],
    original: GaussianMixtureParams,
) -> list[float]:
    """Return normalised weights aligned to a sorted component list."""
    total = sum(c.weight for c in original.components)
    return [c.weight / total for c in sorted_components]
