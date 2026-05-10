import { useMemo, useState } from "react";
import type { ActivityEvent, MarketListing } from "../domain/types";
import { CATEGORY_META } from "../domain/types";
import { BetPrefill } from "../domain/betPrefill";
import { mockActivityForMarket } from "../domain/mockMarkets";
import {
  compactDecimal,
  formatVolume,
  shortHash,
} from "../domain/format";
import { useParabolaStore } from "../state/parabolaStore";
import { DistributionChart } from "./DistributionChart";
import { Sparkline } from "./Sparkline";
import {
  BackBar,
  Card,
  CompactDivider,
  GhostButton,
  MetricPill,
  PrimaryButton,
  SectionLabel,
  StatRow,
  TagPill,
} from "./shared";

type DetailTab = "Bet" | "Stats" | "Flow" | "Rules";

function TabBar({
  active,
  onSelect,
}: {
  active: DetailTab;
  onSelect: (t: DetailTab) => void;
}) {
  const tabs: DetailTab[] = ["Bet", "Stats", "Flow", "Rules"];
  return (
    <div style={{ display: "flex", padding: "0 20px", gap: 0 }}>
      {tabs.map((tab) => {
        const selected = tab === active;
        return (
          <button
            key={tab}
            type="button"
            onClick={() => onSelect(tab)}
            style={{
              flex: 1,
              padding: "10px 4px",
              border: "none",
              background: "transparent",
              cursor: "pointer",
              color: selected ? "var(--pb-text)" : "var(--pb-text-sec)",
              fontWeight: selected ? 700 : 500,
              fontSize: 14,
            }}
          >
            <div>{tab}</div>
            <div
              style={{
                marginTop: 6,
                height: 2,
                borderRadius: 2,
                background: selected ? "var(--pb-you)" : "transparent",
              }}
            />
          </button>
        );
      })}
    </div>
  );
}

function LiveEstimateSourceCard({ market }: { market: MarketListing }) {
  const stats = market.liveEventStats;
  if (!stats) return null;
  const cents = (p: number | null) =>
    p != null ? `${(p * 100).toFixed(1)}¢` : "—";
  return (
    <Card style={{ margin: "0 20px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ fontWeight: 700, fontSize: 16 }}>Live source</span>
        <span style={{ flex: 1 }} />
        {market.sourceBadge ? (
          <TagPill colorVar="var(--pb-chain)">{market.sourceBadge}</TagPill>
        ) : null}
      </div>
      <p style={{ color: "var(--pb-text-sec)", fontSize: 13, marginTop: 8 }}>
        This estimate is pinned to a live binary market and remapped onto a
        probability curve so you can trade conviction and uncertainty together.
      </p>
      <CompactDivider />
      <StatRow label="Outcome" value={stats.outcomeLabel} strong />
      <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
        <MetricPill label="YES" value={cents(stats.yesPrice)} />
        <MetricPill label="NO" value={cents(stats.noPrice)} />
      </div>
      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
        <MetricPill label="BID" value={cents(stats.bestBid)} />
        <MetricPill label="ASK" value={cents(stats.bestAsk)} />
        <MetricPill label="SPREAD" value={cents(stats.spread)} />
      </div>
      {stats.updatedAtMillis != null ? (
        <p className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11, marginTop: 10 }}>
          Updated{" "}
          {new Date(stats.updatedAtMillis).toLocaleString(undefined, {
            dateStyle: "short",
            timeStyle: "short",
          })}
        </p>
      ) : null}
    </Card>
  );
}

function LegendRow({ unit }: { unit: string }) {
  const dot = (label: string, color: string) => (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
      <span
        style={{
          width: 8,
          height: 8,
          borderRadius: 99,
          background: color,
        }}
      />
      <span style={{ color: "var(--pb-text-sec)", fontSize: 11 }}>{label}</span>
    </span>
  );
  return (
    <div
      style={{
        display: "flex",
        flexWrap: "wrap",
        gap: "10px 14px",
        alignItems: "center",
      }}
    >
      {dot("Crowd belief", "var(--pb-crowd)")}
      {dot("Your curve", "var(--pb-you)")}
      {dot("Make money", "var(--pb-long)")}
      {dot("Lose money", "var(--pb-short)")}
      {dot("Realized", "var(--pb-warn)")}
      <span style={{ flex: 1 }} />
      <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
        x = {unit}
      </span>
    </div>
  );
}

