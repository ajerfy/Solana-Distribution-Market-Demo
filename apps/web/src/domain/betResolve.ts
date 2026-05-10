import type { BetRecord, MarketListing } from "./types";
import { simulatedPayoff } from "./quoteMath";

function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a += 0x6d2b79f5;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function simulateRealizedAndPnl(
  market: MarketListing,
  bet: BetRecord,
): [number, number] {
  let h = 0;
  for (let i = 0; i < bet.id.length; i++) {
    h = (h * 31 + bet.id.charCodeAt(i)) >>> 0;
  }
  const rng = mulberry32(h ^ Math.floor(bet.placedAtMillis));
  const z = rng() * 2 - 1;
  const realized = market.crowdMu + z * market.crowdSigma * 1.2;
  const pnl = simulatedPayoff(bet.stake, bet.mu, bet.sigma, realized);
  return [realized, pnl];
}
