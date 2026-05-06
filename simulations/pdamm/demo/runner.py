"""
Demo runner — terminal simulation of a resolving or perpetual distribution market.

Usage:
    python -m pdamm.demo.runner [market_id] [--slots N] [--traders T] [--lp]
    python -m pdamm.demo.runner --list

Examples:
    python -m pdamm.demo.runner sol_price_expiry
    python -m pdamm.demo.runner sol_usd_perp --slots 200
    python -m pdamm.demo.runner --list
"""

from __future__ import annotations

import argparse
import math
import sys
import time
from dataclasses import dataclass
from typing import Optional

from ..amm.gaussian_mixture import GaussianMixtureAMM, GaussianMixtureParams, GaussianComponent
from ..amm.perp_market import PerpMarket
from ..agents.informed_trader import InformedTrader
from ..agents.noise_trader import NoiseTrader
from ..demo.markets import (
    RESOLVING_MARKETS,
    PERP_MARKETS,
    list_markets,
    ResolvingMarketDef,
    PerpMarketDef,
)


# ---------------------------------------------------------------------------
# ASCII distribution renderer
# ---------------------------------------------------------------------------

def _ascii_distribution(
    mu: float,
    sigma: float,
    oracle_mu: Optional[float] = None,
    width: int = 60,
    height: int = 7,
    n_sigma: float = 3.5,
) -> list[str]:
    """Render a Gaussian as an ASCII bar chart with optional oracle marker."""
    lo = mu - n_sigma * sigma
    hi = mu + n_sigma * sigma
    xs = [lo + (hi - lo) * i / (width - 1) for i in range(width)]

    def pdf(x: float) -> float:
        return math.exp(-0.5 * ((x - mu) / sigma) ** 2) / (sigma * math.sqrt(2 * math.pi))

    ys = [pdf(x) for x in xs]
    y_max = max(ys) or 1.0
    rows: list[str] = []
    for row in range(height, 0, -1):
        threshold = y_max * row / height
        line = ""
        for i, y in enumerate(ys):
            if oracle_mu is not None and abs(xs[i] - oracle_mu) < (hi - lo) / width:
                line += "│"
            elif y >= threshold:
                line += "█"
            else:
                line += " "
        rows.append(line)
    # x-axis with mu and oracle label
    axis = f"{'─' * width}"
    lo_label = f"{lo:.1f}"
    hi_label = f"{hi:.1f}"
    mu_label = f"μ={mu:.2f}"
    if oracle_mu is not None:
        oracle_label = f"oracle={oracle_mu:.2f}"
        rows.append(f"{lo_label:<{width//3}}{mu_label:^{width//3}}{oracle_label:>{width//3}}")
    else:
        rows.append(f"{lo_label:<{width//2}}{mu_label:>{width//2}}")
    rows.append(axis)
    return rows


# ---------------------------------------------------------------------------
# Resolving market runner
# ---------------------------------------------------------------------------

