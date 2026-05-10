import type { DemoPerpMarket } from "../domain/types";
import type { MarketListing } from "../domain/types";
import { BetPrefill } from "../domain/betPrefill";
import { useParabolaStore } from "../state/parabolaStore";
import {
  Card,
  DetailBackBar,
  MetricPill,
  PrimaryButton,
  TagPill,
} from "./shared";
import { Sparkline } from "./Sparkline";

function PerpCurveCanvas({ perp }: { perp: DemoPerpMarket }) {
  const pts = perp.curve_points;
  if (pts.length < 2) {
    return (
      <p style={{ color: "var(--pb-text-dim)", fontSize: 13 }}>Curve not available.</p>
    );
  }
  const xs = pts.map((p) => p.x);
  const xMin = Math.min(...xs);
  const xMax = Math.max(...xs);
  const yMax = Math.max(...pts.flatMap((p) => [p.amm, p.anchor]), 1e-6);
  const W = 320;
  const H = 160;
  const pad = 14;
  const plotW = W - pad * 2;
  const plotH = H - pad * 2;
  const xOf = (x: number) =>
    pad + ((x - xMin) / (xMax - xMin || 1)) * plotW;
  const yOf = (y: number) => pad + plotH - (y / yMax) * plotH;

  const path = (key: "amm" | "anchor") =>
    pts
      .map((p, i) => {
        const x = xOf(p.x);
        const y = yOf(p[key]);
        return `${i === 0 ? "M" : "L"} ${x} ${y}`;
      })
      .join(" ");

  return (
    <svg
      width="100%"
      height={H}
      viewBox={`0 0 ${W} ${H}`}
      preserveAspectRatio="none"
      style={{ borderRadius: 12, background: "var(--pb-surface-el)" }}
    >
      <path
        d={path("anchor")}
        fill="none"
        stroke="var(--pb-crowd)"
        strokeWidth={2}
        strokeLinecap="round"
      />
      <path
        d={path("amm")}
        fill="none"
        stroke="var(--pb-you)"
        strokeWidth={2.5}
        strokeLinecap="round"
      />
    </svg>
  );
}

