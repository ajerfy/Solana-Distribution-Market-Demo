"""
Oracle feeds for the perpetual AMM.

An oracle gives the AMM an external reference point — the "true" distribution
of the underlying.  The anchor layer (Layer 2) wraps oracles as sub-anchors.
The funding rate layer (Layer 3) uses the anchor distribution to charge
traders who push the market away from the oracle-informed belief.

Oracle types:
  SyntheticOracle   — a deterministic oracle for testing; follows a scripted
                      path (e.g. random walk, step function, mean-reversion)
  NoisyOracle       — wraps a SyntheticOracle and adds Gaussian observation noise
  HistoricalOracle  — replays a prerecorded price series
"""

from __future__ import annotations

import math
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Callable

import numpy as np


# ---------------------------------------------------------------------------
# Oracle observation
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class OracleObservation:
    slot: int
    mu: float       # point estimate of the underlying value
    sigma: float    # uncertainty around that estimate (not the market sigma)


# ---------------------------------------------------------------------------
# Base interface
# ---------------------------------------------------------------------------

class OracleFeed(ABC):
    @abstractmethod
    def observe(self, slot: int) -> OracleObservation:
        """Return the oracle's belief at the given slot."""


# ---------------------------------------------------------------------------
# Synthetic oracles
# ---------------------------------------------------------------------------

@dataclass
class ConstantOracle(OracleFeed):
    """Always returns the same value — useful for equilibrium tests."""
    mu: float
    sigma: float = 2.0

    def observe(self, slot: int) -> OracleObservation:
        return OracleObservation(slot=slot, mu=self.mu, sigma=self.sigma)


@dataclass
class StepOracle(OracleFeed):
    """
    Follows a step schedule: a list of (slot_threshold, mu) pairs.
    Returns the mu corresponding to the latest threshold not yet exceeded.
    """
    initial_mu: float
    steps: list[tuple[int, float]]   # [(slot, new_mu), ...]
    sigma: float = 2.0

    def observe(self, slot: int) -> OracleObservation:
        mu = self.initial_mu
        for threshold, new_mu in self.steps:
            if slot >= threshold:
                mu = new_mu
        return OracleObservation(slot=slot, mu=mu, sigma=self.sigma)


@dataclass
class RandomWalkOracle(OracleFeed):
    """
    Gaussian random walk with optional mean-reversion.
    seed: reproducible for tests.
    drift: per-slot drift (default 0)
    vol: per-slot volatility
    mean_reversion: pull-back strength toward long_run_mu (0 = pure RW)
    long_run_mu: target for mean reversion
    """
    initial_mu: float
    vol: float = 0.5
    drift: float = 0.0
    mean_reversion: float = 0.0
    long_run_mu: float = 0.0
    sigma: float = 2.0
    seed: int = 42

    _path: dict[int, float] = field(default_factory=dict, init=False)
    _rng: np.random.Generator = field(init=False)

    def __post_init__(self) -> None:
        self._rng = np.random.default_rng(self.seed)
        self._path[0] = self.initial_mu

    def observe(self, slot: int) -> OracleObservation:
        if slot in self._path:
            return OracleObservation(slot=slot, mu=self._path[slot], sigma=self.sigma)

        # Build path sequentially up to slot
        last_slot = max(self._path.keys())
        mu = self._path[last_slot]
        for s in range(last_slot + 1, slot + 1):
            shock = self._rng.normal(0.0, self.vol)
            reversion = self.mean_reversion * (self.long_run_mu - mu)
            mu = mu + self.drift + reversion + shock
            self._path[s] = mu

        return OracleObservation(slot=slot, mu=self._path[slot], sigma=self.sigma)


@dataclass
class NoisyOracle(OracleFeed):
    """
    Wraps any OracleFeed and adds independent Gaussian observation noise.
    This models a real oracle that doesn't see the true value exactly.
    """
    underlying: OracleFeed
    noise_sigma: float = 1.0
    seed: int = 99

    _rng: np.random.Generator = field(init=False)

    def __post_init__(self) -> None:
        self._rng = np.random.default_rng(self.seed)

    def observe(self, slot: int) -> OracleObservation:
        obs = self.underlying.observe(slot)
        noise = self._rng.normal(0.0, self.noise_sigma)
        reported_sigma = math.sqrt(obs.sigma ** 2 + self.noise_sigma ** 2)
        return OracleObservation(slot=slot, mu=obs.mu + noise, sigma=reported_sigma)


@dataclass
class HistoricalOracle(OracleFeed):
    """
    Replays a list of (slot, mu, sigma) observations.
    Between recorded slots, linearly interpolates mu; sigma is taken from
    the nearest recorded observation.
    """
    observations: list[tuple[int, float, float]]   # [(slot, mu, sigma), ...]

    def __post_init__(self) -> None:
        self.observations = sorted(self.observations, key=lambda t: t[0])

    def observe(self, slot: int) -> OracleObservation:
        if not self.observations:
            raise ValueError("no observations recorded")

        slots = [o[0] for o in self.observations]
        if slot <= slots[0]:
            s, mu, sigma = self.observations[0]
            return OracleObservation(slot=slot, mu=mu, sigma=sigma)
        if slot >= slots[-1]:
            s, mu, sigma = self.observations[-1]
            return OracleObservation(slot=slot, mu=mu, sigma=sigma)

        # Linear interpolation
        for i in range(len(slots) - 1):
            if slots[i] <= slot <= slots[i + 1]:
                s0, mu0, sig0 = self.observations[i]
                s1, mu1, sig1 = self.observations[i + 1]
                t = (slot - s0) / (s1 - s0)
                mu = mu0 + t * (mu1 - mu0)
                sigma = sig0 + t * (sig1 - sig0)
                return OracleObservation(slot=slot, mu=mu, sigma=sigma)

        raise RuntimeError(f"interpolation failed for slot {slot}")