function DistributionSliderCard({
  market,
  mu,
  sigma,
  onMu,
  onSigma,
  onResetToCrowd,
  onOpenAdvanced,
}: {
  market: MarketListing;
  mu: number;
  sigma: number;
  onMu: (v: number) => void;
  onSigma: (v: number) => void;
  onResetToCrowd: () => void;
  onOpenAdvanced: () => void;
}) {
  const sigmaMin = market.sigmaMin;
  const sigmaMax = market.sigmaMax;
  const span = Math.max(sigmaMax - sigmaMin, 1e-4);
  const confidence = Math.min(
    1,
    Math.max(0, 1 - (sigma - sigmaMin) / span),
  );

  return (
    <Card style={{ margin: "0 20px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ fontWeight: 700 }}>Tune your bet</span>
        <span style={{ flex: 1 }} />
        <button
          type="button"
          style={{
            border: "none",
            background: "none",
            color: "var(--pb-text-dim)",
            cursor: "pointer",
            fontSize: 11,
          }}
          onClick={onResetToCrowd}
        >
          reset
        </button>
        <button
          type="button"
          style={{
            border: "none",
            background: "none",
            color: "var(--pb-crowd)",
            cursor: "pointer",
            fontSize: 11,
            fontWeight: 700,
          }}
          onClick={onOpenAdvanced}
        >
          advanced
        </button>
      </div>
      <p style={{ color: "var(--pb-text-sec)", fontSize: 12, marginTop: 4 }}>
        Slide your average and pick how confident you are.
      </p>

      <label style={{ display: "block", marginTop: 12, fontSize: 13 }}>
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <span style={{ color: "var(--pb-text-sec)" }}>Average</span>
          <span className="pb-mono" style={{ color: "var(--pb-you)", fontWeight: 700 }}>
            {compactDecimal(mu, 3)} {market.unit}
          </span>
        </div>
        <input
          type="range"
          min={market.muMin}
          max={market.muMax}
          step={0.001}
          value={mu}
          onChange={(e) => onMu(Number(e.target.value))}
          style={{ width: "100%", accentColor: "var(--pb-you)" }}
        />
        <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
          crowd&apos;s {compactDecimal(market.crowdMu, 2)} {market.unit}
        </span>
      </label>

      <label style={{ display: "block", marginTop: 14, fontSize: 13 }}>
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <span style={{ color: "var(--pb-text-sec)" }}>Confidence</span>
          <span className="pb-mono" style={{ color: "var(--pb-crowd)", fontWeight: 700 }}>
            {(confidence * 100).toFixed(0)}%
          </span>
        </div>
        <input
          type="range"
          min={0}
          max={1}
          step={0.001}
          value={confidence}
          onChange={(e) => {
            const c = Number(e.target.value);
            onSigma(sigmaMin + (1 - c) * span);
          }}
          style={{ width: "100%", accentColor: "var(--pb-crowd)" }}
        />
        <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
          crowd&apos;s{" "}
          {(
            (1 - (market.crowdSigma - sigmaMin) / span) *
            100
          ).toFixed(0)}
          % confident
        </span>
      </label>

      <GhostButton label={`Advanced selection · σ ${compactDecimal(sigma, 3)}`} onClick={onOpenAdvanced} />
    </Card>
  );
}

function AdvancedSigmaDialog({
  market,
  sigma,
  onSigma,
  onDismiss,
}: {
  market: MarketListing;
  sigma: number;
  onSigma: (v: number) => void;
  onDismiss: () => void;
}) {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.55)",
        zIndex: 60,
        display: "flex",
        alignItems: "flex-end",
        justifyContent: "center",
        padding: 12,
      }}
      onClick={onDismiss}
      onKeyDown={(e) => e.key === "Escape" && onDismiss()}
      role="presentation"
    >
      <div className="pb-card" style={{ width: "100%", maxWidth: 520 }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 17 }}>Advanced selection</div>
            <div style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>
              Raw standard deviation control
            </div>
          </div>
          <button type="button" style={{ border: "none", background: "none", color: "var(--pb-crowd)", fontWeight: 700, cursor: "pointer" }} onClick={onDismiss}>
            Done
          </button>
        </div>
        <p style={{ color: "var(--pb-text-sec)", fontSize: 13, marginTop: 10 }}>
          Moving σ here updates the chart behind this popup. Lower σ means a tighter, more confident curve.
        </p>
        <label style={{ display: "block", marginTop: 14 }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <span>Standard deviation</span>
            <span className="pb-mono" style={{ color: "var(--pb-long)" }}>
              {compactDecimal(sigma, 3)}
            </span>
          </div>
          <input
            type="range"
            min={market.sigmaMin}
            max={market.sigmaMax}
            step={0.001}
            value={sigma}
            onChange={(e) => onSigma(Number(e.target.value))}
            style={{ width: "100%", accentColor: "var(--pb-long)" }}
          />
        </label>
        <CompactDivider />
        <StatRow label="Crowd σ" value={compactDecimal(market.crowdSigma, 3)} accent="var(--pb-crowd)" />
        <StatRow label="Your σ" value={compactDecimal(sigma, 3)} accent="var(--pb-long)" strong />
        <PrimaryButton label="Done" onClick={onDismiss} />
      </div>
    </div>
  );
}

