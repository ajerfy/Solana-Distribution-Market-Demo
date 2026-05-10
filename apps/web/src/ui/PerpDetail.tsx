import type { DemoPerpMarket, MarketListing } from "../domain/types";
import { BetPrefill } from "../domain/betPrefill";
import { compactDecimal } from "../domain/format";
import { useParabolaStore } from "../state/parabolaStore";
import {
  Card, CompactDivider, DetailBackBar, DistBar,
  HeroMetric, MetricPill, MiniDistCurve, PrimaryButton, StatRow, TagPill,
} from "./shared";
import { Sparkline } from "./Sparkline";

/* ── AMM vs anchor curve ─────────────────────────────────── */
function PerpCurve({ perp }: { perp: DemoPerpMarket }) {
  const pts = perp.curve_points;
  if (pts.length < 2) return <p style={{ color: "var(--pb-text-dim)", fontSize: 13 }}>Curve not available.</p>;

  const xs   = pts.map((p) => p.x);
  const xMin = Math.min(...xs);
  const xMax = Math.max(...xs);
  const yMax = Math.max(...pts.flatMap((p) => [p.amm, p.anchor]), 1e-6);
  const W = 320; const H = 160; const pad = 14;
  const plotW = W - pad * 2; const plotH = H - pad * 2;
  const xOf = (x: number) => pad + ((x - xMin) / (xMax - xMin || 1)) * plotW;
  const yOf = (y: number) => pad + plotH - (y / yMax) * plotH;

  const path = (key: "amm" | "anchor") =>
    pts.map((p, i) => `${i === 0 ? "M" : "L"} ${xOf(p.x)} ${yOf(p[key])}`).join(" ");

  /* fill under AMM curve */
  const ammLine = path("amm");
  const ammFill = `${ammLine} L ${xOf(pts[pts.length - 1]!.x)} ${pad + plotH} L ${xOf(pts[0]!.x)} ${pad + plotH} Z`;

  return (
    <svg width="100%" height={H} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none"
      style={{ borderRadius: 14, background: "var(--pb-surface-el)" }}>
      {/* grid */}
      {[1, 2, 3].map((i) => (
        <line key={i} x1={pad} x2={pad + plotW} y1={pad + plotH * (i / 4)} y2={pad + plotH * (i / 4)}
          stroke="var(--pb-border-strong)" strokeWidth={0.7} strokeDasharray="2 4" opacity={0.4} />
      ))}
      <path d={ammFill} fill="var(--pb-you)" opacity={0.07} />
      <path d={path("anchor")} fill="none" stroke="var(--pb-crowd)" strokeWidth={2} strokeLinecap="round" />
      <path d={ammLine}        fill="none" stroke="var(--pb-you)"   strokeWidth={2.5} strokeLinecap="round" />
    </svg>
  );
}

