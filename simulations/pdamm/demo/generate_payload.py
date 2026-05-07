"""
Generate a demo_market.json payload for any ResolvingMarketDef.

Produces the exact JSON schema the Android app expects, including
Borsh-encoded serialized_instruction_hex compatible with the Rust
normal-v1-program pack_instruction format.

Usage:
    python -m pdamm.demo.generate_payload ufc328_chimaev_win [--out path/to/demo_market.json]
    python -m pdamm.demo.generate_payload sol_price_expiry
"""

from __future__ import annotations

import argparse
import json
import math
import os
import struct
from typing import Optional

from ..amm.gaussian_mixture import (
    GaussianMixtureAMM,
    GaussianMixtureParams,
    compute_collateral,
    position_value,
)
from ..amm.numerical import find_global_minimum
from .markets import RESOLVING_MARKETS, ResolvingMarketDef, _amm

# ---------------------------------------------------------------------------
# Fixed-point helpers (mirrors src/fixed_point.rs: SCALE = 1_000_000_000)
# ---------------------------------------------------------------------------

_SCALE = 1_000_000_000


def _to_fixed_raw(value: float) -> int:
    """Convert float to Fixed i128 raw value (9 decimal places)."""
    return round(value * _SCALE)


def _fixed_bytes(value: float) -> bytes:
    """Encode a float as a 16-byte little-endian i128 Fixed value."""
    raw = _to_fixed_raw(value)
    # i128 → signed 16-byte little-endian
    return raw.to_bytes(16, byteorder="little", signed=True)


def _u64_bytes(value: int) -> bytes:
    return struct.pack("<Q", value)


def _u32_bytes(value: int) -> bytes:
    return struct.pack("<I", value)


# ---------------------------------------------------------------------------
# Instruction encoder
# mirrors programs/normal-v1-program/src/lib.rs pack_instruction(Trade)
#
# Layout (tag=0x01 + push_quote_envelope):
#   [1]  tag = 0x01
#   [32] market_id
#   [8]  expected_market_version  u64 LE
#   [16] new_mu                   i128 LE Fixed
#   [16] new_sigma                i128 LE Fixed
#   [16] collateral_required      i128 LE Fixed
#   [16] fee_paid                 i128 LE Fixed
#   [16] total_debit              i128 LE Fixed
#   [16] max_total_debit          i128 LE Fixed
#   [4]  taker_fee_bps            u32 LE
#   [16] min_taker_fee            i128 LE Fixed
#   [16] search_lower_bound       i128 LE Fixed
#   [16] search_upper_bound       i128 LE Fixed
#   [4]  coarse_samples           u32 LE
#   [4]  refine_samples           u32 LE
#   [8]  quote_slot               u64 LE
#   [8]  quote_expiry_slot        u64 LE
# ---------------------------------------------------------------------------

def _pack_trade_instruction(
    market_id: bytes,
    market_version: int,
    new_mu: float,
    new_sigma: float,
    collateral: float,
    fee: float,
    total_debit: float,
    taker_fee_bps: int,
    min_taker_fee: float,
    lower_bound: float,
    upper_bound: float,
    coarse_samples: int,
    refine_samples: int,
    quote_slot: int,
    quote_expiry_slot: int,
) -> str:
    buf = bytearray()
    buf += b"\x01"                              # Trade tag
    buf += market_id                            # 32 bytes
    buf += _u64_bytes(market_version)
    buf += _fixed_bytes(new_mu)
    buf += _fixed_bytes(new_sigma)
    buf += _fixed_bytes(collateral)
    buf += _fixed_bytes(fee)
    buf += _fixed_bytes(total_debit)
    buf += _fixed_bytes(total_debit)            # max_total_debit = total_debit
    buf += _u32_bytes(taker_fee_bps)
    buf += _fixed_bytes(min_taker_fee)
    buf += _fixed_bytes(lower_bound)
    buf += _fixed_bytes(upper_bound)
    buf += _u32_bytes(coarse_samples)
    buf += _u32_bytes(refine_samples)
    buf += _u64_bytes(quote_slot)
    buf += _u64_bytes(quote_expiry_slot)
    return buf.hex()


