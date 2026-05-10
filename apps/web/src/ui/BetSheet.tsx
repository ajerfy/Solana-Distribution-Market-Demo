import { useState } from "react";
import type { MarketListing } from "../domain/types";
import { buildContinuousQuotePreview, estimateCollateralOffline } from "../domain/quoteMath";
import { compactDecimal } from "../domain/format";
import { BetPrefill } from "../domain/betPrefill";
import { useParabolaStore } from "../state/parabolaStore";
import { DistributionChart } from "./DistributionChart";
import {
  CompactDivider, DistBar, PrimaryButton, SectionLabel,
  StatRow, StatusBlockSimple, TagPill,
} from "./shared";

const STAKE_OPTS = [10, 25, 50, 100, 250, 500];

function estimateCollateral(market: MarketListing, mu: number, sigma: number, quoteCollateral: number | null) {
  return quoteCollateral ?? estimateCollateralOffline(market.crowdMu, market.crowdSigma, mu, sigma);
}

/* ── Shared sheet chrome ─────────────────────────────────── */
function SheetChrome({ accentColor, onDismiss, children }: {
  accentColor: string; onDismiss: () => void; children: React.ReactNode;
}) {
  return (
    <div className="pb-sheet-overlay" onClick={onDismiss} role="presentation">
      <div className="pb-sheet" onClick={(e) => e.stopPropagation()} style={{ position: "relative", overflow: "hidden" }}>
        {/* Accent bar */}
        <div style={{
          position: "absolute", top: 0, left: 0, right: 0, height: 3,
          background: accentColor, borderRadius: "24px 24px 0 0",
        }} />
        {/* Handle */}
        <div style={{
          width: 36, height: 4, borderRadius: 99,
          background: "var(--pb-border-strong)", margin: "16px auto 18px",
        }} />
        {children}
      </div>
    </div>
  );
}

/* ── Stake selector ──────────────────────────────────────── */
function StakeSelector({ stake, onChange }: { stake: number; onChange: (v: number) => void }) {
  return (
    <>
      <SectionLabel>Stake</SectionLabel>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
        {STAKE_OPTS.map((opt) => {
          const sel = stake === opt;
          return (
            <button
              key={opt}
              type="button"
              onClick={() => onChange(opt)}
              className="pb-mono"
              style={{
                flex: "1 1 28%", padding: "11px 4px", borderRadius: 12,
                fontWeight: 700, fontSize: 14, cursor: "pointer",
                border: sel ? "none" : "1px solid var(--pb-border-strong)",
                background: sel ? "var(--pb-you)" : "var(--pb-surface-el)",
                color: sel ? "var(--pb-on-accent)" : "var(--pb-text-sec)",
                boxShadow: sel ? "0 2px 10px var(--pb-you)40" : "none",
              }}
            >
              ${compactDecimal(opt, 0)}
            </button>
          );
        })}
      </div>
    </>
  );
}

