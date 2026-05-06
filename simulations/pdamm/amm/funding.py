"""
Layer 3 — Funding Rate.

The funding rate keeps a perpetual distribution market honest by charging
traders whose open positions push the market distribution away from the
anchor, and paying traders whose positions are aligned with the anchor.

Design constraints (from arXiv 2306.04305 Appendix C):
  - The SKC self-calibration scoring rule MUST be walled off from the
    funding rate computation.  If funding rate is computed from the same
    score used for calibration, a trader can extract unbounded expected
    profit by oscillating the market.  Here they are kept strictly separate:
    funding uses KL(AMM || anchor), calibration is not implemented in the
    funding path at all.

Funding mechanics:
  1. Each slot, compute KL(current_amm_params || anchor_params).
  2. Compute funding_rate = funding_rate_bps * KL / slots_per_period.
  3. For each open position, charge or credit proportional to how much
     their position contributes to the KL divergence from anchor.
     Specifically: positions that moved the market AWAY from anchor pay;
     positions that moved it TOWARD anchor receive.
  4. Payments flow from traders → LP pool (when paying) or LP pool →
     traders (when receiving), keeping the vault balanced.

Per-position funding:
  Each position records its old_params and new_params.  The contribution
  of trade i to the current AMM state's KL from anchor is approximated by
  the signed KL delta that trade induced:
    delta_KL_i = KL(new_params_i || anchor) - KL(old_params_i || anchor)
  Positive delta_KL_i → trader pushed market away → pays funding.
  Negative delta_KL_i → trader moved market toward anchor → receives.

Funding cap: each position's funding is capped at its posted collateral to
prevent positions from going into negative equity from funding alone.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import TYPE_CHECKING

from .anchor import CompositeAnchor, kl_divergence
from .gaussian_mixture import GaussianMixtureParams, TradeRecord

if TYPE_CHECKING:
    from .perp_market import PerpMarket


# ---------------------------------------------------------------------------
# Funding config
# ---------------------------------------------------------------------------

@dataclass
class FundingConfig:
    """
    funding_rate_bps:   maximum annualised funding rate in basis points
                        applied to the KL divergence per period
    slots_per_period:   how often funding is settled (e.g. 1 = every slot,
                        3600 = hourly at ~1 slot/sec)
    kl_cap:             maximum KL divergence used in funding calculation
                        (prevents runaway funding during extreme dislocations)
    min_kl_threshold:   KL below this is treated as zero (no funding charge)
    """
    funding_rate_bps: float = 200.0      # 2% per period at max KL
    slots_per_period: int = 100
    kl_cap: float = 5.0
    min_kl_threshold: float = 0.01


# ---------------------------------------------------------------------------
# Funding state tracked per open position
# ---------------------------------------------------------------------------

@dataclass
class PositionFundingState:
    trade_id: int
    trader_id: str
    cumulative_funding_paid: float = 0.0
    cumulative_funding_received: float = 0.0
    last_settled_slot: int = 0


# ---------------------------------------------------------------------------
# Funding rate engine
# ---------------------------------------------------------------------------

@dataclass
class FundingRateEngine:
    """
    Computes and settles funding for all open positions.

    Walled-off design: this class only uses KL divergence for pricing.
    It has no access to any scoring rule or calibration mechanism,
    satisfying the arXiv 2306.04305 Appendix C safety requirement.
    """
    anchor: CompositeAnchor
    config: FundingConfig = field(default_factory=FundingConfig)

    _position_states: dict[int, PositionFundingState] = field(
        default_factory=dict, init=False
    )
    _total_funding_collected: float = field(default=0.0, init=False)
    _total_funding_paid_out: float = field(default=0.0, init=False)

    def register_position(self, record: TradeRecord, opened_at_slot: int) -> None:
        """Call when a new trade is opened.  opened_at_slot prevents charging
        funding for slots before the position existed."""
        self._position_states[record.trade_id] = PositionFundingState(
            trade_id=record.trade_id,
            trader_id=record.trader_id,
            last_settled_slot=opened_at_slot,
        )

    def close_position(self, trade_id: int) -> None:
        """Call when a position is closed / settled."""
        self._position_states.pop(trade_id, None)

    def settle_funding(
        self,
        current_slot: int,
        current_params: GaussianMixtureParams,
        trades: list[TradeRecord],
        vault_cash: float,
    ) -> dict:
        """
        Settle funding for all open positions at current_slot.

        Returns a dict:
          {
            "slot": int,
            "kl_from_anchor": float,
            "funding_rate": float,
            "payments": {trade_id: signed_amount},   # + = trader pays, - = trader receives
            "net_lp_funding": float,
          }
        """
        anchor_params = self.anchor.distribution(current_slot)
        kl = kl_divergence(current_params, anchor_params)
        kl_clamped = min(kl, self.config.kl_cap)

        funding_rate = (
            self.config.funding_rate_bps / 10_000.0
            * kl_clamped / self.config.slots_per_period
        ) if kl_clamped >= self.config.min_kl_threshold else 0.0

        payments: dict[int, float] = {}
        net_lp = 0.0

        for trade_id, pos_state in list(self._position_states.items()):
            slots_since = current_slot - pos_state.last_settled_slot
            if slots_since <= 0:
                continue

            record = next((t for t in trades if t.trade_id == trade_id), None)
            if record is None:
                continue

            # Signed KL contribution: did this trade increase or decrease KL?
            kl_before = kl_divergence(record.old_params, anchor_params)
            kl_after = kl_divergence(record.new_params, anchor_params)
            delta_kl = kl_after - kl_before

            # Funding payment proportional to KL contribution
            raw_payment = delta_kl * funding_rate * slots_since * record.collateral

            # Cap: trader can't pay more than collateral, can't receive more than collateral
            payment = max(-record.collateral, min(record.collateral, raw_payment))

            payments[trade_id] = payment
            net_lp += payment  # LP receives when trader pays, LP pays when trader receives

            if payment > 0:
                pos_state.cumulative_funding_paid += payment
            else:
                pos_state.cumulative_funding_received += abs(payment)
            pos_state.last_settled_slot = current_slot

        self._total_funding_collected += max(0.0, net_lp)
        self._total_funding_paid_out += max(0.0, -net_lp)

        return {
            "slot": current_slot,
            "kl_from_anchor": kl,
            "funding_rate": funding_rate,
            "payments": payments,
            "net_lp_funding": net_lp,
        }

    def current_funding_rate(self, current_params: GaussianMixtureParams, slot: int) -> float:
        """Spot funding rate: what fraction of collateral is charged per slot at this KL."""
        anchor_params = self.anchor.distribution(slot)
        kl = min(kl_divergence(current_params, anchor_params), self.config.kl_cap)
        if kl < self.config.min_kl_threshold:
            return 0.0
        return self.config.funding_rate_bps / 10_000.0 * kl / self.config.slots_per_period

    @property
    def total_funding_collected(self) -> float:
        return self._total_funding_collected

    @property
    def total_funding_paid_out(self) -> float:
        return self._total_funding_paid_out
