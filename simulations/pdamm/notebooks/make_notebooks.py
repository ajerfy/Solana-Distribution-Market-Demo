"""
Generates the three spec notebooks as .ipynb files.
Run:  python3 simulations/pdamm/notebooks/make_notebooks.py
from the repo root.
"""

import json
import os

NB_DIR = os.path.dirname(os.path.abspath(__file__))


def nb(cells):
    return {
        "nbformat": 4,
        "nbformat_minor": 5,
        "metadata": {
            "kernelspec": {"display_name": "Python 3", "language": "python", "name": "python3"},
            "language_info": {"name": "python", "version": "3.9.0"},
        },
        "cells": cells,
    }


def code(src, idx=0):
    return {
        "cell_type": "code",
        "execution_count": None,
        "id": f"cell_{idx}",
        "metadata": {},
        "outputs": [],
        "source": src if isinstance(src, str) else "\n".join(src),
    }


def md(src):
    return {
        "cell_type": "markdown",
        "id": "md_cell",
        "metadata": {},
        "source": src if isinstance(src, str) else "\n".join(src),
    }


# ---------------------------------------------------------------------------
# Notebook 1: AMM Convergence Demo
# ---------------------------------------------------------------------------

NB1_CELLS = [
    md("# PD-AMM Convergence Demo\n\nShows how `InformedTrader` drives a K=1 Normal AMM toward a target distribution."),
    code("""\
import sys, os
sys.path.insert(0, os.path.abspath("../../.."))

import math
import numpy as np
import matplotlib.pyplot as plt

from pdamm.amm.gaussian_mixture import GaussianMixtureAMM, GaussianMixtureParams
from pdamm.agents.informed_trader import InformedTrader

K_RUST = 5.0 * math.sqrt(10.0 * math.sqrt(math.pi))

def make_amm(mu=95.0, sigma=10.0, b=50.0):
    return GaussianMixtureAMM(
        b=b, k=K_RUST,
        params=GaussianMixtureParams.single(mu=mu, sigma=sigma),
        max_collateral_per_trade=50.0,
    )
"""),
    md("## Single InformedTrader convergence"),
    code("""\
amm = make_amm(mu=95.0)
posterior = GaussianMixtureParams.single(mu=110.0, sigma=8.0)
trader = InformedTrader("i0", posterior=posterior, step_fraction=0.25, max_fraction_of_b=0.15)

history = []
for step in range(100):
    trader.step(amm)
    history.append(amm.current_mu)

fig, ax = plt.subplots(figsize=(10, 4))
ax.plot(history, label="AMM mu")
ax.axhline(110.0, color="red", linestyle="--", label="True mu (posterior)")
ax.axhline(95.0, color="gray", linestyle=":", label="Initial mu")
ax.set_xlabel("Step")
ax.set_ylabel("Distribution mean (mu)")
ax.set_title("InformedTrader drives AMM toward posterior")
ax.legend()
plt.tight_layout()
plt.savefig(os.path.join(os.path.dirname(os.path.abspath("__file__")),
    "../notebooks/convergence_demo.png"), dpi=120)
plt.show()
print(f"Final AMM mu: {amm.current_mu:.2f}  (target: 110.0)")
print(f"Trades executed: {trader.trades_executed}")
"""),
    md("## Collateral paid vs. distribution shift"),
    code("""\
amm2 = make_amm(mu=95.0)
mus = np.linspace(95, 125, 40)
collaterals = []
from pdamm.amm.gaussian_mixture import compute_collateral
for target_mu in mus:
    c = compute_collateral(
        amm2.params,
        GaussianMixtureParams.single(mu=target_mu, sigma=10.0),
        amm2.k,
    )
    collaterals.append(c)

fig, ax = plt.subplots(figsize=(10, 4))
ax.plot(mus, collaterals)
ax.set_xlabel("Target mu")
ax.set_ylabel("Collateral required")
ax.set_title("Collateral vs. distribution shift size (sigma fixed at 10)")
plt.tight_layout()
plt.show()
"""),
    md("## Opposing traders: bull vs. bear"),
    code("""\
amm3 = make_amm(mu=100.0, b=100.0)
bull = InformedTrader("bull", GaussianMixtureParams.single(mu=120.0, sigma=8.0), step_fraction=0.2)
bear = InformedTrader("bear", GaussianMixtureParams.single(mu=80.0, sigma=8.0), step_fraction=0.2)

hist_bull, hist_bear, hist_amm = [], [], []
for _ in range(80):
    bull.step(amm3)
    bear.step(amm3)
    hist_amm.append(amm3.current_mu)

fig, ax = plt.subplots(figsize=(10, 4))
ax.plot(hist_amm, label="AMM mu")
ax.axhline(120.0, color="green", linestyle="--", label="Bull target")
ax.axhline(80.0, color="red", linestyle="--", label="Bear target")
ax.axhline(100.0, color="gray", linestyle=":", label="Initial")
ax.set_title("Opposing traders — market stays near equilibrium")
ax.legend()
plt.tight_layout()
plt.show()
"""),
]