# ---------------------------------------------------------------------------
# Curve point helpers
# ---------------------------------------------------------------------------

CURVE_SAMPLES = 49
QUOTE_SLOT = 2
QUOTE_EXPIRY_SLOT = 12
TAKER_FEE_BPS = 100
MIN_TAKER_FEE = 0.001
COARSE_SAMPLES = 4096
REFINE_SAMPLES = 4096


def _curve_points(
    current_params: GaussianMixtureParams,
    proposed_params: GaussianMixtureParams,
    k: float,
    n_sigma: float = 3.5,
    n_points: int = CURVE_SAMPLES,
) -> list[dict]:
    mu = current_params.components[0].mu
    sigma = current_params.components[0].sigma
    lo = mu - n_sigma * sigma
    hi = mu + n_sigma * sigma
    points = []
    for i in range(n_points):
        x = lo + (hi - lo) * i / (n_points - 1)
        cur_v = position_value(x, current_params, k)
        pro_v = position_value(x, proposed_params, k)
        points.append({
            "x": f"{x:.9f}",
            "current": f"{cur_v:.9f}",
            "proposed": f"{pro_v:.9f}",
            "edge": f"{pro_v - cur_v:.9f}",
        })
    return points


def _search_bounds(
    old_params: GaussianMixtureParams,
    new_params: GaussianMixtureParams,
    tail_sigmas: float = 6.0,
) -> tuple[float, float]:
    all_mus = [c.mu for c in old_params.components] + [c.mu for c in new_params.components]
    all_sigs = [c.sigma for c in old_params.components] + [c.sigma for c in new_params.components]
    lo = min(all_mus) - tail_sigmas * max(all_sigs)
    hi = max(all_mus) + tail_sigmas * max(all_sigs)
    return lo, hi


# ---------------------------------------------------------------------------
# Payload builders
# ---------------------------------------------------------------------------

def _build_preset(
    amm: GaussianMixtureAMM,
    market_id: bytes,
    preset_id: str,
    label: str,
    target_mu: float,
    target_sigma: float,
) -> dict:
    proposed = GaussianMixtureParams.single(mu=target_mu, sigma=target_sigma)
    quote = amm.quote_trade(proposed)
    collateral = quote["collateral"]
    fee = max(collateral * TAKER_FEE_BPS / 10_000.0, MIN_TAKER_FEE)
    total_debit = collateral + fee
    lo, hi = _search_bounds(amm.params, proposed)

    hex_instr = _pack_trade_instruction(
        market_id=market_id,
        market_version=amm.state_version,
        new_mu=target_mu,
        new_sigma=target_sigma,
        collateral=collateral,
        fee=fee,
        total_debit=total_debit,
        taker_fee_bps=TAKER_FEE_BPS,
        min_taker_fee=MIN_TAKER_FEE,
        lower_bound=lo,
        upper_bound=hi,
        coarse_samples=COARSE_SAMPLES,
        refine_samples=REFINE_SAMPLES,
        quote_slot=QUOTE_SLOT,
        quote_expiry_slot=QUOTE_EXPIRY_SLOT,
    )

    return {
        "id": preset_id,
        "label": label,
        "target_mu_display": f"{target_mu:.9f}",
        "target_sigma_display": f"{target_sigma:.9f}",
        "collateral_required_display": f"{collateral:.9f}",
        "fee_paid_display": f"{fee:.9f}",
        "total_debit_display": f"{total_debit:.9f}",
        "max_total_debit_display": f"{total_debit:.9f}",
        "quote_expiry_slot": QUOTE_EXPIRY_SLOT,
        "serialized_instruction_hex": hex_instr,
        "curve_points": _curve_points(amm.params, proposed, amm.k),
    }


