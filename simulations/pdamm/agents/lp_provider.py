"""
LPProvider — passive liquidity provider agent.

Deposits an initial amount at market open, optionally rebalances if NAV
drifts significantly from deposit, and can withdraw at a specified slot.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from ..amm.perp_market import PerpMarket


@dataclass
class LPProvider:
    lp_id: str
    initial_deposit: float
    rebalance_threshold: float = 0.20   # rebalance if NAV drifts > 20% from deposit
    withdraw_at_slot: Optional[int] = None

    deposited: float = field(default=0.0, init=False)
    withdrawn: float = field(default=0.0, init=False)
    total_shares: float = field(default=0.0, init=False)

    def enter(self, market: PerpMarket) -> None:
        pos = market.add_liquidity(self.lp_id, self.initial_deposit)
        self.deposited = self.initial_deposit
        self.total_shares = pos.shares

    def step(self, market: PerpMarket) -> Optional[dict]:
        if self.withdraw_at_slot is not None and market.slot >= self.withdraw_at_slot:
            return self._exit(market)

        nav = market.lp_nav(self.lp_id)
        if nav <= 0:
            return None

        drift = abs(nav - self.deposited) / self.deposited
        if drift > self.rebalance_threshold:
            # Top up if NAV is below deposit target
            if nav < self.deposited:
                top_up = self.deposited - nav
                pos = market.add_liquidity(self.lp_id, top_up)
                self.deposited += top_up
                self.total_shares = pos.shares
                return {"action": "top_up", "amount": top_up}

        return None

    def _exit(self, market: PerpMarket) -> dict:
        if self.lp_id not in market.lp_positions:
            return {"action": "already_exited"}
        pos = market.lp_positions[self.lp_id]
        result = market.remove_liquidity(self.lp_id, pos.shares)
        self.withdrawn = result["cash_returned"]
        return {"action": "exit", **result}

    @property
    def pnl(self) -> float:
        return self.withdrawn - self.deposited