def run_resolving(
    market_def: ResolvingMarketDef,
    n_slots: int = 120,
    n_informed: int = 3,
    add_noise: bool = True,
    animate: bool = True,
    print_interval: int = 10,
) -> dict:
    amm = market_def.build_amm()

    # Build informed traders with progressively updated beliefs from the oracle
    traders = []
    for i in range(n_informed):
        obs = market_def.oracle.observe(n_slots * (i + 1) // (n_informed + 1))
        posterior = GaussianMixtureParams.single(mu=obs.mu, sigma=obs.sigma)
        traders.append(InformedTrader(
            trader_id=f"informed_{i}",
            posterior=posterior,
            max_fraction_of_b=0.08,
            step_fraction=0.25,
        ))

    # For resolving markets, noise traders use a random posterior rather than PerpMarket.open_trade
    import numpy as np
    rng = np.random.default_rng(77)
    noise_rng = rng  # reused each slot

    history: list[dict] = []
    total_volume = 0.0
    total_trades = 0

    print(f"\n{'═' * 64}")
    print(f"  {market_def.title}")
    print(f"  {market_def.description.splitlines()[0]}")
    print(f"  True outcome: {market_def.true_outcome} {market_def.unit}")
    print(f"{'═' * 64}")

    for slot in range(1, n_slots + 1):
        # Update trader beliefs as oracle moves
        oracle_obs = market_def.oracle.observe(slot)
        for t in traders:
            if slot % (n_slots // n_informed) == 0:
                t.posterior = GaussianMixtureParams.single(
                    mu=oracle_obs.mu, sigma=oracle_obs.sigma
                )

        # Execute trades
        slot_volume = 0.0
        for t in traders:
            q = t.step(amm)
            if q:
                slot_volume += q["collateral"]
                total_trades += 1

        # Noise: small random perturbation via a throwaway InformedTrader
        if add_noise and noise_rng.random() < 0.4:
            c = amm.params.components[0]
            noisy_mu = float(c.mu + noise_rng.normal(0.0, market_def.initial_sigma * 0.15))
            noisy_sigma = max(1.0, float(c.sigma + noise_rng.normal(0.0, 0.5)))
            noise_trader = InformedTrader(
                trader_id="noise",
                posterior=GaussianMixtureParams.single(mu=noisy_mu, sigma=noisy_sigma),
                max_fraction_of_b=0.03,
                step_fraction=0.5,
            )
            q = noise_trader.step(amm)
            if q:
                slot_volume += q["collateral"]
                total_trades += 1

        total_volume += slot_volume

        snap = {
            "slot": slot,
            "mu": amm.current_mu,
            "sigma": amm.current_sigma,
            "oracle_mu": oracle_obs.mu,
            "cash": amm.cash,
            "volume": slot_volume,
        }
        history.append(snap)

        if animate and slot % print_interval == 0:
            _print_resolving_frame(snap, market_def.unit)

    # Final frame
    final = history[-1]
    _print_resolving_frame(final, market_def.unit, final=True)

    error = abs(final["mu"] - market_def.true_outcome)
    print(f"\n{'─' * 64}")
    print(f"  Final μ:        {final['mu']:.4f} {market_def.unit}")
    print(f"  True outcome:   {market_def.true_outcome} {market_def.unit}")
    print(f"  Error:          {error:.4f} ({error/market_def.true_outcome*100:.2f}%)")
    print(f"  Total trades:   {total_trades}")
    print(f"  Total volume:   {total_volume:.2f} collateral units")
    print(f"  Final σ:        {final['sigma']:.4f}")
    print(f"{'─' * 64}\n")

    return {"history": history, "amm": amm, "error": error}


def _print_resolving_frame(snap: dict, unit: str, final: bool = False) -> None:
    label = "FINAL" if final else f"slot {snap['slot']:>4}"
    lines = _ascii_distribution(
        snap["mu"], snap["sigma"], oracle_mu=snap["oracle_mu"], width=56, height=5
    )
    print(f"\n  [{label}]  μ={snap['mu']:.3f} σ={snap['sigma']:.3f}  oracle={snap['oracle_mu']:.3f} {unit}")
    for line in lines:
        print("  " + line)


# ---------------------------------------------------------------------------
# Perpetual market runner
# ---------------------------------------------------------------------------

def run_perp(
    market_def: PerpMarketDef,
    n_slots: int = 200,
    n_informed: int = 2,
    add_noise: bool = True,
    add_lp: bool = True,
    animate: bool = True,
    print_interval: int = 20,
) -> dict:
    market = market_def.build()

    # Build informed traders
    traders = []
    for i in range(n_informed):
        obs = market_def.oracle.observe(0)
        posterior = GaussianMixtureParams.single(mu=obs.mu, sigma=obs.sigma)
        traders.append(InformedTrader(
            trader_id=f"informed_{i}",
            posterior=posterior,
            max_fraction_of_b=0.06,
            step_fraction=0.2,
        ))

    noise = NoiseTrader(
        trader_id="noise",
        mu_shock_scale=market_def.initial_sigma * 0.4,
        sigma_shock_scale=market_def.initial_sigma * 0.05,
    ) if add_noise else None

    if add_lp:
        market.add_liquidity("lp_main", amount=market_def.b * 0.5)

    history: list[dict] = []
    total_trades = 0
    total_volume = 0.0
    total_funding_paid = 0.0

    print(f"\n{'═' * 64}")
    print(f"  {market_def.title}")
    print(f"  {market_def.description.splitlines()[0]}")
    print(f"  Funding: {market_def.funding_rate_bps} bps × KL per {market_def.funding_interval} slots")
    print(f"{'═' * 64}")

    for slot in range(1, n_slots + 1):
        oracle_obs = market_def.oracle.observe(slot)

        # Update trader posteriors to track oracle
        for t in traders:
            t.posterior = GaussianMixtureParams.single(
                mu=oracle_obs.mu, sigma=oracle_obs.sigma
            )

        # Execute trades
        slot_volume = 0.0
        for t in traders:
            q = t.step(market)
            if q:
                slot_volume += q["collateral"]
                total_trades += 1
        if noise:
            q = noise.step(market)
            if q:
                slot_volume += q["collateral"]
                total_trades += 1

        total_volume += slot_volume
        market.tick()

        kl = market.current_kl_from_anchor()
        funding_rate = market.spot_funding_rate()
        lp_nav = market.lp_nav("lp_main") if add_lp else 0.0

        # Funding paid this slot (from history if just settled)
        funding_this_slot = 0.0
        if market.funding_history:
            last = market.funding_history[-1]
            if last["slot"] == market.slot:
                funding_this_slot = sum(
                    v for v in last["payments"].values() if v > 0
                )
                total_funding_paid += funding_this_slot

        snap = {
            "slot": slot,
            "mu": market.mark_price(),
            "sigma": market.amm.current_sigma,
            "oracle_mu": oracle_obs.mu,
            "kl": kl,
            "funding_rate": funding_rate,
            "lp_nav": lp_nav,
            "cash": market.amm.cash,
            "volume": slot_volume,
        }
        history.append(snap)

        if animate and slot % print_interval == 0:
            _print_perp_frame(snap, market_def.unit)

    _print_perp_frame(history[-1], market_def.unit, final=True)

    final = history[-1]
    print(f"\n{'─' * 64}")
    print(f"  Final mark price:    {final['mu']:.4f} {market_def.unit}")
    print(f"  Final oracle:        {final['oracle_mu']:.4f} {market_def.unit}")
    print(f"  Final KL divergence: {final['kl']:.4f} nats")
    print(f"  Funding rate (spot): {final['funding_rate']:.6f}/slot")
    print(f"  Total trades:        {total_trades}")
    print(f"  Total volume:        {total_volume:.2f}")
    print(f"  Total funding paid:  {total_funding_paid:.4f}")
    if add_lp:
        print(f"  LP NAV:              {final['lp_nav']:.4f}")
    print(f"{'─' * 64}\n")

    return {"history": history, "market": market}


def _print_perp_frame(snap: dict, unit: str, final: bool = False) -> None:
    label = "FINAL" if final else f"slot {snap['slot']:>4}"
    lines = _ascii_distribution(
        snap["mu"], snap["sigma"], oracle_mu=snap["oracle_mu"], width=56, height=5
    )
    print(
        f"\n  [{label}]  μ={snap['mu']:.3f} σ={snap['sigma']:.3f}  "
        f"KL={snap['kl']:.3f}  fund={snap['funding_rate']:.5f} {unit}"
    )
    for line in lines:
        print("  " + line)


# ---------------------------------------------------------------------------
# CSV export
# ---------------------------------------------------------------------------

def export_csv(history: list[dict], path: str) -> None:
    import csv
    keys = list(history[0].keys())
    with open(path, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(history)
    print(f"  Exported {len(history)} rows → {path}")


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main(argv: Optional[list[str]] = None) -> None:
    parser = argparse.ArgumentParser(
        description="Run a distribution market simulation.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("market_id", nargs="?", help="Market ID to run")
    parser.add_argument("--list", action="store_true", help="List available markets")
    parser.add_argument("--slots", type=int, default=None, help="Number of slots to simulate")
    parser.add_argument("--traders", type=int, default=2, help="Number of informed traders")
    parser.add_argument("--lp", action="store_true", default=True, help="Add an LP provider")
    parser.add_argument("--no-noise", action="store_true", help="Disable noise traders")
    parser.add_argument("--no-animate", action="store_true", help="Suppress per-slot output")
    parser.add_argument("--csv", type=str, default=None, help="Export history to CSV path")
    args = parser.parse_args(argv)

    if args.list or args.market_id is None:
        list_markets()
        return

    add_noise = not args.no_noise
    animate = not args.no_animate

    if args.market_id in RESOLVING_MARKETS:
        mdef = RESOLVING_MARKETS[args.market_id]
        n_slots = args.slots or 120
        result = run_resolving(
            mdef,
            n_slots=n_slots,
            n_informed=args.traders,
            add_noise=add_noise,
            animate=animate,
        )
        if args.csv:
            export_csv(result["history"], args.csv)

    elif args.market_id in PERP_MARKETS:
        mdef = PERP_MARKETS[args.market_id]
        n_slots = args.slots or 200
        result = run_perp(
            mdef,
            n_slots=n_slots,
            n_informed=args.traders,
            add_noise=add_noise,
            add_lp=args.lp,
            animate=animate,
        )
        if args.csv:
            export_csv(result["history"], args.csv)

    else:
        print(f"Unknown market '{args.market_id}'. Use --list to see options.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
