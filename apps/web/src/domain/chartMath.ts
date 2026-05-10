/** Gaussian PDF + skewed visual PDF from `Chart.kt`. */

import { PI, abs, exp, max, min, sqrt } from "./math";

export function normalPdf(x: number, mu: number, sigma: number): number {
  const safeSigma = max(sigma, 0.0001);
  const coef = 1.0 / (safeSigma * sqrt(2.0 * PI));
  const ex = -((x - mu) * (x - mu)) / (2.0 * safeSigma * safeSigma);
  return coef * exp(ex);
}

export function visualDistributionPdf(
  x: number,
  mu: number,
  sigma: number,
  skew: number,
): number {
  if (abs(skew) < 0.01) return normalPdf(x, mu, sigma);
  const safeSigma = max(sigma, 0.000001);
  const skewAmount = min(max(abs(skew) / 7.0, 0), 1);
  const tailSigma = safeSigma * (1.0 + 1.35 * skewAmount);
  const tightSigma = safeSigma * (1.0 - 0.35 * skewAmount);
  const leftSigma = skew < 0 ? tailSigma : tightSigma;
  const rightSigma = skew < 0 ? tightSigma : tailSigma;
  const sideSigma = x < mu ? leftSigma : rightSigma;
  const z = (x - mu) / sideSigma;
  const normalizer = sqrt(2.0 / PI) / (leftSigma + rightSigma);
  return normalizer * exp(-0.5 * z * z);
}
