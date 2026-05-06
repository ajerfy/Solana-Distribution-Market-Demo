"""
Golden-section minimizer — Python port of src/numerical.rs.

The single public entry point is find_global_minimum(f, lower, upper).
"""

from __future__ import annotations

import numpy as np
from dataclasses import dataclass
from typing import Callable

_PHI = (1.0 + np.sqrt(5.0)) / 2.0
_INV_PHI = 1.0 / _PHI


@dataclass
class MinimumResult:
    x_min: float
    value: float


def find_global_minimum(
    f: Callable[[float], float],
    lower: float,
    upper: float,
    coarse_samples: int = 512,
    tolerance: float = 1e-10,
    max_iterations: int = 256,
) -> MinimumResult:
    """
    Coarse grid scan → bracket → golden-section refinement, mirroring the
    Rust find_global_minimum pipeline.  Returns the x that minimises f.
    """
    bracket = _bracket_minimum(f, lower, upper, coarse_samples)
    refined = _golden_section_minimum(f, bracket[0], bracket[1], tolerance, max_iterations)

    # also check boundary candidates
    candidates = [lower, upper, bracket[0], bracket[1]]
    best = refined
    for x in candidates:
        v = f(x)
        if v < best.value:
            best = MinimumResult(x_min=x, value=v)

    return best


def _bracket_minimum(
    f: Callable[[float], float],
    lower: float,
    upper: float,
    samples: int,
) -> tuple[float, float]:
    xs = np.linspace(lower, upper, samples + 1)
    values = np.array([f(x) for x in xs])
    best_idx = int(np.argmin(values))
    left_idx = max(best_idx - 1, 0)
    right_idx = min(best_idx + 1, samples)
    return (xs[left_idx], xs[right_idx])


def _golden_section_minimum(
    f: Callable[[float], float],
    lower: float,
    upper: float,
    tolerance: float,
    max_iterations: int,
) -> MinimumResult:
    a, b = lower, upper
    x1 = b - (b - a) * _INV_PHI
    x2 = a + (b - a) * _INV_PHI
    f1, f2 = f(x1), f(x2)

    for _ in range(max_iterations):
        if abs(b - a) <= tolerance:
            break
        if f1 < f2:
            b, x2, f2 = x2, x1, f1
            x1 = b - (b - a) * _INV_PHI
            f1 = f(x1)
        else:
            a, x1, f1 = x1, x2, f2
            x2 = a + (b - a) * _INV_PHI
            f2 = f(x2)

    mid = (a + b) / 2.0
    candidates = [(a, f(a)), (mid, f(mid)), (b, f(b))]
    best_x, best_v = min(candidates, key=lambda t: t[1])
    return MinimumResult(x_min=best_x, value=best_v)
