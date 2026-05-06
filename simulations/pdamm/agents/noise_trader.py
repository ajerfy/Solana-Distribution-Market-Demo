"""
NoiseTrader — random direction, fixed step size.

Trades toward a random perturbation of the current AMM distribution each step.
Used to provide liquidity demand and test that the funding rate punishes
uninformed flow that pushes the market away from the anchor.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Optional

import numpy as np

from ..amm.gaussian_mixture import GaussianMixtureAMM, GaussianMixtureParams, GaussianComponent
from ..amm.perp_market import PerpMarket


@dataclass
class NoiseTrader:
    trader_id: str
    mu_shock_scale: float = 2.0      # std dev of random mu perturbation each step
    sigma_shock_scale: float = 0.5   # std dev of random sigma perturbation
    max_fraction_of_b: float = 0.03
    seed: int = 0

    trades_executed: int = field(default=0, init=False)
    _rng: np.random.Generator = field(init=False)

    def __post_init__(self) -> None:
        self._rng = np.random.default_rng(self.seed)

    def step(self, market: PerpMarket) -> Optional[dict]:
        amm = market.amm
        c = amm.params.components[0]
        new_mu = c.mu + self._rng.normal(0.0, self.mu_shock_scale)
        new_sigma = max(0.5, c.sigma + self._rng.normal(0.0, self.sigma_shock_scale))
        new_params = GaussianMixtureParams.single(mu=new_mu, sigma=new_sigma)

        try:
            quote = amm.quote_trade(new_params)
            max_col = amm.b * self.max_fraction_of_b
            if quote["collateral"] > max_col:
                return None
            market.open_trade(
                trader_id=self.trader_id,
                new_params=new_params,
                max_total_debit=quote["total_debit"] * 1.1,
            )
            self.trades_executed += 1
            return quote
        except ValueError:
            return None
