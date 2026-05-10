import { useState } from "react";
import type { DemoPerpMarket, DemoRegimeIndex, MarketListing } from "../domain/types";
import { compactDecimal } from "../domain/format";
import { BetPrefill } from "../domain/betPrefill";
import {
  CompactDivider, DistBar, MiniDistCurve, PrimaryButton,
  SectionLabel, StatRow, StatusBlockSimple, TagPill,
} from "./shared";

const STAKE_OPTS = [10, 25, 50, 100, 250, 500];

/* ── Shared sheet chrome ─────────────────────────────────── */
function SheetChrome({ accentColor, onDismiss, children }: {
  accentColor: string; onDismiss: () => void; children: React.ReactNode;
}) {
  return (
    <div className="pb-sheet-overlay" onClick={onDismiss} role="presentation">
      <div className="pb-sheet" onClick={(e) => e.stopPropagation()} style={{ position: "relative", overflow: "hidden" }}>
        <div style={{
          position: "absolute", top: 0, left: 0, right: 0, height: 3,
          background: accentColor, borderRadius: "24px 24px 0 0",
        }} />
        <div style={{
          width: 36, height: 4, borderRadius: 99,
          background: "var(--pb-border-strong)", margin: "16px auto 18px",
        }} />
        {children}
      </div>
    </div>
  );
}

/* ── Side toggle (Long / Short) ──────────────────────────── */
function SideToggle({ side, onChange }: { side: "Long" | "Short"; onChange: (s: "Long" | "Short") => void }) {
  return (
    <div style={{
      display: "flex", gap: 6, padding: 5, borderRadius: 16,
      background: "var(--pb-surface-el)", border: "1px solid var(--pb-border)",
      marginBottom: 18,
    }}>
      {(["Long", "Short"] as const).map((s) => {
        const sel   = side === s;
        const color = s === "Long" ? "var(--pb-long)" : "var(--pb-short)";
        return (
          <button
            key={s}
            type="button"
            onClick={() => onChange(s)}
            style={{
              flex: 1, padding: "11px 8px", borderRadius: 12,
              border: "none", cursor: "pointer", fontWeight: 800, fontSize: 15,
              background: sel ? color : "transparent",
              color: sel ? "#fff" : "var(--pb-text-sec)",
              boxShadow: sel ? `0 2px 12px ${color}50` : "none",
            }}
          >
            {s}
          </button>
        );
      })}
    </div>
  );
}

/* ── Stake selector ──────────────────────────────────────── */
function StakeSelector({ stake, onChange }: { stake: number; onChange: (v: number) => void }) {
  return (
    <>
      <SectionLabel>Stake</SectionLabel>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 8, marginBottom: 16 }}>
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