# ---------------------------------------------------------------------------
# Notebook 2: Funding Rate Mechanics
# ---------------------------------------------------------------------------

NB2_CELLS = [
    md("# Funding Rate Mechanics\n\nDemonstrates how KL divergence drives the funding rate and how it penalises positions that push the market away from the anchor."),
    code("""\
import sys, os
sys.path.insert(0, os.path.abspath("../../.."))

import math
import numpy as np
import matplotlib.pyplot as plt

from pdamm.amm.gaussian_mixture import GaussianMixtureAMM, GaussianMixtureParams
from pdamm.amm.anchor import CompositeAnchor, OracleAnchor, kl_divergence
from pdamm.amm.oracle import ConstantOracle
from pdamm.amm.funding import FundingConfig
from pdamm.amm.perp_market import PerpMarket
from pdamm.agents.lp_provider import LPProvider
from pdamm.agents.noise_trader import NoiseTrader
from pdamm.agents.informed_trader import InformedTrader

K_RUST = 5.0 * math.sqrt(10.0 * math.sqrt(math.pi))

def make_perp(amm_mu=95.0, anchor_mu=95.0, funding_rate_bps=200.0, funding_interval=10):
    amm = GaussianMixtureAMM(
        b=50.0, k=K_RUST,
        params=GaussianMixtureParams.single(mu=amm_mu, sigma=10.0),
        max_collateral_per_trade=50.0,
    )
    oracle = ConstantOracle(mu=anchor_mu, sigma=5.0)
    anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle)])
    config = FundingConfig(
        funding_rate_bps=funding_rate_bps,
        slots_per_period=funding_interval,
        kl_cap=10.0, min_kl_threshold=0.001,
    )
    return PerpMarket(amm=amm, anchor=anchor, funding_config=config,
                      funding_interval=funding_interval)
"""),
    md("## KL divergence vs. funding rate"),
    code("""\
anchor_mu = 100.0
amm_mus = np.linspace(80, 130, 60)
kl_vals, rate_vals = [], []

for amm_mu in amm_mus:
    market = make_perp(amm_mu=amm_mu, anchor_mu=anchor_mu)
    kl_vals.append(market.current_kl_from_anchor())
    rate_vals.append(market.spot_funding_rate())

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4))
ax1.plot(amm_mus, kl_vals)
ax1.set_xlabel("AMM mu")
ax1.set_ylabel("KL(AMM || anchor)")
ax1.set_title("KL divergence from anchor")
ax1.axvline(anchor_mu, color="red", linestyle="--", label="Anchor mu")
ax1.legend()

ax2.plot(amm_mus, rate_vals)
ax2.set_xlabel("AMM mu")
ax2.set_ylabel("Funding rate (per slot)")
ax2.set_title("Spot funding rate vs AMM mu")
ax2.axvline(anchor_mu, color="red", linestyle="--", label="Anchor mu")
ax2.legend()
plt.tight_layout()
plt.show()
"""),
    md("## Funding accumulation over time: noise vs. informed"),
    code("""\
market = make_perp(anchor_mu=100.0, funding_rate_bps=300.0, funding_interval=5)
lp = LPProvider("lp0", initial_deposit=200.0)
lp.enter(market)

noise = NoiseTrader("noise", mu_shock_scale=8.0, seed=3)
informed = InformedTrader(
    "informed", GaussianMixtureParams.single(mu=100.0, sigma=9.0),
    step_fraction=0.15, max_fraction_of_b=0.10,
)

slot_hist, kl_hist, rate_hist, cash_hist = [], [], [], []
for step in range(200):
    noise.step(market)
    informed.step(market)
    market.tick(1)
    slot_hist.append(market.slot)
    kl_hist.append(market.current_kl_from_anchor())
    rate_hist.append(market.spot_funding_rate())
    cash_hist.append(market.amm.cash)

fig, axes = plt.subplots(3, 1, figsize=(12, 9), sharex=True)
axes[0].plot(slot_hist, kl_hist, color="purple")
axes[0].set_ylabel("KL(AMM||anchor)")
axes[0].set_title("Funding dynamics: noise + informed traders")

axes[1].plot(slot_hist, rate_hist, color="orange")
axes[1].set_ylabel("Funding rate")

axes[2].plot(slot_hist, cash_hist, color="blue")
axes[2].set_ylabel("Vault cash")
axes[2].set_xlabel("Slot")
plt.tight_layout()
plt.show()

print(f"Final vault cash: {market.amm.cash:.3f}")
print(f"Total funding events: {len(market.funding_history)}")
"""),
]