def _build_quote_grid(amm: GaussianMixtureAMM, market_id: bytes) -> list[dict]:
    mu0 = amm.params.components[0].mu
    sigma0 = amm.params.components[0].sigma
    entries = []
    for dmu in [-8, -4, 0, 4, 8]:
        for dsig in [-1.5, 0, 1.5]:
            tmu = mu0 + dmu
            tsig = max(2.0, sigma0 + dsig)
            gid = f"grid-{tmu}-{tsig}"
            label = f"Grid quote for mu {tmu}, sigma {tsig}"
            try:
                entries.append(_build_preset(amm, market_id, gid, label, tmu, tsig))
            except (ValueError, Exception):
                pass
    return entries


def _build_risk_grid(amm: GaussianMixtureAMM, proposed: GaussianMixtureParams, n: int = 25) -> list[dict]:
    mu = proposed.components[0].mu
    sigma = proposed.components[0].sigma
    lo = mu - 4 * sigma
    hi = mu + 4 * sigma
    proposed_quote = amm.quote_trade(proposed)
    collateral = proposed_quote["collateral"]
    backing = amm.b
    rows = []
    for i in range(n):
        outcome = lo + (hi - lo) * i / (n - 1)
        trader_liability = collateral
        lp_residual = max(0.0, backing + collateral - trader_liability)
        rows.append({
            "outcome_display": f"{outcome:.9f}",
            "trader_liability_display": f"{trader_liability:.9f}",
            "lp_residual_after_traders_display": f"{lp_residual:.9f}",
            "maker_buffer_display": f"{lp_residual:.9f}",
        })
    return rows


def _build_backend_trace(amm: GaussianMixtureAMM, trace_mu: float, trace_sigma: float) -> dict:
    old = amm.params.components[0]
    lo, hi = _search_bounds(
        amm.params,
        GaussianMixtureParams.single(mu=trace_mu, sigma=trace_sigma),
    )
    proposed = GaussianMixtureParams.single(mu=trace_mu, sigma=trace_sigma)
    quote = amm.quote_trade(proposed)
    collateral = quote["collateral"]
    fee = max(collateral * TAKER_FEE_BPS / 10_000.0, MIN_TAKER_FEE)
    total_debit = collateral + fee
    vault_before = amm.cash
    vault_after = amm.cash + collateral
    return {
        "market_version": amm.state_version,
        "old_mu_display": f"{old.mu:.9f}",
        "old_sigma_display": f"{old.sigma:.9f}",
        "new_mu_display": f"{trace_mu:.9f}",
        "new_sigma_display": f"{trace_sigma:.9f}",
        "k_display": f"{amm.k:.9f}",
        "search_lower_bound_display": f"{lo:.9f}",
        "search_upper_bound_display": f"{hi:.9f}",
        "max_loss_outcome_display": f"{lo:.9f}",
        "max_directional_loss_display": f"{collateral:.9f}",
        "collateral_required_display": f"{collateral:.9f}",
        "fee_paid_display": f"{fee:.9f}",
        "total_debit_display": f"{total_debit:.9f}",
        "vault_cash_before_display": f"{vault_before:.9f}",
        "vault_cash_after_display": f"{vault_after:.9f}",
        "locked_collateral_before_display": "0.000000000",
        "locked_collateral_after_display": f"{collateral:.9f}",
        "worst_case_liability_before_display": "0.000000000",
        "worst_case_liability_after_display": f"{collateral:.9f}",
        "maker_buffer_before_display": f"{vault_before:.9f}",
        "maker_buffer_after_display": f"{vault_after - collateral:.9f}",
    }


