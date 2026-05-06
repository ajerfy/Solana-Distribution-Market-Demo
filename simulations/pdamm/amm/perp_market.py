"""
Perpetual Distribution Market — wires Layers 1–3 together.

A PerpMarket wraps a GaussianMixtureAMM (Layer 1) with:
  - A CompositeAnchor (Layer 2) that provides the reference distribution
  - A FundingRateEngine (Layer 3) that charges/credits open positions
  - An EMAnchor that is updated on every trade and LP event
  - Slot-based lifecycle (no expiry / no oracle-resolved settlement)

LP exit mechanics:
  LPs deposit collateral and receive shares proportional to their share
  of the pool.  They can redeem shares at any time for their pro-rata
  fraction of the vault minus outstanding trader obligations.
  Outstanding obligations = sum of max possible payouts across all open
  positions (conservative upper bound: collateral_i for each position).

Slot progression:
  The market advances one slot per tick() call.  In a real deployment each
  slot would correspond to a Solana slot (~400ms).  Tests drive the clock
  explicitly.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Optional

from .gaussian_mixture import (
    GaussianMixtureAMM,
    GaussianMixtureParams,
    GaussianComponent,
    TradeRecord,
    compute_collateral,
    position_value,
)
from .anchor import CompositeAnchor, EMAnchor, kl_divergence
from .funding import FundingRateEngine, FundingConfig


# ---------------------------------------------------------------------------
# LP share ledger
# ---------------------------------------------------------------------------

@dataclass
class LPPosition:
    lp_id: str
    shares: float
    deposit: float
    entry_slot: int


# ---------------------------------------------------------------------------
# Open position tracker (wraps TradeRecord with funding state)
# ---------------------------------------------------------------------------

@dataclass
class OpenPosition:
    record: TradeRecord
    funding_paid: float = 0.0
    funding_received: float = 0.0
    closed: bool = False


# ---------------------------------------------------------------------------
# Perpetual market
# ---------------------------------------------------------------------------

@dataclass
class PerpMarket:
    """
    Parameters
    ----------
    amm:            The base Layer-1 AMM.
    anchor:         Composite anchor (Layer 2).
    funding_config: Funding rate parameters (Layer 3).
    funding_interval: How many slots between automatic funding settlements.
    ema_half_life:  Half-life (in slots) for the EMA sub-anchor that tracks
                    the AMM's own recent distribution history.
    """
    amm: GaussianMixtureAMM
    anchor: CompositeAnchor
    funding_config: FundingConfig = field(default_factory=FundingConfig)
    funding_interval: int = 100
    ema_half_life: float = 500.0

    # runtime state
    slot: int = field(default=0, init=False)
    open_positions: dict[int, OpenPosition] = field(default_factory=dict, init=False)
    lp_positions: dict[str, LPPosition] = field(default_factory=dict, init=False)
    total_lp_shares: float = field(default=0.0, init=False)
    funding_history: list[dict] = field(default_factory=list, init=False)

    # internal
    _funding_engine: FundingRateEngine = field(init=False)
    _ema_anchor: EMAnchor = field(init=False)
    _next_funding_slot: int = field(default=0, init=False)

    def __post_init__(self) -> None:
        self._ema_anchor = EMAnchor(half_life_slots=self.ema_half_life)
        self._ema_anchor.update(self.amm.params, self.slot)
        self._funding_engine = FundingRateEngine(
            anchor=self.anchor,
            config=self.funding_config,
        )
        self._next_funding_slot = self.funding_interval

    # ------------------------------------------------------------------
    # Clock
    # ------------------------------------------------------------------

    def tick(self, n: int = 1) -> None:
        """Advance the market clock by n slots, settling funding if due."""
        for _ in range(n):
            self.slot += 1
            if self.slot >= self._next_funding_slot:
                self._settle_funding()
                self._next_funding_slot = self.slot + self.funding_interval

    # ------------------------------------------------------------------
    # Trading
    # ------------------------------------------------------------------

    def open_trade(
        self,
        trader_id: str,
        new_params: GaussianMixtureParams,
        max_total_debit: Optional[float] = None,
    ) -> OpenPosition:
        """
        Open a new position by executing a trade on the AMM.
        Registers the position with the funding engine.
        Updates the EMA anchor with the new distribution.
        """
        record = self.amm.execute_trade(
            trader_id=trader_id,
            new_params=new_params,
            max_total_debit=max_total_debit,
        )
        self._funding_engine.register_position(record)
        self._ema_anchor.update(self.amm.params, self.slot)

        pos = OpenPosition(record=record)
        self.open_positions[record.trade_id] = pos
        return pos

    def close_trade(self, trade_id: int) -> dict:
        """
        Close an open position.  In a perpetual market there is no
        oracle-resolved outcome — instead the trader receives a mark-to-market
        payout based on the current AMM distribution vs their entry.

        Payout formula (mark-to-market):
          payout = collateral + (f_current(midpoint) - f_entry(midpoint))
        where midpoint = average of current anchor mu and position entry mu.

        This is conservative — it uses a single representative outcome rather
        than the full distribution.  A full settlement would require an
        oracle observation.
        """
        if trade_id not in self.open_positions:
            raise ValueError(f"position {trade_id} not open")

        pos = self.open_positions[trade_id]
        if pos.closed:
            raise ValueError(f"position {trade_id} already closed")

        record = pos.record
        anchor_params = self.anchor.distribution(self.slot)
        midpoint = anchor_params.components[0].mu

        f_current = position_value(midpoint, self.amm.params, record.k_at_trade)
        f_entry = position_value(midpoint, record.old_params, record.k_at_trade)
        payout = max(0.0, record.collateral + (f_current - f_entry) - pos.funding_paid)

        pos.closed = True
        self._funding_engine.close_position(trade_id)

        return {
            "trade_id": trade_id,
            "payout": payout,
            "collateral_returned": payout,
            "funding_paid": pos.funding_paid,
            "funding_received": pos.funding_received,
        }

    # ------------------------------------------------------------------
    # LP management
    # ------------------------------------------------------------------

    def add_liquidity(self, lp_id: str, amount: float) -> LPPosition:
        """Deposit collateral, receive pro-rata shares."""
        new_shares = self._shares_for_deposit(amount)
        self.amm.add_liquidity(amount)
        self.total_lp_shares += new_shares

        if lp_id in self.lp_positions:
            existing = self.lp_positions[lp_id]
            existing.shares += new_shares
            existing.deposit += amount
        else:
            pos = LPPosition(
                lp_id=lp_id,
                shares=new_shares,
                deposit=amount,
                entry_slot=self.slot,
            )
            self.lp_positions[lp_id] = pos

        self._ema_anchor.update(self.amm.params, self.slot)
        return self.lp_positions[lp_id]

    def remove_liquidity(self, lp_id: str, shares: float) -> dict:
        """Redeem shares for pro-rata vault value minus trader obligations."""
        if lp_id not in self.lp_positions:
            raise ValueError(f"LP {lp_id} has no position")

        pos = self.lp_positions[lp_id]
        if shares > pos.shares + 1e-12:
            raise ValueError(f"LP {lp_id} only has {pos.shares:.4f} shares")

        shares = min(shares, pos.shares)
        fraction = shares / self.total_lp_shares
        available = self._available_lp_cash()
        redemption = fraction * available

        # Scale down AMM proportionally
        scale = 1.0 - fraction
        self.amm.b *= scale
        self.amm.k *= scale
        self.amm.cash -= redemption

        pos.shares -= shares
        self.total_lp_shares -= shares
        if pos.shares < 1e-12:
            del self.lp_positions[lp_id]

        return {"lp_id": lp_id, "shares_redeemed": shares, "cash_returned": redemption}

    # ------------------------------------------------------------------
    # Queries
    # ------------------------------------------------------------------

    def current_kl_from_anchor(self) -> float:
        anchor_params = self.anchor.distribution(self.slot)
        return kl_divergence(self.amm.params, anchor_params)

    def spot_funding_rate(self) -> float:
        return self._funding_engine.current_funding_rate(self.amm.params, self.slot)

    def mark_price(self) -> float:
        """Expected value of the current distribution — the 'price' in mu space."""
        return self.amm.current_mu

    def lp_nav(self, lp_id: str) -> float:
        """Net asset value of an LP position."""
        if lp_id not in self.lp_positions:
            return 0.0
        pos = self.lp_positions[lp_id]
        if self.total_lp_shares <= 0:
            return 0.0
        fraction = pos.shares / self.total_lp_shares
        return fraction * self._available_lp_cash()

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _settle_funding(self) -> None:
        trades = [p.record for p in self.open_positions.values() if not p.closed]
        result = self._funding_engine.settle_funding(
            current_slot=self.slot,
            current_params=self.amm.params,
            trades=trades,
            vault_cash=self.amm.cash,
        )
        # Apply funding payments to open positions
        for trade_id, payment in result["payments"].items():
            if trade_id in self.open_positions:
                pos = self.open_positions[trade_id]
                if payment > 0:
                    pos.funding_paid += payment
                    self.amm.cash += payment        # trader pays → LP pool grows
                else:
                    pos.funding_received += abs(payment)
                    self.amm.cash -= abs(payment)   # LP pool pays → trader receives

        self.funding_history.append(result)

    def _available_lp_cash(self) -> float:
        """
        Cash available for LP redemptions = total vault minus worst-case
        outstanding trader obligations (conservatively: sum of collaterals).
        """
        outstanding = sum(
            p.record.collateral for p in self.open_positions.values() if not p.closed
        )
        return max(0.0, self.amm.cash - outstanding)

    def _shares_for_deposit(self, amount: float) -> float:
        """New LP shares proportional to deposit vs current pool value."""
        if self.total_lp_shares <= 0 or self.amm.b <= 0:
            return amount  # first depositor: 1 share per unit
        pool_value = self._available_lp_cash() + amount
        return self.total_lp_shares * amount / max(pool_value - amount, 1e-12)
