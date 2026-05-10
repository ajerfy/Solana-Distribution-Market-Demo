/** Mirrors Kotlin `QuoteMath.kt` core collateral search. */

import type { ContinuousQuotePreview, DemoMarket } from "./types";
import { FIXED_EPSILON, buildTradeInstructionHex } from "./encoding";

function normalPdf(x: number, mu: number, sigma: number): number {
  const safeSigma = Math.max(sigma, 0.0001);
  const coef = 1.0 / (safeSigma * Math.sqrt(2.0 * Math.PI));
  const exp =
    -((x - mu) * (x - mu)) / (2.0 * safeSigma * safeSigma);
  return coef * Math.exp(exp);
}

function scaledNormalValue(x: number, mu: number, sigma: number, k: number): number {
  const safeSigma = Math.max(sigma, 0.0001);
  const lambda = k * Math.sqrt(2.0 * safeSigma * Math.sqrt(Math.PI));
  return lambda * normalPdf(x, mu, safeSigma);
}

function directionalLossAt(
  x: number,
  currentMu: number,
  currentSigma: number,
  proposedMu: number,
  proposedSigma: number,
  k: number,
): number {
  const cv = scaledNormalValue(x, currentMu, currentSigma, k);
  const pv = scaledNormalValue(x, proposedMu, proposedSigma, k);
  return Math.max(cv - pv, 0.0);
}

function computeSearchBounds(
  currentMu: number,
  currentSigma: number,
  proposedMu: number,
  proposedSigma: number,
): [number, number] {
  const span = Math.abs(currentMu - proposedMu);
  const sigma = Math.max(currentSigma, proposedSigma);
  const tail = span + sigma * 8.0;
  return [
    Math.min(currentMu, proposedMu) - tail,
    Math.max(currentMu, proposedMu) + tail,
  ];
}

function maximumDirectionalLossWithArgmax(
  currentMu: number,
  currentSigma: number,
  proposedMu: number,
  proposedSigma: number,
  k: number,
  lowerBound: number,
  upperBound: number,
  samples: number,
): [number, number] {
  let bestX = lowerBound;
  let bestLoss = 0.0;
  const safeSamples = Math.max(samples, 1);
  for (let step = 0; step <= safeSamples; step++) {
    const x =
      lowerBound +
      ((upperBound - lowerBound) * step) / safeSamples;
    const loss = directionalLossAt(
      x,
      currentMu,
      currentSigma,
      proposedMu,
      proposedSigma,
      k,
    );
    if (loss > bestLoss) {
      bestLoss = loss;
      bestX = x;
    }
  }
  return [bestX, bestLoss];
}

export function computeCollateralRequired(
  currentMu: number,
  currentSigma: number,
  proposedMu: number,
  proposedSigma: number,
  k: number,
  lowerBound: number,
  upperBound: number,
  coarseSamples: number,
  refineSamples: number,
): number {
  const coarse = maximumDirectionalLossWithArgmax(
    currentMu,
    currentSigma,
    proposedMu,
    proposedSigma,
    k,
    lowerBound,
    upperBound,
    coarseSamples,
  );
  const coarseStep = (upperBound - lowerBound) / Math.max(coarseSamples, 1);
  const refineLower = Math.max(lowerBound, coarse[0] - coarseStep);
  const refineUpper = Math.min(upperBound, coarse[0] + coarseStep);
  const refine = maximumDirectionalLossWithArgmax(
    currentMu,
    currentSigma,
    proposedMu,
    proposedSigma,
    k,
    refineLower,
    refineUpper,
    refineSamples,
  );
  const endpointLoss = Math.max(
    directionalLossAt(
      lowerBound,
      currentMu,
      currentSigma,
      proposedMu,
      proposedSigma,
      k,
    ),
    directionalLossAt(
      upperBound,
      currentMu,
      currentSigma,
      proposedMu,
      proposedSigma,
      k,
    ),
  );
  return (
    Math.max(Math.max(coarse[1], refine[1]), endpointLoss) + FIXED_EPSILON
  );
}

export function buildContinuousQuotePreview(
  market: DemoMarket,
  targetMu: number,
  targetSigma: number,
): ContinuousQuotePreview {
  const currentMu = Number(market.current_mu_display);
  const currentSigma = Number(market.current_sigma_display);
  const k = Number(market.k_display);
  const [lowerBound, upperBound] = computeSearchBounds(
    currentMu,
    currentSigma,
    targetMu,
    targetSigma,
  );
  const collateralRequired = computeCollateralRequired(
    currentMu,
    currentSigma,
    targetMu,
    targetSigma,
    k,
    lowerBound,
    upperBound,
    market.coarse_samples,
    market.refine_samples,
  );
  const minTakerFee = Number(market.min_taker_fee_display);
  const feePaid = Math.max(
    (collateralRequired * market.taker_fee_bps) / 10_000.0,
    minTakerFee,
  );
  const totalDebit = collateralRequired + feePaid;
  const serializedInstructionHex = buildTradeInstructionHex(
    market,
    targetMu,
    targetSigma,
    collateralRequired,
    feePaid,
    totalDebit,
    totalDebit,
    lowerBound,
    upperBound,
  );
  return {
    targetMu,
    targetSigma,
    collateralRequired,
    feePaid,
    totalDebit,
    maxTotalDebit: totalDebit,
    quoteExpirySlot: market.demo_quote_expiry_slot,
    serializedInstructionHex,
  };
}

export function simulatedPayoff(
  stake: number,
  yourMu: number,
  yourSigma: number,
  realized: number,
): number {
  const safeSigma = Math.max(yourSigma, 0.0001);
  const z = (realized - yourMu) / safeSigma;
  const score = Math.exp(-0.5 * z * z);
  return stake * (score - 0.4) * 2.5;
}

export function estimateCollateralOffline(
  crowdMu: number,
  crowdSigma: number,
  mu: number,
  sigma: number,
): number {
  const muDelta = Math.abs(mu - crowdMu);
  const sigmaRatio = sigma / Math.max(crowdSigma, 0.001);
  const base = crowdSigma * 0.6;
  return base + muDelta * 0.4 + Math.max(0.0, 1.0 - sigmaRatio) * 1.5;
}
