"""
Layer 2 — Composite Anchor.

The anchor is a weighted mixture of sub-anchors.  Each sub-anchor produces a
probability distribution.  The composite anchor is the KL-projection of the
weighted mixture back onto the Gaussian family.

In a perpetual distribution market the anchor prevents the AMM distribution
from drifting arbitrarily far from some meaningful reference.  The funding
rate (Layer 3) uses the KL divergence between the current AMM distribution
and the anchor to charge traders whose positions push the market away.

Sub-anchor types implemented:
  - FixedAnchor:       constant distribution (e.g. long-run prior)
  - OracleAnchor:      tracks a live oracle (see oracle.py)
  - EMAnchor:          exponential moving average of recent AMM states
  - CompositeAnchor:   weighted mixture of the above
"""

from __future__ import annotations

import math
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Optional

from .gaussian_mixture import GaussianMixtureParams, GaussianComponent


# ---------------------------------------------------------------------------
# KL divergence  KL(p || q)  for two single-component Gaussians
# ---------------------------------------------------------------------------

def kl_divergence_normal(
    mu_p: float, sigma_p: float,
    mu_q: float, sigma_q: float,
) -> float:
    """
    KL(N(mu_p, sigma_p) || N(mu_q, sigma_q))
    = log(sigma_q/sigma_p) + (sigma_p^2 + (mu_p-mu_q)^2) / (2*sigma_q^2) - 0.5
    """
    return (
        math.log(sigma_q / sigma_p)
        + (sigma_p ** 2 + (mu_p - mu_q) ** 2) / (2.0 * sigma_q ** 2)
        - 0.5
    )


def kl_divergence(p: GaussianMixtureParams, q: GaussianMixtureParams, n_samples: int = 512) -> float:
    """
    Monte-Carlo estimate of KL(p || q) via importance sampling.
    For K=1 uses the closed form above.
    """
    p_comps = p.components
    q_comps = q.components

    if len(p_comps) == 1 and len(q_comps) == 1:
        return kl_divergence_normal(
            p_comps[0].mu, p_comps[0].sigma,
            q_comps[0].mu, q_comps[0].sigma,
        )

    # General case: Monte-Carlo
    import numpy as np
    rng = np.random.default_rng(0)
    p_weights = p.normalized_weights
    # Sample component indices
    comp_idx = rng.choice(len(p_comps), size=n_samples, p=p_weights)
    xs = np.array([
        rng.normal(p_comps[i].mu, p_comps[i].sigma) for i in comp_idx
    ])
    log_p = np.log(np.array([p.pdf(float(x)) for x in xs]) + 1e-300)
    log_q = np.log(np.array([q.pdf(float(x)) for x in xs]) + 1e-300)
    return float(np.mean(log_p - log_q))


# ---------------------------------------------------------------------------
# KL-projection: find the single-component Gaussian closest (in KL) to p
# ---------------------------------------------------------------------------

def kl_project_to_normal(p: GaussianMixtureParams) -> GaussianMixtureParams:
    """
    Moment-matched KL projection: the optimal single Gaussian matching p in KL
    is the one with the same mean and variance (moment matching).
    """
    weights = p.normalized_weights
    components = p.components

    # First moment
    mu = sum(w * c.mu for w, c in zip(weights, components))

    # Second central moment
    variance = sum(
        w * (c.sigma ** 2 + (c.mu - mu) ** 2)
        for w, c in zip(weights, components)
    )
    sigma = math.sqrt(max(variance, 1e-12))

    return GaussianMixtureParams.single(mu=mu, sigma=sigma)


# ---------------------------------------------------------------------------
# Sub-anchor interface
# ---------------------------------------------------------------------------

class SubAnchor(ABC):
    @abstractmethod
    def distribution(self, slot: int) -> GaussianMixtureParams:
        """Return this anchor's distribution at the given slot."""

    @property
    @abstractmethod
    def weight(self) -> float:
        """Relative weight in the composite."""


# ---------------------------------------------------------------------------
# Concrete sub-anchors
# ---------------------------------------------------------------------------