/* ── Quote breakdown ─────────────────────────────────────── */
function QuoteCard({ children }: { children: React.ReactNode }) {
  return (
    <div style={{
      padding: "14px 16px", borderRadius: 16,
      background: "var(--pb-surface-el)", border: "1px solid var(--pb-border-strong)",
      boxShadow: "var(--pb-card-shadow)", marginBottom: 14,
    }}>
      {children}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   RegimeBetSheet
═══════════════════════════════════════════════════════════ */
export function RegimeBetSheet({
  market, regime, onDismiss,
}: { market: MarketListing; regime: DemoRegimeIndex; onDismiss: () => void }) {
  const [side, setSide]   = useState<"Long" | "Short">(() => BetPrefill.consumeRegimeSide() ?? "Long");
  const [stake, setStake] = useState(50);
  const [status, setStatus] = useState<{ message: string; variant: "error" | "working" | "ok" } | null>(null);

  const quote      = side === "Long" ? regime.long_quote : regime.short_quote;
  const collateral = Number.parseFloat(quote.collateral_required_display) || 0;
  const fee        = Number.parseFloat(quote.fee_paid_display) || 0;
  const level      = Number.parseFloat(regime.level_display) || 0;
  const levelMin   = Math.max(0, level - market.crowdSigma * 3);
  const levelMax   = level + market.crowdSigma * 3;

  const onSubmit = () => setStatus({
    message: "Wallet memo signing is available in the Android demo (Solana Mobile Wallet Adapter).",
    variant: "error",
  });

  const accentColor = side === "Long"
    ? "linear-gradient(90deg, var(--pb-long), #1db87a)"
    : "linear-gradient(90deg, var(--pb-short), #ff7a7a)";

  return (
    <SheetChrome accentColor={accentColor} onDismiss={onDismiss}>
      {/* Header */}
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6 }}>
        <TagPill colorVar="var(--pb-long)" filled>REGIME</TagPill>
        <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11, letterSpacing: "0.06em" }}>{regime.symbol}</span>
      </div>
      <h2 style={{ margin: "0 0 10px", fontSize: 19, fontWeight: 700, letterSpacing: "-0.01em", lineHeight: 1.3 }}>
        {market.title}
      </h2>

      {/* Level + distribution preview */}
      <div style={{ display: "flex", gap: 16, alignItems: "flex-end", marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em", marginBottom: 2 }}>INDEX LEVEL</div>
          <div className="pb-mono" style={{ fontSize: 22, fontWeight: 700, color: "var(--pb-long)" }}>
            {compactDecimal(level, 3)}
          </div>
        </div>
        <div style={{ flex: 1 }}>
          <DistBar mu={level} sigma={market.crowdSigma} muMin={levelMin} muMax={levelMax} color="var(--pb-long)" height={5} />
        </div>
        <MiniDistCurve mu={market.crowdMu} sigma={market.crowdSigma} muMin={market.muMin} muMax={market.muMax} color="var(--pb-long)" width={56} height={32} />
      </div>

      {/* Basket summary */}
      <div style={{
        padding: "10px 14px", borderRadius: 12, marginBottom: 18,
        background: "var(--pb-surface-el)", border: "1px solid var(--pb-border)",
        fontSize: 13, color: "var(--pb-text-sec)",
      }}>
        <span style={{ color: "var(--pb-text)", fontWeight: 600 }}>
          {regime.constituents.length} legs:{" "}
        </span>
        {regime.constituents.slice(0, 4).map((c) => c.label).join(" · ")}
        {regime.constituents.length > 4 && ` + ${regime.constituents.length - 4} more`}
      </div>

      <SideToggle side={side} onChange={(s) => { setSide(s); setStatus(null); }} />
      <StakeSelector stake={stake} onChange={setStake} />

      <QuoteCard>
        <StatRow label="Entry level"  value={quote.entry_level_display.slice(0, 8)} accent="var(--pb-crowd)" />
        <StatRow label="Token price"  value={quote.token_price_display.slice(0, 8)} />
        <StatRow label="Collateral"   value={compactDecimal(collateral, 4)} />
        <StatRow label="Fee"          value={compactDecimal(fee, 4)} />
        <CompactDivider />
        <StatRow label="Max win"  value={`+$${compactDecimal(stake * 2, 2)}`}  accent="var(--pb-long)"  strong />
        <StatRow label="Max loss" value={`−$${compactDecimal(stake, 2)}`}       accent="var(--pb-short)" />
      </QuoteCard>

      {status && <div style={{ marginBottom: 14 }}><StatusBlockSimple message={status.message} variant={status.variant} /></div>}

      <PrimaryButton
        label={side === "Long" ? "Sign long memo · devnet" : "Sign short memo · devnet"}
        accent={side === "Long" ? "var(--pb-long)" : "var(--pb-short)"}
        foreground="#fff"
        onClick={onSubmit}
      />

      <p style={{ color: "var(--pb-text-dim)", fontSize: 12, marginTop: 10, lineHeight: 1.5 }}>
        {side === "Long" ? "Profits if the index level rises." : "Profits if the index level falls."}
        {" "}Memo signed on devnet via Solana Mobile Wallet Adapter (Android only).
      </p>
    </SheetChrome>
  );
}