# ---------------------------------------------------------------------------
# Notebook 3: Regime-Change Simulation
# ---------------------------------------------------------------------------

NB3_CELLS = [
    md("# Regime-Change Simulation\n\nShows how the PD-AMM tracks a StepOracle through a regime change, with informed traders and funding pressure realigning the market."),
    code("""\
import sys, os
sys.path.insert(0, os.path.abspath("../../.."))

import math
import numpy as np
import matplotlib.pyplot as plt

from pdamm.amm.gaussian_mixture import GaussianMixtureAMM, GaussianMixtureParams
from pdamm.amm.anchor import CompositeAnchor, OracleAnchor
from pdamm.amm.oracle import StepOracle, RandomWalkOracle
from pdamm.amm.funding import FundingConfig
from pdamm.amm.perp_market import PerpMarket
from pdamm.agents.lp_provider import LPProvider
from pdamm.agents.informed_trader import InformedTrader
from pdamm.agents.noise_trader import NoiseTrader

K_RUST = 5.0 * math.sqrt(10.0 * math.sqrt(math.pi))
"""),
    md("## Step-function regime change"),
    code("""\
oracle = StepOracle(initial_mu=95.0, steps=[(100, 120.0), (200, 95.0)])
anchor = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle)])
amm = GaussianMixtureAMM(
    b=50.0, k=K_RUST,
    params=GaussianMixtureParams.single(mu=95.0, sigma=10.0),
    max_collateral_per_trade=50.0,
)
config = FundingConfig(funding_rate_bps=200.0, slots_per_period=10, kl_cap=10.0, min_kl_threshold=0.001)
market = PerpMarket(amm=amm, anchor=anchor, funding_config=config, funding_interval=10)

lp = LPProvider("lp0", initial_deposit=150.0)
lp.enter(market)

noise = NoiseTrader("noise", mu_shock_scale=3.0, seed=7)

slot_hist, oracle_hist, amm_hist, kl_hist = [], [], [], []
for step in range(300):
    obs_mu = oracle.observe(market.slot).mu
    informed = InformedTrader(
        f"i_{step}",
        GaussianMixtureParams.single(mu=obs_mu, sigma=9.0),
        step_fraction=0.15, max_fraction_of_b=0.10,
    )
    informed.step(market)
    noise.step(market)
    market.tick(1)
    slot_hist.append(market.slot)
    oracle_hist.append(oracle.observe(market.slot).mu)
    amm_hist.append(market.amm.current_mu)
    kl_hist.append(market.current_kl_from_anchor())

fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 7), sharex=True)
ax1.plot(slot_hist, oracle_hist, "r--", label="Oracle mu", alpha=0.8)
ax1.plot(slot_hist, amm_hist, "b-", label="AMM mu")
ax1.axvline(100, color="gray", linestyle=":", alpha=0.7, label="Regime change")
ax1.axvline(200, color="gray", linestyle=":", alpha=0.7)
ax1.set_ylabel("Distribution mean")
ax1.set_title("AMM tracking a step-function oracle through regime changes")
ax1.legend()

ax2.plot(slot_hist, kl_hist, color="purple")
ax2.axvline(100, color="gray", linestyle=":", alpha=0.7)
ax2.axvline(200, color="gray", linestyle=":", alpha=0.7)
ax2.set_ylabel("KL(AMM || anchor)")
ax2.set_xlabel("Slot")
ax2.set_title("KL divergence (funding pressure)")
plt.tight_layout()
plt.show()

tracking_err = abs(amm_hist[-1] - oracle_hist[-1])
print(f"Final tracking error: {tracking_err:.2f}")
print(f"LP NAV: {market.lp_nav('lp0'):.3f}  (deposited: 150.0)")
"""),
    md("## Random-walk oracle: long-run tracking"),
    code("""\
oracle2 = RandomWalkOracle(
    initial_mu=100.0, vol=0.4,
    mean_reversion=0.05, long_run_mu=100.0, seed=42,
)
anchor2 = CompositeAnchor(sub_anchors=[OracleAnchor(oracle_feed=oracle2)])
amm2 = GaussianMixtureAMM(
    b=50.0, k=K_RUST,
    params=GaussianMixtureParams.single(mu=100.0, sigma=10.0),
    max_collateral_per_trade=50.0,
)
config2 = FundingConfig(funding_rate_bps=200.0, slots_per_period=10, kl_cap=10.0, min_kl_threshold=0.001)
market2 = PerpMarket(amm=amm2, anchor=anchor2, funding_config=config2, funding_interval=10)

lp2 = LPProvider("lp0", initial_deposit=150.0)
lp2.enter(market2)

slots2, oracle2_hist, amm2_hist, err_hist = [], [], [], []
for step in range(500):
    obs_mu = oracle2.observe(market2.slot).mu
    informed2 = InformedTrader(
        f"i_{step}",
        GaussianMixtureParams.single(mu=obs_mu, sigma=9.0),
        step_fraction=0.12, max_fraction_of_b=0.08,
    )
    informed2.step(market2)
    market2.tick(1)
    slots2.append(market2.slot)
    o_mu = oracle2.observe(market2.slot).mu
    oracle2_hist.append(o_mu)
    amm2_hist.append(market2.amm.current_mu)
    err_hist.append(abs(market2.amm.current_mu - o_mu))

fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 7), sharex=True)
ax1.plot(slots2, oracle2_hist, "r--", label="Oracle", alpha=0.8)
ax1.plot(slots2, amm2_hist, "b-", label="AMM mu", alpha=0.9)
ax1.set_ylabel("mu")
ax1.set_title("Random-walk oracle tracking (500 slots)")
ax1.legend()

ax2.plot(slots2, err_hist, color="orange")
ax2.set_ylabel("|AMM - oracle|")
ax2.set_xlabel("Slot")
ax2.set_title("Tracking error")
plt.tight_layout()
plt.show()

avg_err = sum(err_hist[-100:]) / 100
print(f"Average tracking error (last 100 slots): {avg_err:.3f}")
print(f"LP NAV: {market2.lp_nav('lp0'):.3f}")
"""),
]

# ---------------------------------------------------------------------------
# Write notebooks
# ---------------------------------------------------------------------------

notebooks = {
    "01_convergence_demo.ipynb": nb(NB1_CELLS),
    "02_funding_mechanics.ipynb": nb(NB2_CELLS),
    "03_regime_change.ipynb": nb(NB3_CELLS),
}

for filename, notebook in notebooks.items():
    path = os.path.join(NB_DIR, filename)
    with open(path, "w") as f:
        json.dump(notebook, f, indent=1)
    print(f"Written: {path}")
