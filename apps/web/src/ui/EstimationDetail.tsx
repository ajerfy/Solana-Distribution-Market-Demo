import { useMemo, useState } from "react";
import type { ActivityEvent, MarketListing } from "../domain/types";
import { CATEGORY_META } from "../domain/types";
import { BetPrefill } from "../domain/betPrefill";
import { mockActivityForMarket } from "../domain/mockMarkets";
import { compactDecimal, formatVolume, shortHash } from "../domain/format";
import { useParabolaStore } from "../state/parabolaStore";
import { DistributionChart } from "./DistributionChart";
import { Sparkline } from "./Sparkline";
import {
  BackBar, Card, CompactDivider, DistBar, GhostButton, HeroMetric,
  MetricPill, MiniDistCurve, PrimaryButton, SectionLabel, StatRow,
  StatusBlockSimple, TagPill,
} from "./shared";

type DetailTab = "Bet" | "Stats" | "Flow" | "Rules";

/* ── Underline tab bar ─────────────────────────────────────── */
function TabBar({ active, onSelect }: { active: DetailTab; onSelect: (t: DetailTab) => void }) {
  const tabs: DetailTab[] = ["Bet", "Stats", "Flow", "Rules"];
  return (
    <div style={{
      display: "flex", padding: "0 20px",
      borderBottom: "1px solid var(--pb-border)",
    }}>
      {tabs.map((tab) => {
        const sel = tab === active;
        return (
          <button
            key={tab}
            type="button"
            className="pb-tab-btn"
            onClick={() => onSelect(tab)}
            style={{
              flex: 1, padding: "10px 4px", position: "relative",
              color: sel ? "var(--pb-text)" : "var(--pb-text-dim)",
              fontWeight: sel ? 700 : 400, fontSize: 14,
            }}
          >
            {tab}
            {sel && (
              <div style={{
                position: "absolute", bottom: 0, left: "10%", right: "10%",
                height: 2, borderRadius: 2, background: "var(--pb-you)",
              }} />
            )}
          </button>
        );
      })}
    </div>
  );
}