/* ═══════════════════════════════════════════════════════════
   PerpBetSheet
═══════════════════════════════════════════════════════════ */
export function PerpBetSheet({
  market, perp, onDismiss,
}: { market: MarketListing; perp: DemoPerpMarket; onDismiss: () => void }) {
  const [side, setSide]   = useState<"Long" | "Short">(() => BetPrefill.consumePerpSide() ?? "Long");
  const [stake, setStake] = useState(50);
  const [status, setStatus] = useState<{ message: string; variant: "error" | "working" | "ok" } | null>(null);

  const quote      = side === "Long" ? perp.long_quote : perp.short_quote;
  const collateral = Number.parseFloat(quote.collateral_required_display) || 0;
  const fee        = Number.parseFloat(quote.fee_paid_display) || 0;
  const estFunding = Number.parseFloat(quote.estimated_funding_display) || 0;
  const funding    = Number.parseFloat(perp.spot_funding_rate_display) || 0;
  const mark       = Number.parseFloat(perp.mark_price_display) || 0;
  const ammSigma   = Number.parseFloat(perp.amm_sigma_display) || 8;
  const muMin      = mark - ammSigma * 4;
  const muMax      = mark + ammSigma * 4;

  const fundingColor = funding > 0 ? "var(--pb-long)" : "var(--pb-short)";
  const onSubmit = () => setStatus({
    message: "Perp memo signing is available in the Android demo (Solana Mobile Wallet Adapter).",
    variant: "error",
  });

  const accentColor = side === "Long"
    ? "linear-gradient(90deg, var(--pb-long), #1db87a)"
    : "linear-gradient(90deg, var(--pb-short), #ff7a7a)";

  return (
    <SheetChrome accentColor={accentColor} onDismiss={onDismiss}>
      {/* Header */}
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6 }}>
        <TagPill colorVar="var(--pb-warn)" filled>PERP</TagPill>
        <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11, letterSpacing: "0.06em" }}>{perp.symbol}</span>
      </div>
      <h2 style={{ margin: "0 0 10px", fontSize: 19, fontWeight: 700, letterSpacing: "-0.01em", lineHeight: 1.3 }}>
        {market.title}
      </h2>

      {/* Mark price + distribution preview */}
      <div style={{ display: "flex", gap: 16, alignItems: "flex-end", marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em", marginBottom: 2 }}>MARK PRICE</div>
          <div className="pb-mono" style={{ fontSize: 22, fontWeight: 700, color: "var(--pb-you)" }}>
            ${perp.mark_price_display.slice(0, 7)}
          </div>
        </div>
        <div>
          <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em", marginBottom: 2 }}>FUNDING</div>
          <div className="pb-mono" style={{ fontSize: 22, fontWeight: 700, color: fundingColor }}>
            {funding >= 0 ? "+" : ""}{(funding * 100).toFixed(3)}%
          </div>
        </div>
        <div style={{ flex: 1 }}>
          <DistBar mu={mark} sigma={ammSigma} muMin={muMin} muMax={muMax} color="var(--pb-warn)" height={5} />
        </div>
        <MiniDistCurve mu={mark} sigma={ammSigma} muMin={muMin} muMax={muMax} color="var(--pb-warn)" width={56} height={32} />
      </div>

      {/* Funding context */}
      <div style={{
        padding: "10px 14px", borderRadius: 12, marginBottom: 18,
        background: "var(--pb-surface-el)", border: "1px solid var(--pb-border)",
        fontSize: 13, color: "var(--pb-text-sec)",
      }}>
        <span style={{ color: fundingColor, fontWeight: 700 }}>
          {funding > 0 ? "Longs pay funding" : funding < 0 ? "Shorts pay funding" : "Funding neutral"}
        </span>
        {" "}— AMM curve diverges from anchor when you enter. Funding closes the gap.
      </div>

      <SideToggle side={side} onChange={(s) => { setSide(s); setStatus(null); }} />
      <StakeSelector stake={stake} onChange={setStake} />

      <QuoteCard>
        <StatRow label="Mark"         value={quote.close_mark_display.slice(0, 10)} />
        <StatRow label="Est. funding" value={compactDecimal(estFunding, 4)} />
        <StatRow label="Collateral"   value={compactDecimal(collateral, 4)} />
        <StatRow label="Fee"          value={compactDecimal(fee, 4)} />
        <CompactDivider />
        <StatRow label="Max win"  value={`+$${compactDecimal(stake * 1.6, 2)}`} accent="var(--pb-long)"  strong />
        <StatRow label="Max loss" value={`−$${compactDecimal(stake, 2)}`}        accent="var(--pb-short)" />
      </QuoteCard>

      {status && <div style={{ marginBottom: 14 }}><StatusBlockSimple message={status.message} variant={status.variant} /></div>}

      <PrimaryButton
        label={side === "Long" ? "Sign long perp memo · devnet" : "Sign short perp memo · devnet"}
        accent={side === "Long" ? "var(--pb-long)" : "var(--pb-short)"}
        foreground="#fff"
        onClick={onSubmit}
      />

      <p style={{ color: "var(--pb-text-dim)", fontSize: 12, marginTop: 10, lineHeight: 1.5 }}>
        {side === "Long" ? "Long profits when mark drifts above anchor." : "Short profits when mark drifts below anchor."}
        {" "}Signed on devnet via Solana Mobile Wallet Adapter (Android only).
      </p>
    </SheetChrome>
  );
}
