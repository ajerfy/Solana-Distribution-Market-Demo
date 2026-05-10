import type { DemoRegimeIndex, MarketListing } from "../domain/types";
import { compactDecimal } from "../domain/format";
import { BetPrefill } from "../domain/betPrefill";
import { useParabolaStore } from "../state/parabolaStore";
import {
  Card, CompactDivider, DetailBackBar, DistBar,
  HeroMetric, MetricPill, MiniDistCurve, PrimaryButton, SectionLabel, StatRow, TagPill,
} from "./shared";
import { Sparkline } from "./Sparkline";

export function RegimeDetail({ market, regime }: { market: MarketListing; regime: DemoRegimeIndex }) {
  const closeMarket    = useParabolaStore((s) => s.closeMarket);
  const setShowBetSheet = useParabolaStore((s) => s.setShowBetSheet);

  const level  = Number.parseFloat(regime.level_display) || 0;
  const change = Number.parseFloat(regime.change_display) || 0;
  const changeColor = change > 0 ? "var(--pb-long)" : change < 0 ? "var(--pb-short)" : "var(--pb-text-sec)";

  const historyPts = regime.history
    .map((p) => Number.parseFloat(p.level_display))
    .filter((n) => !Number.isNaN(n));

  const levelMin  = Math.max(0, level - market.crowdSigma * 3);
  const levelMax  = level + market.crowdSigma * 3;

  return (
    <div className="pb-scroll" style={{ background: "var(--pb-bg)", minHeight: "100vh" }}>
      <DetailBackBar onBack={closeMarket} trailing={`${regime.symbol} · regime`} />

      {/* ── Hero ── */}
      <div style={{ padding: "18px 20px 0" }}>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center", marginBottom: 10 }}>
          <TagPill colorVar="var(--pb-long)" filled>REGIME</TagPill>
          <TagPill colorVar="var(--pb-chain)">{regime.symbol}</TagPill>
          <span style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>· {regime.constituents.length} legs</span>
        </div>

        <h1 style={{ margin: "0 0 6px", fontSize: 22, fontWeight: 700, letterSpacing: "-0.02em" }}>
          {market.title}
        </h1>
        <p style={{ margin: "0 0 18px", color: "var(--pb-text-sec)", fontSize: 14, lineHeight: 1.5 }}>
          {regime.thesis.trim() || market.subtitle}
        </p>

        <div style={{ display: "flex", gap: 20, alignItems: "flex-end", marginBottom: 14 }}>
          <HeroMetric label="INDEX LEVEL" value={compactDecimal(level, 3)} color="var(--pb-long)" />
          <HeroMetric
            label="CHANGE"
            value={`${change >= 0 ? "+" : ""}${compactDecimal(change, 3)}`}
            color={changeColor}
          />
          <div style={{ flex: 1 }} />
          <MiniDistCurve
            mu={market.crowdMu} sigma={market.crowdSigma}
            muMin={market.muMin} muMax={market.muMax}
            color="var(--pb-long)" width={72} height={38}
          />
        </div>

        <DistBar mu={level} sigma={market.crowdSigma} muMin={levelMin} muMax={levelMax} color="var(--pb-long)" height={6} />
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 5, marginBottom: 20 }}>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>{compactDecimal(levelMin, 1)}</span>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-long)", opacity: 0.7 }}>±1σ spread</span>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>{compactDecimal(levelMax, 1)}</span>
        </div>

        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 20 }}>
          <MetricPill label="PREV LEVEL"  value={regime.previous_level_display.slice(0, 7)} />
          <MetricPill label="REBALANCE"   value={`slot ${regime.next_rebalance_slot}`} accent="var(--pb-crowd)" />
          <MetricPill label="LEGS"        value={String(regime.constituents.length)} />
          <MetricPill label="VOLUME"      value={`$${compactDecimal(market.volumeUsd / 1000, 0)}k`} />
        </div>
      </div>

      {/* ── Level history ── */}
      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <span style={{ fontWeight: 700 }}>Level history</span>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>{historyPts.length} pts</span>
        </div>
        {historyPts.length >= 2 ? (
          <Sparkline values={historyPts} color="var(--pb-long)" width={320} height={96} />
        ) : (
          <p style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>Not enough history yet.</p>
        )}
      </Card>

      {/* ── Basket legs ── */}
      <Card style={{ margin: "0 20px 120px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
          <span style={{ fontWeight: 700 }}>Basket legs</span>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>{regime.constituents.length} markets</span>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {regime.constituents.map((c) => {
            const prob      = Number.parseFloat(c.probability_display) || 0;
            const prevProb  = Number.parseFloat(c.previous_probability_display) || 0;
            const delta     = prob - prevProb;
            const isLong    = c.side.toLowerCase() === "long";
            const sideColor = isLong ? "var(--pb-long)" : "var(--pb-short)";
            return (
              <div key={c.id} style={{
                borderRadius: 12, padding: "12px 14px",
                background: "var(--pb-surface-el)", border: "1px solid var(--pb-border)",
              }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 14 }}>{c.label}</div>
                    <div className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11, marginTop: 2 }}>
                      {c.side.toUpperCase()} · {(c.weight_bps / 100).toFixed(1)}% weight
                    </div>
                  </div>
                  <div style={{ textAlign: "right", flexShrink: 0, marginLeft: 12 }}>
                    <div className="pb-mono" style={{ fontWeight: 700, fontSize: 16, color: sideColor }}>
                      {prob.toFixed(1)}%
                    </div>
                    {Math.abs(delta) > 0.01 && (
                      <div className="pb-mono" style={{ fontSize: 10, color: delta > 0 ? "var(--pb-long)" : "var(--pb-short)" }}>
                        {delta > 0 ? "+" : ""}{delta.toFixed(1)}%
                      </div>
                    )}
                  </div>
                </div>
                {/* probability bar */}
                <div style={{ marginTop: 8, height: 3, borderRadius: 99, background: "var(--pb-surface-muted)" }}>
                  <div style={{ height: "100%", width: `${prob}%`, background: sideColor, borderRadius: 99, opacity: 0.6 }} />
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ── Fixed CTA ── */}
      <div style={{
        position: "fixed", bottom: 0, left: 0, right: 0,
        display: "flex", gap: 10,
        padding: "16px 20px calc(16px + env(safe-area-inset-bottom))",
        background: "linear-gradient(transparent, var(--pb-bg) 40%)", zIndex: 30,
      }}>
        <div style={{ flex: 1 }}>
          <PrimaryButton label="Long" accent="var(--pb-long)" foreground="#fff"
            onClick={() => { BetPrefill.regimeSide = "Long"; setShowBetSheet(true); }} />
        </div>
        <div style={{ flex: 1 }}>
          <PrimaryButton label="Short" accent="var(--pb-short)" foreground="#fff"
            onClick={() => { BetPrefill.regimeSide = "Short"; setShowBetSheet(true); }} />
        </div>
      </div>
    </div>
  );
}