/* ── Quote breakdown card ────────────────────────────────── */
function QuoteCard({ children }: { children: React.ReactNode }) {
  return (
    <div style={{
      marginTop: 14, padding: "14px 16px", borderRadius: 16,
      background: "var(--pb-surface-el)",
      border: "1px solid var(--pb-border-strong)",
      boxShadow: "var(--pb-card-shadow)",
    }}>
      {children}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   EstimationBetSheet
═══════════════════════════════════════════════════════════ */
export function EstimationBetSheet({ market, onDismiss }: { market: MarketListing; onDismiss: () => void }) {
  const payload       = useParabolaStore((s) => s.payload);
  const addBet        = useParabolaStore((s) => s.addBet);
  const setLastSubmit = useParabolaStore((s) => s.setLastSubmit);

  const [{ mu, sigma }, setCurve] = useState(() => {
    const [m, s] = BetPrefill.consume();
    return { mu: m ?? market.crowdMu, sigma: s ?? market.crowdSigma };
  });
  const [stake, setStake]           = useState(50);
  const [showAdv, setShowAdv]       = useState(false);
  const [status, setStatus]         = useState<{ message: string; variant: "error" | "working" | "ok" } | null>(null);

  let quote: ReturnType<typeof buildContinuousQuotePreview> | null = null;
  try { if (market.isOnChain && payload) quote = buildContinuousQuotePreview(payload.market, mu, sigma); }
  catch { quote = null; }

  const collateral = estimateCollateral(market, mu, sigma, quote?.collateralRequired ?? null);
  const fee        = quote?.feePaid ?? Math.max(stake * 0.003, 0.05);
  const maxWin     = stake * 1.85;

  const { sigmaMin, sigmaMax } = market;
  const span       = Math.max(sigmaMax - sigmaMin, 1e-4);
  const confidence = Math.min(1, Math.max(0, 1 - (sigma - sigmaMin) / span));
  const crowdConf  = (1 - (market.crowdSigma - sigmaMin) / span) * 100;

  const placeLocalBet = () => {
    addBet({
      id: `bet-${Date.now()}`, marketId: market.id, marketTitle: market.title,
      mu, sigma, stake, collateral, fee,
      placedAtMillis: Date.now(), resolved: false,
      realizedOutcome: null, realizedPnl: null, txSignatureHex: null, isOnChain: false,
    });
    setLastSubmit({ message: "Bet placed locally · resolve from Portfolio.", isError: false });
    onDismiss();
  };

  const onPrimary = () => {
    if (market.isOnChain) {
      setStatus({ message: "On-chain signing uses Solana Mobile Wallet Adapter on Android. Place a local demo bet from a non-chain market, or use the Android app.", variant: "error" });
      return;
    }
    placeLocalBet();
  };

  return (
    <SheetChrome accentColor="linear-gradient(90deg, var(--pb-you), var(--pb-crowd))" onDismiss={onDismiss}>
      {/* Header */}
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6 }}>
        {market.isOnChain && <TagPill colorVar="var(--pb-chain)" filled>ON-CHAIN</TagPill>}
        <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11, letterSpacing: "0.06em" }}>ESTIMATE</span>
      </div>
      <h2 style={{ margin: "0 0 16px", fontSize: 19, fontWeight: 700, letterSpacing: "-0.01em", lineHeight: 1.3 }}>
        {market.title}
      </h2>

      {/* Distribution chart */}
      <DistributionChart
        crowdMu={market.crowdMu} crowdSigma={market.crowdSigma}
        yourMu={mu} yourSigma={sigma}
        height={180}
      />

      {/* μ hero + bar */}
      <div style={{ display: "flex", gap: 16, margin: "14px 0 4px" }}>
        <div>
          <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em", marginBottom: 2 }}>YOUR MEAN μ</div>
          <div className="pb-mono" style={{ fontSize: 20, fontWeight: 700, color: "var(--pb-you)" }}>
            {compactDecimal(mu, 3)} <span style={{ fontSize: 12, color: "var(--pb-text-sec)" }}>{market.unit}</span>
          </div>
        </div>
        <div>
          <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em", marginBottom: 2 }}>CROWD μ</div>
          <div className="pb-mono" style={{ fontSize: 20, fontWeight: 700, color: "var(--pb-crowd)" }}>
            {compactDecimal(market.crowdMu, 2)} <span style={{ fontSize: 12, color: "var(--pb-text-sec)" }}>{market.unit}</span>
          </div>
        </div>
      </div>

      {/* μ slider */}
      <label style={{ display: "block", marginTop: 12, fontSize: 13 }}>
        <input
          type="range" min={market.muMin} max={market.muMax} step={0.001} value={mu}
          onChange={(e) => { setCurve({ mu: Number(e.target.value), sigma }); setStatus(null); }}
          style={{ width: "100%", accentColor: "var(--pb-you)" }}
        />
      </label>

      {/* Confidence */}
      <div style={{ display: "flex", justifyContent: "space-between", margin: "12px 0 4px" }}>
        <div>
          <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em", marginBottom: 2 }}>YOUR CONFIDENCE</div>
          <div className="pb-mono" style={{ fontSize: 20, fontWeight: 700, color: "var(--pb-crowd)" }}>
            {(confidence * 100).toFixed(0)}%
          </div>
        </div>
        <div style={{ textAlign: "right" }}>
          <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em", marginBottom: 2 }}>CROWD</div>
          <div className="pb-mono" style={{ fontSize: 20, fontWeight: 500, color: "var(--pb-text-sec)" }}>
            {crowdConf.toFixed(0)}%
          </div>
        </div>
      </div>
      <label style={{ display: "block", fontSize: 13 }}>
        <input
          type="range" min={0} max={1} step={0.001} value={confidence}
          onChange={(e) => { const c = Number(e.target.value); setCurve({ mu, sigma: sigmaMin + (1 - c) * span }); setStatus(null); }}
          style={{ width: "100%", accentColor: "var(--pb-crowd)" }}
        />
      </label>

      {/* DistBar preview */}
      <div style={{ margin: "10px 0 4px" }}>
        <DistBar mu={mu} sigma={sigma} muMin={market.muMin} muMax={market.muMax} color="var(--pb-you)" height={5} />
      </div>

      {/* Advanced toggle */}
      <div style={{ textAlign: "right", marginBottom: 16 }}>
        <button
          type="button"
          style={{ border: "none", background: "none", color: showAdv ? "var(--pb-long)" : "var(--pb-crowd)", fontWeight: 700, fontSize: 11, cursor: "pointer" }}
          onClick={() => setShowAdv(!showAdv)}
        >
          {showAdv ? "hide σ" : "advanced · show σ"}
        </button>
      </div>

      {showAdv && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
            <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em" }}>STD DEVIATION σ</div>
            <span className="pb-mono" style={{ color: "var(--pb-long)", fontWeight: 700, fontSize: 14 }}>{compactDecimal(sigma, 3)}</span>
          </div>
          <input
            type="range" min={sigmaMin} max={sigmaMax} step={0.001} value={sigma}
            onChange={(e) => { setCurve({ mu, sigma: Number(e.target.value) }); setStatus(null); }}
            style={{ width: "100%", accentColor: "var(--pb-long)" }}
          />
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: 4 }}>
            <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>crowd σ {compactDecimal(market.crowdSigma, 3)}</span>
            <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-long)" }}>your σ {compactDecimal(sigma, 3)}</span>
          </div>
        </div>
      )}

      <StakeSelector stake={stake} onChange={setStake} />

      <QuoteCard>
        <StatRow label="Collateral locked" value={compactDecimal(collateral, 4)} />
        <StatRow label="Fee"               value={compactDecimal(fee, 4)} />
        <CompactDivider />
        <StatRow label="Max win"  value={`+$${compactDecimal(maxWin, 2)}`}  accent="var(--pb-long)"  strong />
        <StatRow label="Max loss" value={`−$${compactDecimal(stake, 2)}`}   accent="var(--pb-short)" />
      </QuoteCard>

      {status && <div style={{ marginTop: 12 }}><StatusBlockSimple message={status.message} variant={status.variant} /></div>}

      <div style={{ marginTop: 14 }}>
        <PrimaryButton
          label={market.isOnChain ? "Sign & place bet · devnet" : `Place bet · $${compactDecimal(stake, 0)}`}
          accent={market.isOnChain
            ? "var(--pb-you)"
            : "linear-gradient(135deg, var(--pb-you) 0%, #80e830 100%)"}
          onClick={onPrimary}
        />
      </div>

      <p style={{ color: "var(--pb-text-dim)", fontSize: 12, marginTop: 10, lineHeight: 1.5 }}>
        {market.isOnChain
          ? "Sign on devnet to lock collateral. Quote envelope is the same one the program expects."
          : "Demo market. Bet records locally — use Resolve in Portfolio to draw a synthetic outcome."}
      </p>
    </SheetChrome>
  );
}