function ActivityRow({
  event,
  unit,
}: {
  event: ActivityEvent;
  unit: string;
}) {
  return (
    <Card style={{ margin: "0 20px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <div>
          <div className="pb-mono" style={{ fontWeight: 700, fontSize: 13 }}>
            {event.anonHandle}
          </div>
          <div className="pb-mono" style={{ color: "var(--pb-text-sec)", fontSize: 12 }}>
            μ={compactDecimal(event.mu, 2)} {unit} · σ={compactDecimal(event.sigma, 2)}
          </div>
        </div>
        <div style={{ textAlign: "right" }}>
          <div style={{ color: "var(--pb-long)", fontWeight: 700 }} className="pb-mono">
            ${compactDecimal(event.stake, 0)}
          </div>
          <div style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>{event.ageMinutes}m ago</div>
        </div>
      </div>
    </Card>
  );
}

type QuickTake = { title: string; sub: string; mu: number; sigma: number };

function QuickTakes({ market }: { market: MarketListing }) {
  const setShowBetSheet = useParabolaStore((s) => s.setShowBetSheet);
  const setLastSubmit = useParabolaStore((s) => s.setLastSubmit);
  const takes: QuickTake[] = [
    {
      title: "Above crowd",
      sub: "+0.6σ",
      mu: market.crowdMu + market.crowdSigma * 0.6,
      sigma: market.crowdSigma,
    },
    {
      title: "Below crowd",
      sub: "−0.6σ",
      mu: market.crowdMu - market.crowdSigma * 0.6,
      sigma: market.crowdSigma,
    },
    {
      title: "Sharper",
      sub: "0.5×σ",
      mu: market.crowdMu,
      sigma: market.crowdSigma * 0.5,
    },
    {
      title: "Wider",
      sub: "1.6×σ",
      mu: market.crowdMu,
      sigma: market.crowdSigma * 1.6,
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      {chunkPairs(takes).map((row, ri) => (
        <div key={ri} style={{ display: "flex", gap: 8 }}>
          {row.map((t) => (
            <button
              key={t.title}
              type="button"
              onClick={() => {
                BetPrefill.muOverride = t.mu;
                BetPrefill.sigmaOverride = t.sigma;
                setLastSubmit(null);
                setShowBetSheet(true);
              }}
              style={{
                flex: 1,
                textAlign: "left",
                padding: 14,
                borderRadius: 12,
                border: "none",
                background: "var(--pb-surface-el)",
                cursor: "pointer",
                color: "var(--pb-text)",
              }}
            >
              <div style={{ fontWeight: 700 }}>{t.title}</div>
              <div className="pb-mono" style={{ color: "var(--pb-you)", fontSize: 13 }}>
                {t.sub}
              </div>
            </button>
          ))}
          {row.length === 1 ? <div style={{ flex: 1 }} /> : null}
        </div>
      ))}
    </div>
  );
}

function chunkPairs<T>(arr: T[]): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < arr.length; i += 2) {
    out.push(arr.slice(i, i + 2));
  }
  return out;
}

export function EstimationDetail({ market }: { market: MarketListing }) {
  const closeMarket = useParabolaStore((s) => s.closeMarket);
  const bets = useParabolaStore((s) => s.bets);
  const setShowBetSheet = useParabolaStore((s) => s.setShowBetSheet);

  const [activeTab, setActiveTab] = useState<DetailTab>("Bet");
  const [previewMu, setPreviewMu] = useState(market.crowdMu);
  const [previewSigma, setPreviewSigma] = useState(market.crowdSigma);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const yourBets = bets.filter((b) => b.marketId === market.id);
  const yourLatest = yourBets[0];
  const activity = useMemo(() => mockActivityForMarket(market), [market]);

  const catMeta = CATEGORY_META[market.category];

  return (
    <div className="pb-scroll" style={{ background: "var(--pb-bg)", minHeight: "100vh" }}>
      <BackBar onBack={closeMarket} unitUpper={market.unit.toUpperCase()} />

      <div style={{ padding: "0 20px 14px", display: "flex", flexDirection: "column", gap: 8 }}>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center" }}>
          {market.isOnChain ? (
            <TagPill colorVar="var(--pb-chain)" filled>
              ON-CHAIN
            </TagPill>
          ) : null}
          {market.sourceBadge ? (
            <TagPill colorVar="var(--pb-chain)">{market.sourceBadge}</TagPill>
          ) : null}
          <TagPill colorVar="var(--pb-crowd)">{catMeta.label.toUpperCase()}</TagPill>
          <span style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
            · resolves {market.resolvesAt}
          </span>
        </div>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 600 }}>{market.title}</h1>
        <p style={{ margin: 0, color: "var(--pb-text-sec)", fontSize: 15 }}>
          {market.subtitle}
        </p>
      </div>

      {market.liveEventStats ? (
        <div style={{ marginBottom: 14 }}>
          <LiveEstimateSourceCard market={market} />
        </div>
      ) : null}

      <div style={{ padding: "0 20px 14px", display: "flex", flexDirection: "column", gap: 10 }}>
        <DistributionChart
          crowdMu={market.crowdMu}
          crowdSigma={market.crowdSigma}
          yourMu={previewMu}
          yourSigma={previewSigma}
          realizedOutcome={yourLatest?.realizedOutcome}
        />
        <LegendRow unit={market.unit} />
      </div>

      <div style={{ marginBottom: 14 }}>
        <DistributionSliderCard
          market={market}
          mu={previewMu}
          sigma={previewSigma}
          onMu={setPreviewMu}
          onSigma={setPreviewSigma}
          onResetToCrowd={() => {
            setPreviewMu(market.crowdMu);
            setPreviewSigma(market.crowdSigma);
          }}
          onOpenAdvanced={() => setShowAdvanced(true)}
        />
      </div>

      <div style={{ display: "flex", gap: 8, padding: "0 20px", flexWrap: "wrap" }}>
        <MetricPill
          label="CROWD GUESS"
          value={`${compactDecimal(market.crowdMu, 2)} ${market.unit}`}
          accent="var(--pb-crowd)"
        />
        <MetricPill label="± RANGE" value={compactDecimal(market.crowdSigma, 2)} />
        <MetricPill label="BETTORS" value={String(market.bettorCount)} />
        <MetricPill label="VOL" value={formatVolume(market.volumeUsd)} />
      </div>

      <div style={{ marginTop: 14 }}>
        <TabBar active={activeTab} onSelect={setActiveTab} />
      </div>

      <div style={{ marginTop: 14, paddingBottom: 120 }}>
        {activeTab === "Bet" ? (
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <Card style={{ margin: "0 20px" }}>
              <div style={{ fontWeight: 700, marginBottom: 8 }}>Quick takes</div>
              <p style={{ color: "var(--pb-text-sec)", fontSize: 12, marginTop: 0 }}>
                Tap a stance, then tune in the bet sheet.
              </p>
              <QuickTakes market={market} />
            </Card>
            {yourLatest ? (
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
                <StatRow label="Your guess" value={`${compactDecimal(yourLatest.mu, 3)} ${market.unit}`} accent="var(--pb-you)" />
                <StatRow label="How sure" value={compactDecimal(yourLatest.sigma, 3)} />
                <StatRow label="Stake" value={`$${compactDecimal(yourLatest.stake, 2)}`} />
                <StatRow label="Stake locked" value={compactDecimal(yourLatest.collateral, 4)} />
                <StatRow label="Fee" value={compactDecimal(yourLatest.fee, 4)} />
                {yourLatest.txSignatureHex ? (
                  <StatRow label="Tx" value={shortHash(yourLatest.txSignatureHex, 6, 6)} accent="var(--pb-chain)" />
                ) : null}
              </Card>
            ) : null}
          </div>
        ) : null}

        {activeTab === "Stats" ? (
          <Card style={{ margin: "0 20px" }}>
            <div style={{ fontWeight: 700, marginBottom: 10 }}>Crowd μ over time</div>
            <Sparkline values={market.crowdHistory} height={80} width={340} />
            <CompactDivider style={{ marginTop: 14 }} />
            <StatRow label="Total volume" value={formatVolume(market.volumeUsd)} />
            <StatRow label="Bettors" value={String(market.bettorCount)} />
            <StatRow
              label="μ range"
              value={`${compactDecimal(market.muMin, 2)} – ${compactDecimal(market.muMax, 2)} ${market.unit}`}
            />
            <StatRow
              label="σ range"
              value={`${compactDecimal(market.sigmaMin, 2)} – ${compactDecimal(market.sigmaMax, 2)}`}
            />
          </Card>
        ) : null}

        {activeTab === "Flow"
          ? activity.map((ev) => (
              <ActivityRow key={`${ev.anonHandle}-${ev.ageMinutes}`} event={ev} unit={market.unit} />
            ))
          : null}

        {activeTab === "Rules" ? (
          <Card style={{ margin: "0 20px" }}>
            <SectionLabel>Resolution source</SectionLabel>
            <p style={{ color: "var(--pb-text)", marginTop: 6 }}>{market.resolutionSource}</p>
            <SectionLabel>Rule</SectionLabel>
            <p style={{ color: "var(--pb-text-sec)", marginTop: 6 }}>{market.resolutionRule}</p>
            <SectionLabel>Resolves at</SectionLabel>
            <p className="pb-mono" style={{ marginTop: 6 }}>
              {market.resolvesAt}
            </p>
          </Card>
        ) : null}
      </div>

      <div
        style={{
          position: "fixed",
          bottom: 0,
          left: 0,
          right: 0,
          padding: "16px 20px calc(16px + env(safe-area-inset-bottom))",
          background: "linear-gradient(transparent, var(--pb-bg) 35%)",
          zIndex: 30,
        }}
      >
        <PrimaryButton
          label={
            market.isOnChain ? "Place bet · sign on devnet" : "Place bet"
          }
          onClick={() => {
            BetPrefill.muOverride = previewMu;
            BetPrefill.sigmaOverride = previewSigma;
            setShowBetSheet(true);
          }}
        />
      </div>

      {showAdvanced ? (
        <AdvancedSigmaDialog
          market={market}
          sigma={previewSigma}
          onSigma={setPreviewSigma}
          onDismiss={() => setShowAdvanced(false)}
        />
      ) : null}
    </div>
  );
}