def _build_settlement_waterfall(amm: GaussianMixtureAMM, outcome: float, collateral: float) -> dict:
    trader_claims = collateral
    lp_residual = max(0.0, amm.cash + collateral - trader_claims)
    return {
        "outcome_display": f"{outcome:.9f}",
        "trader_claims_display": f"{trader_claims:.9f}",
        "lp_residual_claim_display": f"{lp_residual:.9f}",
        "protocol_dust_display": "0.000000000",
    }


def _stub_regime_indexes() -> list[dict]:
    return [
        {
            "id": "macro-rates",
            "title": "Macro Rates Index",
            "description": "Composite of rate-sensitive markets",
            "current_score_display": "52.300000000",
            "change_display": "+1.200000000",
            "constituents": [
                {"market_id": "fed_rate_decision", "label": "Fed Funds Rate", "weight_display": "0.600000000"},
            ],
            "history": [
                {"slot": i * 10, "score_display": f"{50.0 + i * 0.2:.9f}"} for i in range(10)
            ],
            "long_quote": {
                "side": "Long", "target_score_display": "55.000000000",
                "collateral_display": "1.200000000", "memo_payload": "regime-long|index=macro-rates",
            },
            "short_quote": {
                "side": "Short", "target_score_display": "48.000000000",
                "collateral_display": "0.900000000", "memo_payload": "regime-short|index=macro-rates",
            },
        }
    ]


def _stub_perp_section(market_def: ResolvingMarketDef) -> dict:
    mu = market_def.initial_mu
    sigma = market_def.initial_sigma
    anchor_mu = mu + 2.0
    lo = mu - 3 * sigma
    hi = mu + 3 * sigma
    n = 28
    curve_points = []
    for i in range(n):
        x = lo + (hi - lo) * i / (n - 1)
        amm_v = math.exp(-0.5 * ((x - mu) / sigma) ** 2) / (sigma * math.sqrt(2 * math.pi))
        anch_v = math.exp(-0.5 * ((x - anchor_mu) / sigma) ** 2) / (sigma * math.sqrt(2 * math.pi))
        curve_points.append({
            "x": f"{x:.9f}",
            "amm": f"{amm_v:.9f}",
            "anchor": f"{anch_v:.9f}",
            "edge": f"{amm_v - anch_v:.9f}",
        })
    funding_path = [
        {"slot": i * 5, "amm_mu_display": f"{mu:.9f}", "anchor_mu_display": f"{anchor_mu:.9f}",
         "kl_display": f"{0.02 * (1 + i * 0.1):.9f}", "funding_rate_display": f"{0.0002 * (1 + i * 0.05):.9f}"}
        for i in range(8)
    ]
    long_col = 1.5
    short_col = 1.2
    return {
        "symbol": market_def.id.upper()[:12] + "-PERP",
        "title": f"Perpetual {market_def.title}",
        "status": "Active",
        "slot": 24,
        "next_funding_slot": 30,
        "funding_interval": 10,
        "mark_price_display": f"{mu:.9f}",
        "anchor_mu_display": f"{anchor_mu:.9f}",
        "anchor_sigma_display": f"{sigma:.9f}",
        "amm_mu_display": f"{mu:.9f}",
        "amm_sigma_display": f"{sigma:.9f}",
        "kl_display": f"{0.025:.9f}",
        "spot_funding_rate_display": f"{0.00025:.9f}",
        "vault_cash_display": f"{market_def.b * 1.05:.9f}",
        "lp_nav_display": f"{market_def.b:.9f}",
        "available_lp_cash_display": f"{market_def.b:.9f}",
        "open_positions": 2,
        "total_lp_shares_display": f"{market_def.b:.9f}",
        "curve_points": curve_points,
        "funding_path": funding_path,
        "long_quote": {
            "side": "Long",
            "target_mu_display": f"{mu + 3:.9f}",
            "target_sigma_display": f"{sigma - 1:.9f}",
            "collateral_required_display": f"{long_col:.9f}",
            "fee_paid_display": f"{long_col * 0.01:.9f}",
            "total_debit_display": f"{long_col * 1.01:.9f}",
            "estimated_funding_display": f"{-0.0005:.9f}",
            "close_mark_display": f"{anchor_mu:.9f}",
            "memo_payload": f"perp-market-demo|side=long|target_mu={mu+3:.9f}|collateral={long_col:.9f}",
        },
        "short_quote": {
            "side": "Short",
            "target_mu_display": f"{mu - 3:.9f}",
            "target_sigma_display": f"{sigma + 1:.9f}",
            "collateral_required_display": f"{short_col:.9f}",
            "fee_paid_display": f"{short_col * 0.01:.9f}",
            "total_debit_display": f"{short_col * 1.01:.9f}",
            "estimated_funding_display": f"{0.0003:.9f}",
            "close_mark_display": f"{anchor_mu:.9f}",
            "memo_payload": f"perp-market-demo|side=short|target_mu={mu-3:.9f}|collateral={short_col:.9f}",
        },
        "positions": [
            {
                "id": "perp-long-001", "side": "Long",
                "entry_mu_display": f"{mu + 3:.9f}",
                "collateral_display": f"{long_col:.9f}",
                "funding_paid_display": "0.000000000",
                "funding_received_display": "0.000500000",
                "mark_payout_display": f"{long_col * 1.1:.9f}",
                "status": "Open",
            },
            {
                "id": "perp-short-001", "side": "Short",
                "entry_mu_display": f"{mu - 3:.9f}",
                "collateral_display": f"{short_col:.9f}",
                "funding_paid_display": "0.000300000",
                "funding_received_display": "0.000000000",
                "mark_payout_display": f"{short_col * 0.95:.9f}",
                "status": "Open",
            },
        ],
    }