/* ═══════════════════════════════════════════════════════════
   PerpDetail
═══════════════════════════════════════════════════════════ */
export function PerpDetail({ market, perp }: { market: MarketListing; perp: DemoPerpMarket }) {
  const closeMarket    = useParabolaStore((s) => s.closeMarket);
  const setShowBetSheet = useParabolaStore((s) => s.setShowBetSheet);

  const funding      = Number.parseFloat(perp.spot_funding_rate_display) || 0;
  const fundingColor = funding > 0 ? "var(--pb-long)" : "var(--pb-short)";
  const mark         = Number.parseFloat(perp.mark_price_display) || 0;
  const anchorMu     = Number.parseFloat(perp.anchor_mu_display) || 0;
  const ammSigma     = Number.parseFloat(perp.amm_sigma_display) || Number.parseFloat(perp.anchor_sigma_display) || 8;

  const fundingPts = perp.funding_path
    .map((p) => Number.parseFloat(p.funding_rate_display))
    .filter((n) => !Number.isNaN(n));

  const muMin = mark - ammSigma * 4;
  const muMax = mark + ammSigma * 4;

  return (
    <div className="pb-scroll" style={{ background: "var(--pb-bg)", minHeight: "100vh" }}>
      <DetailBackBar onBack={closeMarket} trailing={`${perp.symbol} · perp`} />

      {/* ── Hero ── */}
      <div style={{ padding: "18px 20px 0" }}>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center", marginBottom: 10 }}>
          <TagPill colorVar="var(--pb-warn)" filled>PERP</TagPill>
          <TagPill colorVar="var(--pb-chain)">{perp.symbol}</TagPill>
          <span style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
            · funding every {perp.funding_interval} slots
          </span>
        </div>

        <h1 style={{ margin: "0 0 6px", fontSize: 22, fontWeight: 700, letterSpacing: "-0.02em" }}>
          {market.title}
        </h1>
        <p style={{ margin: "0 0 18px", color: "var(--pb-text-sec)", fontSize: 14, lineHeight: 1.5 }}>
          {market.subtitle}
        </p>

        {/* Mark price + funding as hero metrics */}
        <div style={{ display: "flex", gap: 20, alignItems: "flex-end", marginBottom: 14 }}>
          <HeroMetric label="MARK PRICE" value={`$${perp.mark_price_display.slice(0, 7)}`} color="var(--pb-you)" />
          <HeroMetric
            label="SPOT FUNDING"
            value={`${funding >= 0 ? "+" : ""}${(funding * 100).toFixed(3)}%`}
            color={fundingColor}
          />
          <div style={{ flex: 1 }} />
          <MiniDistCurve mu={mark} sigma={ammSigma} muMin={muMin} muMax={muMax} color="var(--pb-warn)" width={72} height={38} />
        </div>

        {/* Distribution bar: AMM distribution around mark */}
        <DistBar mu={mark} sigma={ammSigma} muMin={muMin} muMax={muMax} color="var(--pb-warn)" height={6} />
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 5, marginBottom: 20 }}>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>${compactDecimal(muMin, 0)}</span>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-warn)", opacity: 0.7 }}>AMM ±1σ</span>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>${compactDecimal(muMax, 0)}</span>
        </div>

        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 20 }}>
          <MetricPill label="ANCHOR μ" value={perp.anchor_mu_display.slice(0, 7)}  accent="var(--pb-crowd)" />
          <MetricPill label="AMM μ"    value={perp.amm_mu_display.slice(0, 7)}     accent="var(--pb-you)" />
          <MetricPill label="σ"        value={perp.amm_sigma_display.slice(0, 6)} />
          <MetricPill label="OPEN POS" value={String(perp.open_positions)} />
        </div>

        {/* Funding explanation */}
        <div style={{
          padding: "10px 14px", borderRadius: 12,
          background: "var(--pb-surface-el)", border: "1px solid var(--pb-border)",
          fontSize: 13, color: "var(--pb-text-sec)", marginBottom: 20,
        }}>
          <span style={{ color: fundingColor, fontWeight: 700 }}>
            {funding > 0 ? "Longs pay shorts" : funding < 0 ? "Shorts pay longs" : "Funding neutral"}
          </span>
          {" "}— when the AMM curve drifts above the anchor, longs pay funding to bring it back; below, shorts pay.
        </div>
      </div>

      {/* ── AMM vs anchor curve ── */}
      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
          <span style={{ fontWeight: 700 }}>AMM vs anchor distribution</span>
          <span className="pb-mono" style={{ color: "var(--pb-crowd)", fontSize: 11 }}>
            KL {perp.kl_display.slice(0, 6)}
          </span>
        </div>
        <p style={{ color: "var(--pb-text-sec)", fontSize: 12, margin: "0 0 10px" }}>
          The AMM's Normal distribution (green) vs. the oracle anchor (blue). Funding flows in the direction that closes the gap.
        </p>
        <PerpCurve perp={perp} />
        <div style={{ display: "flex", gap: 16, marginTop: 10 }}>
          <span style={{ fontSize: 11, color: "var(--pb-text-sec)", display: "flex", alignItems: "center", gap: 5 }}>
            <span style={{ width: 8, height: 2, background: "var(--pb-you)", display: "inline-block", borderRadius: 2 }} />
            AMM
          </span>
          <span style={{ fontSize: 11, color: "var(--pb-text-sec)", display: "flex", alignItems: "center", gap: 5 }}>
            <span style={{ width: 8, height: 2, background: "var(--pb-crowd)", display: "inline-block", borderRadius: 2 }} />
            Anchor
          </span>
        </div>
      </Card>

      {/* ── Funding history ── */}
      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <span style={{ fontWeight: 700 }}>Funding rate history</span>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>next slot {perp.next_funding_slot}</span>
        </div>
        {fundingPts.length >= 2 ? (
          <Sparkline values={fundingPts} color="var(--pb-warn)" width={320} height={64} />
        ) : (
          <p style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>Not enough funding history yet.</p>
        )}
      </Card>

      {/* ── Open positions ── */}
      {perp.positions.length > 0 && (
        <Card style={{ margin: "0 20px 120px" }}>
          <div style={{ fontWeight: 700, marginBottom: 12 }}>Open positions</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {perp.positions.map((pos) => {
              const isLong = pos.side.toLowerCase() === "long";
              const sideColor = isLong ? "var(--pb-long)" : "var(--pb-short)";
              return (
                <div key={pos.id} style={{
                  borderRadius: 12, padding: "12px 14px",
                  background: "var(--pb-surface-el)", border: "1px solid var(--pb-border)",
                }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <span className="pb-mono" style={{ fontWeight: 700, color: sideColor, fontSize: 14 }}>
                        {pos.side.toUpperCase()}
                      </span>
                      <span className="pb-mono" style={{ fontSize: 12, color: "var(--pb-text-sec)", marginLeft: 10 }}>
                        entry μ {pos.entry_mu_display.slice(0, 7)}
                      </span>
                    </div>
                    <span style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>{pos.status}</span>
                  </div>
                  <CompactDivider />
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <MetricPill label="COLLATERAL"  value={pos.collateral_display.slice(0, 8)} />
                    <MetricPill label="MARK PAYOUT" value={pos.mark_payout_display.slice(0, 8)} />
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      )}

      {perp.positions.length === 0 && <div style={{ height: 40 }} />}

      {/* ── Fixed CTA ── */}
      <div style={{
        position: "fixed", bottom: 0, left: 0, right: 0,
        display: "flex", gap: 10,
        padding: "16px 20px calc(16px + env(safe-area-inset-bottom))",
        background: "linear-gradient(transparent, var(--pb-bg) 40%)", zIndex: 30,
      }}>
        <div style={{ flex: 1 }}>
          <PrimaryButton label="Long" accent="var(--pb-long)" foreground="#fff"
            onClick={() => { BetPrefill.perpSide = "Long"; setShowBetSheet(true); }} />
        </div>
        <div style={{ flex: 1 }}>
          <PrimaryButton label="Short" accent="var(--pb-short)" foreground="#fff"
            onClick={() => { BetPrefill.perpSide = "Short"; setShowBetSheet(true); }} />
        </div>
      </div>
    </div>
  );
}