/* ── Live source reference card ────────────────────────────── */
function LiveSourceCard({ market }: { market: MarketListing }) {
  const stats = market.liveEventStats;
  if (!stats) return null;
  const cents = (p: number | null) => p != null ? `${(p * 100).toFixed(1)}¢` : "—";

  return (
    <Card style={{ margin: "0 20px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ fontWeight: 700, fontSize: 15 }}>Live source</span>
        <span style={{ flex: 1 }} />
        {market.sourceBadge && <TagPill colorVar="var(--pb-chain)">{market.sourceBadge}</TagPill>}
      </div>
      <p style={{ color: "var(--pb-text-sec)", fontSize: 13, margin: "8px 0 0" }}>
        Anchored to a live binary market and remapped onto a probability
        distribution — you bet on the shape, not just the outcome.
      </p>
      <CompactDivider />
      <StatRow label="Outcome" value={stats.outcomeLabel} strong />
      <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
        <MetricPill label="YES" value={cents(stats.yesPrice)} accent="var(--pb-long)" />
        <MetricPill label="NO"  value={cents(stats.noPrice)}  accent="var(--pb-short)" />
      </div>
      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
        <MetricPill label="BID"    value={cents(stats.bestBid)} />
        <MetricPill label="ASK"    value={cents(stats.bestAsk)} />
        <MetricPill label="SPREAD" value={cents(stats.spread)} />
      </div>
      {stats.updatedAtMillis != null && (
        <p className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11, marginTop: 10 }}>
          Updated {new Date(stats.updatedAtMillis).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" })}
        </p>
      )}
    </Card>
  );
}

/* ── Distribution slider card ──────────────────────────────── */
function SliderCard({
  market, mu, sigma, onMu, onSigma, onResetToCrowd, onOpenAdvanced,
}: {
  market: MarketListing; mu: number; sigma: number;
  onMu: (v: number) => void; onSigma: (v: number) => void;
  onResetToCrowd: () => void; onOpenAdvanced: () => void;
}) {
  const { sigmaMin, sigmaMax } = market;
  const span       = Math.max(sigmaMax - sigmaMin, 1e-4);
  const confidence = Math.min(1, Math.max(0, 1 - (sigma - sigmaMin) / span));
  const crowdConf  = (1 - (market.crowdSigma - sigmaMin) / span) * 100;

  return (
    <Card style={{ margin: "0 20px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
        <span style={{ fontWeight: 700 }}>Tune your distribution</span>
        <span style={{ flex: 1 }} />
        <button type="button" style={{ border: "none", background: "none", color: "var(--pb-text-dim)", cursor: "pointer", fontSize: 11 }} onClick={onResetToCrowd}>
          reset
        </button>
        <button type="button" style={{ border: "none", background: "none", color: "var(--pb-crowd)", cursor: "pointer", fontSize: 11, fontWeight: 700 }} onClick={onOpenAdvanced}>
          σ advanced
        </button>
      </div>
      <p style={{ color: "var(--pb-text-sec)", fontSize: 12, margin: "0 0 14px" }}>
        Slide your mean μ and pick how confident you are (narrow σ = more confident).
      </p>

      {/* μ slider */}
      <label style={{ display: "block", fontSize: 13 }}>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
          <span style={{ color: "var(--pb-text-sec)" }}>Mean μ</span>
          <span className="pb-mono" style={{ color: "var(--pb-you)", fontWeight: 700 }}>
            {compactDecimal(mu, 3)} {market.unit}
          </span>
        </div>
        <input
          type="range" min={market.muMin} max={market.muMax} step={0.001} value={mu}
          onChange={(e) => onMu(Number(e.target.value))}
          style={{ width: "100%", accentColor: "var(--pb-you)" }}
        />
        <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
          crowd μ {compactDecimal(market.crowdMu, 2)} {market.unit}
        </span>
      </label>

      {/* confidence slider */}
      <label style={{ display: "block", marginTop: 16, fontSize: 13 }}>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
          <span style={{ color: "var(--pb-text-sec)" }}>Confidence</span>
          <span className="pb-mono" style={{ color: "var(--pb-crowd)", fontWeight: 700 }}>
            {(confidence * 100).toFixed(0)}%
          </span>
        </div>
        <input
          type="range" min={0} max={1} step={0.001} value={confidence}
          onChange={(e) => { const c = Number(e.target.value); onSigma(sigmaMin + (1 - c) * span); }}
          style={{ width: "100%", accentColor: "var(--pb-crowd)" }}
        />
        <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
          crowd confidence {crowdConf.toFixed(0)}%
        </span>
      </label>

      <GhostButton label={`Advanced · σ = ${compactDecimal(sigma, 3)}`} onClick={onOpenAdvanced} />
    </Card>
  );
}

/* ── Advanced σ dialog ─────────────────────────────────────── */
function AdvancedDialog({
  market, sigma, onSigma, onDismiss,
}: { market: MarketListing; sigma: number; onSigma: (v: number) => void; onDismiss: () => void }) {
  return (
    <div
      style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.6)", backdropFilter: "blur(4px)", zIndex: 60, display: "flex", alignItems: "flex-end", justifyContent: "center", padding: 12 }}
      onClick={onDismiss}
      onKeyDown={(e) => e.key === "Escape" && onDismiss()}
      role="presentation"
    >
      <div
        className="pb-card"
        style={{ width: "100%", maxWidth: 520, boxShadow: "0 -8px 40px rgba(0,0,0,0.4)" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 17 }}>Standard deviation σ</div>
            <div style={{ color: "var(--pb-text-sec)", fontSize: 13, marginTop: 2 }}>
              Lower σ = tighter, more confident curve
            </div>
          </div>
          <button type="button" style={{ border: "none", background: "none", color: "var(--pb-crowd)", fontWeight: 700, cursor: "pointer", fontSize: 14 }} onClick={onDismiss}>
            Done
          </button>
        </div>
        <label style={{ display: "block" }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
            <span style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>Your σ</span>
            <span className="pb-mono" style={{ color: "var(--pb-long)", fontWeight: 700 }}>
              {compactDecimal(sigma, 3)}
            </span>
          </div>
          <input
            type="range" min={market.sigmaMin} max={market.sigmaMax} step={0.001} value={sigma}
            onChange={(e) => onSigma(Number(e.target.value))}
            style={{ width: "100%", accentColor: "var(--pb-long)" }}
          />
        </label>
        <CompactDivider />
        <StatRow label="Crowd σ" value={compactDecimal(market.crowdSigma, 3)} accent="var(--pb-crowd)" />
        <StatRow label="Your σ"  value={compactDecimal(sigma, 3)} accent="var(--pb-long)" strong />
        <div style={{ marginTop: 12 }}>
          <PrimaryButton label="Done" onClick={onDismiss} />
        </div>
      </div>
    </div>
  );
}

/* ── Activity row ──────────────────────────────────────────── */
function ActivityRow({ event, unit }: { event: ActivityEvent; unit: string }) {
  return (
    <Card style={{ margin: "0 20px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <div>
          <div className="pb-mono" style={{ fontWeight: 700, fontSize: 13 }}>{event.anonHandle}</div>
          <div className="pb-mono" style={{ color: "var(--pb-text-sec)", fontSize: 12 }}>
            μ={compactDecimal(event.mu, 2)} {unit} · σ={compactDecimal(event.sigma, 2)}
          </div>
        </div>
        <div style={{ textAlign: "right" }}>
          <div className="pb-mono" style={{ color: "var(--pb-long)", fontWeight: 700 }}>
            ${compactDecimal(event.stake, 0)}
          </div>
          <div style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>{event.ageMinutes}m ago</div>
        </div>
      </div>
    </Card>
  );
}

/* ── Quick takes ───────────────────────────────────────────── */
type QuickTake = { title: string; sub: string; mu: number; sigma: number };

function QuickTakes({ market }: { market: MarketListing }) {
  const setShowBetSheet = useParabolaStore((s) => s.setShowBetSheet);
  const setLastSubmit   = useParabolaStore((s) => s.setLastSubmit);

  const takes: QuickTake[] = [
    { title: "Above crowd", sub: "+0.6σ", mu: market.crowdMu + market.crowdSigma * 0.6, sigma: market.crowdSigma },
    { title: "Below crowd", sub: "−0.6σ", mu: market.crowdMu - market.crowdSigma * 0.6, sigma: market.crowdSigma },
    { title: "Sharper",     sub: "0.5×σ", mu: market.crowdMu, sigma: market.crowdSigma * 0.5 },
    { title: "Wider",       sub: "1.6×σ", mu: market.crowdMu, sigma: market.crowdSigma * 1.6 },
  ];

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
      {takes.map((t) => (
        <button
          key={t.title}
          type="button"
          onClick={() => {
            BetPrefill.muOverride    = t.mu;
            BetPrefill.sigmaOverride = t.sigma;
            setLastSubmit(null);
            setShowBetSheet(true);
          }}
          style={{
            textAlign: "left", padding: "14px", borderRadius: 14,
            border: "1px solid var(--pb-border-strong)",
            background: "var(--pb-surface-el)", cursor: "pointer", color: "var(--pb-text)",
          }}
        >
          <div style={{ fontWeight: 700, fontSize: 14 }}>{t.title}</div>
          <div className="pb-mono" style={{ color: "var(--pb-you)", fontSize: 12, marginTop: 2 }}>{t.sub}</div>
        </button>
      ))}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   EstimationDetail — main component
═══════════════════════════════════════════════════════════ */
export function EstimationDetail({ market }: { market: MarketListing }) {
  const closeMarket    = useParabolaStore((s) => s.closeMarket);
  const bets           = useParabolaStore((s) => s.bets);
  const setShowBetSheet = useParabolaStore((s) => s.setShowBetSheet);

  const [activeTab, setActiveTab]   = useState<DetailTab>("Bet");
  const [previewMu, setPreviewMu]   = useState(market.crowdMu);
  const [previewSig, setPreviewSig] = useState(market.crowdSigma);
  const [showAdv, setShowAdv]       = useState(false);

  const yourBets  = bets.filter((b) => b.marketId === market.id);
  const yourLatest = yourBets[0];
  const activity  = useMemo(() => mockActivityForMarket(market), [market]);
  const catMeta   = CATEGORY_META[market.category];

  return (
    <div className="pb-scroll" style={{ background: "var(--pb-bg)", minHeight: "100vh" }}>
      <BackBar onBack={closeMarket} unitUpper={market.unit.toUpperCase()} />

      {/* ── Hero section ── */}
      <div style={{ padding: "18px 20px 0" }}>
        {/* Badges */}
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center", marginBottom: 10 }}>
          {market.isOnChain && <TagPill colorVar="var(--pb-chain)" filled>ON-CHAIN</TagPill>}
          {market.sourceBadge && <TagPill colorVar="var(--pb-chain)">{market.sourceBadge}</TagPill>}
          <TagPill colorVar="var(--pb-crowd)">{catMeta.label.toUpperCase()}</TagPill>
          <span style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>· resolves {market.resolvesAt}</span>
        </div>

        <h1 style={{ margin: "0 0 6px", fontSize: 22, fontWeight: 700, letterSpacing: "-0.02em" }}>
          {market.title}
        </h1>
        <p style={{ margin: "0 0 18px", color: "var(--pb-text-sec)", fontSize: 14, lineHeight: 1.5 }}>
          {market.subtitle}
        </p>

        {/* μ and σ hero numbers */}
        <div style={{ display: "flex", gap: 20, alignItems: "flex-end", marginBottom: 14 }}>
          <HeroMetric label="CROWD MEAN μ" value={compactDecimal(market.crowdMu, 2)} unit={market.unit} color="var(--pb-crowd)" />
          <HeroMetric label="UNCERTAINTY σ" value={`±${compactDecimal(market.crowdSigma, 2)}`} unit={market.unit} color="var(--pb-text-sec)" />
          <div style={{ flex: 1 }} />
          <MiniDistCurve mu={market.crowdMu} sigma={market.crowdSigma} muMin={market.muMin} muMax={market.muMax} color="var(--pb-crowd)" width={72} height={38} />
        </div>

        {/* Distribution bar */}
        <DistBar mu={market.crowdMu} sigma={market.crowdSigma} muMin={market.muMin} muMax={market.muMax} color="var(--pb-crowd)" height={6} />
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 5, marginBottom: 20 }}>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>
            {compactDecimal(market.muMin, 2)} {market.unit}
          </span>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-crowd)", opacity: 0.7 }}>±1σ range</span>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>
            {compactDecimal(market.muMax, 2)} {market.unit}
          </span>
        </div>
      </div>

      {/* Live source card */}
      {market.liveEventStats && (
        <div style={{ marginBottom: 16 }}>
          <LiveSourceCard market={market} />
        </div>
      )}

      {/* Distribution chart + legend */}
      <div style={{ padding: "0 20px 6px" }}>
        <DistributionChart
          crowdMu={market.crowdMu} crowdSigma={market.crowdSigma}
          yourMu={previewMu} yourSigma={previewSig}
          realizedOutcome={yourLatest?.realizedOutcome}
        />
        <div style={{ display: "flex", flexWrap: "wrap", gap: "8px 14px", alignItems: "center", marginTop: 10 }}>
          {[
            { label: "Crowd belief", color: "var(--pb-crowd)" },
            { label: "Your curve",   color: "var(--pb-you)" },
            { label: "Profit zone",  color: "var(--pb-long)" },
            { label: "Loss zone",    color: "var(--pb-short)" },
            { label: "Realized",     color: "var(--pb-warn)" },
          ].map(({ label, color }) => (
            <span key={label} style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
              <span style={{ width: 8, height: 8, borderRadius: 99, background: color, flexShrink: 0 }} />
              <span style={{ color: "var(--pb-text-sec)", fontSize: 11 }}>{label}</span>
            </span>
          ))}
          <span style={{ flex: 1 }} />
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>x = {market.unit}</span>
        </div>
      </div>

      {/* Slider card */}
      <div style={{ padding: "14px 0" }}>
        <SliderCard
          market={market} mu={previewMu} sigma={previewSig}
          onMu={setPreviewMu} onSigma={setPreviewSig}
          onResetToCrowd={() => { setPreviewMu(market.crowdMu); setPreviewSig(market.crowdSigma); }}
          onOpenAdvanced={() => setShowAdv(true)}
        />
      </div>

      {/* Metric pills */}
      <div style={{ display: "flex", gap: 8, padding: "0 20px 16px", flexWrap: "wrap" }}>
        <MetricPill label="CROWD GUESS" value={`${compactDecimal(market.crowdMu, 2)} ${market.unit}`} accent="var(--pb-crowd)" />
        <MetricPill label="± RANGE"    value={compactDecimal(market.crowdSigma, 2)} />
        <MetricPill label="BETTORS"    value={String(market.bettorCount)} />
        <MetricPill label="VOLUME"     value={`$${formatVolume(market.volumeUsd)}`} />
      </div>

      {/* Tabs */}
      <TabBar active={activeTab} onSelect={setActiveTab} />

      <div style={{ paddingBottom: 120 }}>
        {/* ── Bet tab ── */}
        {activeTab === "Bet" && (
          <div style={{ display: "flex", flexDirection: "column", gap: 12, paddingTop: 14 }}>
            <Card style={{ margin: "0 20px" }}>
              <div style={{ fontWeight: 700, marginBottom: 6 }}>Quick takes</div>
              <p style={{ color: "var(--pb-text-sec)", fontSize: 12, margin: "0 0 12px" }}>
                Tap a stance — it opens the bet sheet with those parameters pre-filled.
              </p>
              <QuickTakes market={market} />
            </Card>

            {yourLatest && (
              <Card style={{ margin: "0 20px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span style={{ fontWeight: 700 }}>Your last bet</span>
                  {yourLatest.resolved && yourLatest.realizedPnl != null ? (
                    <span className="pb-mono" style={{ color: yourLatest.realizedPnl >= 0 ? "var(--pb-long)" : "var(--pb-short)", fontWeight: 700 }}>
                      {yourLatest.realizedPnl >= 0 ? "+" : ""}${compactDecimal(yourLatest.realizedPnl, 2)}
                    </span>
                  ) : (
                    <TagPill colorVar="var(--pb-long)">OPEN</TagPill>
                  )}
                </div>
                <CompactDivider />
                <StatRow label="Your μ"       value={`${compactDecimal(yourLatest.mu, 3)} ${market.unit}`} accent="var(--pb-you)" />
                <StatRow label="Your σ"       value={compactDecimal(yourLatest.sigma, 3)} />
                <StatRow label="Stake"        value={`$${compactDecimal(yourLatest.stake, 2)}`} />
                <StatRow label="Collateral"   value={compactDecimal(yourLatest.collateral, 4)} />
                <StatRow label="Fee"          value={compactDecimal(yourLatest.fee, 4)} />
                {yourLatest.txSignatureHex && (
                  <StatRow label="Tx" value={shortHash(yourLatest.txSignatureHex, 6, 6)} accent="var(--pb-chain)" />
                )}
              </Card>
            )}
          </div>
        )}

        {/* ── Stats tab ── */}
        {activeTab === "Stats" && (
          <div style={{ paddingTop: 14 }}>
            <Card style={{ margin: "0 20px" }}>
              <div style={{ fontWeight: 700, marginBottom: 10 }}>Crowd μ over time</div>
              <Sparkline values={market.crowdHistory} height={80} width={340} color="var(--pb-crowd)" />
              <CompactDivider style={{ marginTop: 14 }} />
              <StatRow label="Total volume" value={`$${formatVolume(market.volumeUsd)}`} />
              <StatRow label="Bettors"      value={String(market.bettorCount)} />
              <StatRow label="μ range"      value={`${compactDecimal(market.muMin, 2)} – ${compactDecimal(market.muMax, 2)} ${market.unit}`} />
              <StatRow label="σ range"      value={`${compactDecimal(market.sigmaMin, 2)} – ${compactDecimal(market.sigmaMax, 2)}`} />
            </Card>
          </div>
        )}

        {/* ── Flow tab ── */}
        {activeTab === "Flow" && (
          <div style={{ display: "flex", flexDirection: "column", gap: 10, paddingTop: 14 }}>
            {activity.map((ev) => (
              <ActivityRow key={`${ev.anonHandle}-${ev.ageMinutes}`} event={ev} unit={market.unit} />
            ))}
          </div>
        )}

        {/* ── Rules tab ── */}
        {activeTab === "Rules" && (
          <div style={{ paddingTop: 14 }}>
            <Card style={{ margin: "0 20px" }}>
              <SectionLabel>Resolution source</SectionLabel>
              <p style={{ color: "var(--pb-text)", marginTop: 6 }}>{market.resolutionSource}</p>
              <SectionLabel>Rule</SectionLabel>
              <p style={{ color: "var(--pb-text-sec)", marginTop: 6 }}>{market.resolutionRule}</p>
              <SectionLabel>Resolves at</SectionLabel>
              <p className="pb-mono" style={{ marginTop: 6 }}>{market.resolvesAt}</p>
            </Card>
          </div>
        )}
      </div>

      {/* Fixed CTA */}
      <div style={{
        position: "fixed", bottom: 0, left: 0, right: 0,
        padding: "16px 20px calc(16px + env(safe-area-inset-bottom))",
        background: "linear-gradient(transparent, var(--pb-bg) 40%)", zIndex: 30,
      }}>
        <PrimaryButton
          label={market.isOnChain ? "Place bet · sign on devnet" : "Place bet"}
          accent="linear-gradient(135deg, var(--pb-you) 0%, #80e830 100%)"
          onClick={() => {
            BetPrefill.muOverride    = previewMu;
            BetPrefill.sigmaOverride = previewSig;
            setShowBetSheet(true);
          }}
        />
      </div>

      {showAdv && (
        <AdvancedDialog
          market={market} sigma={previewSig}
          onSigma={setPreviewSig} onDismiss={() => setShowAdv(false)}
        />
      )}
    </div>
  );
}