def generate_payload(market_def: ResolvingMarketDef, market_id_hex: Optional[str] = None) -> dict:
    """
    Build a complete demo_market.json-compatible payload for a ResolvingMarketDef.
    """
    amm = market_def.build_amm()
    market_id = bytes.fromhex(market_id_hex) if market_id_hex else (
        bytes([hash(market_def.id) & 0xFF] * 32)
    )
    if len(market_id) != 32:
        raise ValueError("market_id must be 32 bytes")

    c = amm.params.components[0]
    mu, sigma = c.mu, c.sigma

    # Market section
    market_section = {
        "title": market_def.title,
        "status": "Active",
        "market_id_hex": market_id.hex(),
        "state_version": amm.state_version,
        "current_mu_display": f"{mu:.9f}",
        "current_sigma_display": f"{sigma:.9f}",
        "k_display": f"{amm.k:.9f}",
        "backing_display": f"{amm.b:.9f}",
        "taker_fee_bps": TAKER_FEE_BPS,
        "min_taker_fee_display": f"{MIN_TAKER_FEE:.9f}",
        "maker_fees_earned_display": "0.000000000",
        "maker_deposit_display": f"{amm.b:.9f}",
        "total_trades": 0,
        "max_open_trades": 64,
        "expiry_slot": 1_000_000,
        "demo_quote_slot": QUOTE_SLOT,
        "demo_quote_expiry_slot": QUOTE_EXPIRY_SLOT,
        "coarse_samples": COARSE_SAMPLES,
        "refine_samples": REFINE_SAMPLES,
    }

    # Liquidity
    liquidity = {
        "maker_deposit_display": f"{amm.b:.9f}",
        "vault_cash_display": f"{amm.cash:.9f}",
        "accrued_fees_display": "0.000000000",
        "current_k_display": f"{amm.k:.9f}",
        "total_lp_shares_display": f"{amm.b:.9f}",
        "locked_trader_collateral_display": "0.000000000",
        "worst_case_trader_liability_display": "0.000000000",
        "available_maker_buffer_display": f"{amm.cash:.9f}",
        "open_trades": 0,
        "max_open_trades": 64,
        "lp_controls_status": "Normal",
    }

    # Trace using first preset as the trace trade
    trace_mu = market_def.presets[0]["target_mu"]
    trace_sigma = market_def.presets[0]["target_sigma"]
    backend_trace = _build_backend_trace(amm, trace_mu, trace_sigma)

    # Preview liquidity (after trace trade)
    trace_proposed = GaussianMixtureParams.single(mu=trace_mu, sigma=trace_sigma)
    trace_collateral = amm.quote_trade(trace_proposed)["collateral"]
    preview_liquidity = {
        **liquidity,
        "vault_cash_display": f"{amm.cash + trace_collateral:.9f}",
        "locked_trader_collateral_display": f"{trace_collateral:.9f}",
        "worst_case_trader_liability_display": f"{trace_collateral:.9f}",
        "available_maker_buffer_display": f"{amm.cash:.9f}",
        "open_trades": 1,
    }

    # Risk grid
    risk_grid = _build_risk_grid(amm, trace_proposed)

    # Settlement waterfall
    settlement_waterfall = _build_settlement_waterfall(amm, market_def.true_outcome, trace_collateral)

    # Presets
    presets = []
    for p in market_def.presets:
        try:
            preset = _build_preset(
                amm, market_id,
                p.get("id", p["label"].lower().replace(" ", "_").replace("(", "").replace(")", "").replace("%", "").replace("/", "_")),
                p["label"],
                p["target_mu"],
                p["target_sigma"],
            )
            presets.append(preset)
        except (ValueError, Exception):
            pass

    # Quote grid
    quote_grid = _build_quote_grid(amm, market_id)

    # Regime indexes (stub)
    regime_indexes = _stub_regime_indexes()

    # Perps (stub from market params)
    perps = _stub_perp_section(market_def)

    return {
        "market": market_section,
        "liquidity": liquidity,
        "preview_liquidity": preview_liquidity,
        "backend_trace": backend_trace,
        "risk_grid": risk_grid,
        "settlement_waterfall": settlement_waterfall,
        "presets": presets,
        "quote_grid": quote_grid,
        "regime_indexes": regime_indexes,
        "perps": perps,
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv=None):
    parser = argparse.ArgumentParser(description="Generate demo_market.json for a market.")
    parser.add_argument("market_id", help="Market ID from RESOLVING_MARKETS")
    parser.add_argument(
        "--out", default=None,
        help="Output path (default: apps/android-demo/.../demo_market.json)"
    )
    parser.add_argument("--stdout", action="store_true", help="Print JSON to stdout")
    args = parser.parse_args(argv)

    if args.market_id not in RESOLVING_MARKETS:
        print(f"Unknown market '{args.market_id}'. Available: {list(RESOLVING_MARKETS.keys())}")
        raise SystemExit(1)

    mdef = RESOLVING_MARKETS[args.market_id]
    print(f"Generating payload for: {mdef.title}")

    payload = generate_payload(mdef)
    json_str = json.dumps(payload, indent=2)

    if args.stdout:
        print(json_str)
        return

    if args.out:
        out_path = args.out
    else:
        # Default: write to Android assets as demo_<market_id>.json (avoids clobbering demo_market.json)
        here = os.path.dirname(os.path.abspath(__file__))
        repo_root = os.path.dirname(os.path.dirname(os.path.dirname(here)))
        out_path = os.path.join(
            repo_root,
            "apps", "android-demo", "app", "src", "main", "assets",
            f"demo_{args.market_id}.json",
        )

    with open(out_path, "w") as f:
        f.write(json_str)
    print(f"Written to {out_path}")
    print(f"  Presets: {len(payload['presets'])}")
    print(f"  Quote grid entries: {len(payload['quote_grid'])}")
    print(f"  mu={payload['market']['current_mu_display']}  σ={payload['market']['current_sigma_display']}")


if __name__ == "__main__":
    main()