@dataclass
class FixedAnchor(SubAnchor):
    """A constant distribution — captures a long-run prior."""
    _distribution: GaussianMixtureParams
    _weight: float = 1.0

    def distribution(self, slot: int) -> GaussianMixtureParams:
        return self._distribution

    @property
    def weight(self) -> float:
        return self._weight


@dataclass
class OracleAnchor(SubAnchor):
    """
    Tracks a live oracle feed.  The oracle returns (mu, sigma) observations;
    this anchor wraps them as a GaussianMixtureParams.

    oracle_feed: callable (slot) -> (mu, sigma)
    sigma_floor: minimum sigma to keep market non-degenerate
    """
    oracle_feed: object  # OracleFeed from oracle.py — avoid circular import
    _weight: float = 1.0
    sigma_floor: float = 0.5

    def distribution(self, slot: int) -> GaussianMixtureParams:
        obs = self.oracle_feed.observe(slot)
        sigma = max(obs.sigma, self.sigma_floor)
        return GaussianMixtureParams.single(mu=obs.mu, sigma=sigma)

    @property
    def weight(self) -> float:
        return self._weight


@dataclass
class EMAnchor(SubAnchor):
    """
    Exponential moving average of AMM distribution states.
    Each call to update(params, slot) blends the new params into the EMA.
    half_life_slots: number of slots for the weight to halve.
    """
    half_life_slots: float
    _weight: float = 1.0
    _ema_mu: Optional[float] = field(default=None, init=False)
    _ema_sigma: Optional[float] = field(default=None, init=False)
    _last_slot: int = field(default=0, init=False)

    def update(self, params: GaussianMixtureParams, slot: int) -> None:
        projected = kl_project_to_normal(params)
        mu = projected.components[0].mu
        sigma = projected.components[0].sigma

        if self._ema_mu is None:
            self._ema_mu = mu
            self._ema_sigma = sigma
            self._last_slot = slot
            return

        elapsed = max(slot - self._last_slot, 0)
        alpha = 1.0 - math.exp(-elapsed * math.log(2.0) / self.half_life_slots)
        self._ema_mu = (1.0 - alpha) * self._ema_mu + alpha * mu
        self._ema_sigma = (1.0 - alpha) * self._ema_sigma + alpha * sigma
        self._last_slot = slot

    def distribution(self, slot: int) -> GaussianMixtureParams:
        if self._ema_mu is None:
            raise RuntimeError("EMAnchor has not been updated yet")
        return GaussianMixtureParams.single(mu=self._ema_mu, sigma=self._ema_sigma)

    @property
    def weight(self) -> float:
        return self._weight


# ---------------------------------------------------------------------------
# Composite anchor
# ---------------------------------------------------------------------------

@dataclass
class CompositeAnchor:
    """
    Weighted KL-projection over a list of sub-anchors.

    The composite distribution is the KL-projected (moment-matched) Normal
    of the weighted sum of sub-anchor distributions.

    This ensures the anchor always produces a single Gaussian that best
    represents the weighted belief across all sub-anchors.
    """
    sub_anchors: list[SubAnchor]

    def __post_init__(self) -> None:
        if not self.sub_anchors:
            raise ValueError("at least one sub-anchor required")

    def distribution(self, slot: int) -> GaussianMixtureParams:
        total_weight = sum(a.weight for a in self.sub_anchors)
        if total_weight <= 0:
            raise ValueError("sub-anchor weights must sum to positive value")

        # Weighted moment aggregation
        weighted_mu = 0.0
        weighted_second_moment = 0.0

        for anchor in self.sub_anchors:
            w = anchor.weight / total_weight
            d = anchor.distribution(slot)
            projected = kl_project_to_normal(d)
            c = projected.components[0]
            weighted_mu += w * c.mu
            weighted_second_moment += w * (c.sigma ** 2 + c.mu ** 2)

        mu = weighted_mu
        sigma = math.sqrt(max(weighted_second_moment - mu ** 2, 1e-12))
        return GaussianMixtureParams.single(mu=mu, sigma=sigma)
