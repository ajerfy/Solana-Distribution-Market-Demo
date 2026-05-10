import type { DemoRegimeIndex } from "../domain/types";
import type { MarketListing } from "../domain/types";
import { compactDecimal } from "../domain/format";
import { BetPrefill } from "../domain/betPrefill";
import { useParabolaStore } from "../state/parabolaStore";
import {
  Card,
  DetailBackBar,
  MetricPill,
  PrimaryButton,
  SectionLabel,
  TagPill,
} from "./shared";
import { Sparkline } from "./Sparkline";

export function RegimeDetail({
  market,
  regime,
}: {
  market: MarketListing;
  regime: DemoRegimeIndex;
}) {
  const closeMarket = useParabolaStore((s) => s.closeMarket);
  const setShowBetSheet = useParabolaStore((s) => s.setShowBetSheet);

  const change = Number.parseFloat(regime.change_display) || 0;
  const changeColor =
    change > 0 ? "var(--pb-long)" : change < 0 ? "var(--pb-short)" : "var(--pb-text-sec)";

  const historyPts = regime.history
    .map((p) => Number.parseFloat(p.level_display))
    .filter((n) => !Number.isNaN(n));

  return (
    <div className="pb-scroll" style={{ background: "var(--pb-bg)", minHeight: "100vh" }}>
      <DetailBackBar onBack={closeMarket} trailing={`${regime.symbol} · level`} />

      <div style={{ padding: "0 20px 14px", display: "flex", flexDirection: "column", gap: 10 }}>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center" }}>
          <TagPill colorVar="var(--pb-long)" filled>
            THEME
          </TagPill>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
            Symbol {regime.symbol}
          </span>
        </div>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 600 }}>{market.title}</h1>
        <div style={{ display: "flex", alignItems: "baseline", gap: 10, flexWrap: "wrap" }}>
          <span className="pb-mono" style={{ fontSize: 28, fontWeight: 800 }}>
            {regime.level_display.slice(0, 8)}
          </span>
          <span className="pb-mono" style={{ color: changeColor, fontWeight: 600 }}>
            {change >= 0 ? "+" : ""}
            {compactDecimal(change, 3)} since last rebalance
          </span>
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <MetricPill label="LAST" value={regime.previous_level_display.slice(0, 7)} />
          <MetricPill
            label="REBALANCE"
            value={`slot ${regime.next_rebalance_slot}`}
            accent="var(--pb-crowd)"
          />
          <MetricPill label="LEGS" value={String(regime.constituents.length)} />
        </div>
      </div>

      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <span style={{ fontWeight: 700 }}>Level history</span>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
            {historyPts.length} pts
          </span>
        </div>
        <div style={{ marginTop: 10 }}>
          {historyPts.length >= 2 ? (
            <Sparkline values={historyPts} color="var(--pb-long)" width={320} height={96} />
          ) : (
            <p style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>
              Not enough history points yet.
            </p>
          )}
        </div>
      </Card>

      {regime.thesis.trim() ? (
        <Card style={{ margin: "0 20px 14px" }}>
          <SectionLabel>Thesis</SectionLabel>
          <p style={{ marginTop: 8, color: "var(--pb-text)" }}>{regime.thesis}</p>
        </Card>
      ) : null}

      <Card style={{ margin: "0 20px 120px" }}>
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <span style={{ fontWeight: 700 }}>Basket legs</span>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
            {regime.constituents.length} markets
          </span>
        </div>
        <div style={{ marginTop: 12, display: "flex", flexDirection: "column", gap: 8 }}>
          {regime.constituents.map((c) => (
            <div
              key={c.id}
              style={{
                borderRadius: 10,
                padding: 12,
                background: "var(--pb-surface-el)",
              }}
            >
              <div style={{ fontWeight: 600 }}>{c.label}</div>
              <div className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
                {c.side.toUpperCase()} · {(c.weight_bps / 100).toFixed(1)}% weight
              </div>
              <div
                className="pb-mono"
                style={{
                  marginTop: 4,
                  fontWeight: 700,
                  color: c.side.toLowerCase() === "long" ? "var(--pb-long)" : "var(--pb-short)",
                }}
              >
                {c.probability_display.slice(0, 6)}%
              </div>
            </div>
          ))}
        </div>
      </Card>

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
              BetPrefill.regimeSide = "Long";
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
              BetPrefill.regimeSide = "Short";
              setShowBetSheet(true);
            }}
          />
        </div>
      </div>
    </div>
  );
}