export function PerpDetail({
  market,
  perp,
}: {
  market: MarketListing;
  perp: DemoPerpMarket;
}) {
  const closeMarket = useParabolaStore((s) => s.closeMarket);
  const setShowBetSheet = useParabolaStore((s) => s.setShowBetSheet);

  const funding = Number.parseFloat(perp.spot_funding_rate_display) || 0;
  const fundingColor =
    funding > 0 ? "var(--pb-long)" : "var(--pb-short)";

  const fundingPts = perp.funding_path
    .map((p) => Number.parseFloat(p.funding_rate_display))
    .filter((n) => !Number.isNaN(n));

  return (
    <div className="pb-scroll" style={{ background: "var(--pb-bg)", minHeight: "100vh" }}>
      <DetailBackBar onBack={closeMarket} trailing={`${perp.symbol} · perpetual`} />

      <div style={{ padding: "0 20px 14px", display: "flex", flexDirection: "column", gap: 10 }}>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center" }}>
          <TagPill colorVar="var(--pb-warn)" filled>
            PERP
          </TagPill>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
            {perp.symbol} · funding every {perp.funding_interval} slots
          </span>
        </div>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 600 }}>{market.title}</h1>
        <div style={{ display: "flex", alignItems: "baseline", gap: 10, flexWrap: "wrap" }}>
          <span className="pb-mono" style={{ fontSize: 28, fontWeight: 800 }}>
            ${perp.mark_price_display.slice(0, 7)}
          </span>
          <span className="pb-mono" style={{ color: fundingColor, fontWeight: 600 }}>
            Mark · funding {funding >= 0 ? "+" : ""}
            {(funding * 100).toFixed(3)}%
          </span>
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <MetricPill label="ANCHOR μ" value={perp.anchor_mu_display.slice(0, 6)} accent="var(--pb-crowd)" />
          <MetricPill label="AMM μ" value={perp.amm_mu_display.slice(0, 6)} accent="var(--pb-you)" />
          <MetricPill label="σ" value={perp.amm_sigma_display.slice(0, 5)} />
          <MetricPill label="OPEN" value={String(perp.open_positions)} />
        </div>
      </div>

      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <span style={{ fontWeight: 700 }}>AMM vs anchor curve</span>
          <span className="pb-mono" style={{ color: "var(--pb-crowd)", fontSize: 11 }}>
            KL {perp.kl_display.slice(0, 6)}
          </span>
        </div>
        <p style={{ color: "var(--pb-text-sec)", fontSize: 12, marginTop: 6 }}>
          Long pays funding when AMM is above anchor; short pays when below.
        </p>
        <div style={{ marginTop: 10 }}>
          <PerpCurveCanvas perp={perp} />
        </div>
        <div style={{ display: "flex", gap: 12, marginTop: 10, flexWrap: "wrap" }}>
          <span style={{ fontSize: 11, color: "var(--pb-text-sec)" }}>
            <span style={{ color: "var(--pb-you)" }}>●</span> AMM
          </span>
          <span style={{ fontSize: 11, color: "var(--pb-text-sec)" }}>
            <span style={{ color: "var(--pb-crowd)" }}>●</span> Anchor
          </span>
        </div>
      </Card>

      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <span style={{ fontWeight: 700 }}>Funding history</span>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
            next slot {perp.next_funding_slot}
          </span>
        </div>
        <div style={{ marginTop: 10 }}>
          {fundingPts.length >= 2 ? (
            <Sparkline values={fundingPts} color="var(--pb-warn)" width={320} height={64} />
          ) : (
            <p style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>
              Not enough funding history yet.
            </p>
          )}
        </div>
      </Card>

      {perp.positions.length > 0 ? (
        <Card style={{ margin: "0 20px 120px" }}>
          <div style={{ fontWeight: 700, marginBottom: 10 }}>Open positions</div>
          {perp.positions.map((pos) => (
            <div
              key={pos.id}
              style={{
                borderRadius: 10,
                padding: 12,
                marginBottom: 8,
                background: "var(--pb-surface-el)",
              }}
            >
              <div
                className="pb-mono"
                style={{
                  fontWeight: 700,
                  color:
                    pos.side.toLowerCase() === "long"
                      ? "var(--pb-long)"
                      : "var(--pb-short)",
                }}
              >
                {pos.side.toUpperCase()}
              </div>
              <div className="pb-mono" style={{ fontSize: 12, color: "var(--pb-text-sec)" }}>
                entry μ {pos.entry_mu_display.slice(0, 7)}
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", marginTop: 6 }}>
                <span className="pb-mono" style={{ fontWeight: 600 }}>
                  {pos.mark_payout_display.slice(0, 8)}
                </span>
                <span style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>{pos.status}</span>
              </div>
            </div>
          ))}
        </Card>
      ) : (
        <div style={{ height: 40 }} />
      )}

      <div
        style={{
          position: "fixed",
          bottom: 0,
          left: 0,
          right: 0,
          display: "flex",
          gap: 10,
          padding: "16px 20px calc(16px + env(safe-area-inset-bottom))",
          background: "linear-gradient(transparent, var(--pb-bg) 35%)",
          zIndex: 30,
        }}
      >
        <div style={{ flex: 1 }}>
          <PrimaryButton
            label="Long"
            accent="var(--pb-long)"
            foreground="#fff"
            onClick={() => {
              BetPrefill.perpSide = "Long";
              setShowBetSheet(true);
            }}
          />
        </div>
        <div style={{ flex: 1 }}>
          <PrimaryButton
            label="Short"
            accent="var(--pb-short)"
            foreground="#fff"
            onClick={() => {
              BetPrefill.perpSide = "Short";
              setShowBetSheet(true);
            }}
          />
        </div>
      </div>
    </div>
  );
}
