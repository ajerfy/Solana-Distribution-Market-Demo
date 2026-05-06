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
from typing import Optional

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

    def step(self, amm: GaussianMixtureAMM) -> Optional[dict]:
        """
        Attempt one trade.  Returns the quote dict if a trade was made,
        None if the edge was too small or the trade was refused.
        """
        target_params = self._compute_target(amm)
        if target_params is None:
            return None

        # Dry-run quote to check edge and size
        quote = amm.quote_trade(target_params)
        collateral = quote["collateral"]

        if collateral < 1e-9:
            return None  # nothing to do

        max_collateral = amm.b * self.max_fraction_of_b
        if collateral > max_collateral:
            # Try a smaller step toward posterior
            target_params = self._scale_step(amm, max_collateral)
            if target_params is None:
                return None
            quote = amm.quote_trade(target_params)

        try:
            record = amm.execute_trade(
                trader_id=self.trader_id,
                new_params=target_params,
                max_total_debit=quote["total_debit"] * 1.05,  # 5% slippage tolerance
            )
        except ValueError:
            return None

        self.trades_executed += 1
        self.total_collateral_posted += record.collateral
        return quote

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _compute_target(self, amm: GaussianMixtureAMM) -> Optional[GaussianMixtureParams]:
        """
        Interpolate between the current AMM params and the posterior by
        step_fraction, keeping the result within the solvency envelope.
        """
        return _interpolate_params(amm.params, self.posterior, self.step_fraction)

    def _scale_step(
        self,
        amm: GaussianMixtureAMM,
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
    Linear interpolation between two single-component distributions.
    For K=1 this is clean; for K>1 we match components by index.
    Falls back to None if sigma goes non-positive.
    """
    # Align component count — use smaller K for safety
    k = min(len(current.components), len(target.components))
    if k == 0:
        return None

    new_components = []
    cur_w = current.normalized_weights
    tgt_w = target.normalized_weights

    for i in range(k):
        c_cur = current.components[i]
        c_tgt = target.components[i]
        w = (1.0 - fraction) * cur_w[i] + fraction * tgt_w[i]
        mu = (1.0 - fraction) * c_cur.mu + fraction * c_tgt.mu
        sigma = (1.0 - fraction) * c_cur.sigma + fraction * c_tgt.sigma
        if sigma <= 0:
            return None
        new_components.append(GaussianComponent(weight=w, mu=mu, sigma=sigma))

    return GaussianMixtureParams(components=tuple(new_components))
