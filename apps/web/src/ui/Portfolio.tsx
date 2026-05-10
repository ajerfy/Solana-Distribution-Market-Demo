import { useMemo } from "react";
import type { BetRecord, MarketListing } from "../domain/types";
import { compactDecimal, shortHash } from "../domain/format";
import { buildMarketListings } from "../domain/mockMarkets";
import { simulateRealizedAndPnl } from "../domain/betResolve";
import { useParabolaStore } from "../state/parabolaStore";
import { DistributionChart } from "./DistributionChart";
import {
  Card,
  CompactDivider,
  GhostButton,
  MetricPill,
  PrimaryButton,
  PnlText,
  TagPill,
} from "./shared";

function BetCard({ bet, market }: { bet: BetRecord; market: MarketListing | undefined }) {
  const resolveBet = useParabolaStore((s) => s.resolveBet);

  return (
    <Card style={{ margin: "0 20px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 6 }}>
            {bet.isOnChain ? (
              <TagPill colorVar="var(--pb-chain)" filled>
                ON-CHAIN
              </TagPill>
            ) : null}
            {bet.resolved ? (
              <TagPill colorVar="var(--pb-text-dim)">DONE</TagPill>
            ) : (
              <TagPill colorVar="var(--pb-long)">OPEN</TagPill>
            )}
          </div>
          <div style={{ fontWeight: 700, fontSize: 15 }}>{bet.marketTitle}</div>
        </div>
        {bet.resolved && bet.realizedPnl != null ? (
          <PnlText value={bet.realizedPnl} prefix="$" />
        ) : (
          <span className="pb-mono" style={{ fontWeight: 800, color: "var(--pb-you)", fontSize: 18 }}>
            ${compactDecimal(bet.stake, 0)}
          </span>
        )}
      </div>

      {market ? (
        <>
          <div style={{ marginTop: 12 }}>
            <DistributionChart
              crowdMu={market.crowdMu}
              crowdSigma={market.crowdSigma}
              yourMu={bet.mu}
              yourSigma={bet.sigma}
              realizedOutcome={bet.realizedOutcome}
              height={130}
            />
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
            <MetricPill
              label="YOUR GUESS"
              value={`${compactDecimal(bet.mu, 2)}${market.unit ? ` ${market.unit}` : ""}`}
              accent="var(--pb-you)"
            />
            <MetricPill label="± RANGE" value={compactDecimal(bet.sigma, 2)} />
            <MetricPill label="STAKE" value={`$${compactDecimal(bet.stake, 0)}`} />
            {bet.resolved && bet.realizedOutcome != null ? (
              <MetricPill label="REALIZED" value={compactDecimal(bet.realizedOutcome, 2)} accent="var(--pb-warn)" />
            ) : null}
          </div>
        </>
      ) : null}

      {!bet.resolved && market ? (
        <>
          <CompactDivider style={{ marginTop: 14 }} />
          <PrimaryButton
            label="Resolve · draw outcome"
            accent="var(--pb-warn)"
            onClick={() => {
              const [realized, pnl] = simulateRealizedAndPnl(market, bet);
              resolveBet(bet, realized, pnl);
            }}
          />
        </>
      ) : null}

      {bet.txSignatureHex ? (
        <p className="pb-mono" style={{ color: "var(--pb-chain)", fontSize: 11, marginTop: 10 }}>
          tx {shortHash(bet.txSignatureHex, 8, 8)}
        </p>
      ) : null}
    </Card>
  );
}

export function Portfolio() {
  const bets = useParabolaStore((s) => s.bets);
  const payload = useParabolaStore((s) => s.payload);
  const clearAllBets = useParabolaStore((s) => s.clearAllBets);
  const allMarkets = useMemo(() => (payload ? buildMarketListings(payload) : []), [payload]);

  const totalStaked = bets.reduce((a, b) => a + b.stake, 0);
  const realizedPnl = bets.filter((b) => b.resolved).reduce((a, b) => a + (b.realizedPnl ?? 0), 0);
  const open = bets.filter((b) => !b.resolved);
  const resolved = bets.filter((b) => b.resolved);

  if (bets.length === 0) {
    return (
      <div
        className="pb-scroll"
        style={{
          padding: "48px 24px",
          textAlign: "center",
          maxWidth: 420,
          margin: "0 auto",
        }}
      >
        <h2 style={{ marginTop: 0 }}>No bets yet</h2>
        <p style={{ color: "var(--pb-text-sec)" }}>
          Pick a market, set your guess, place a bet.
        </p>
      </div>
    );
  }

  return (
    <div className="pb-scroll">
      <header style={{ padding: "22px 20px 12px" }}>
        <h1 style={{ margin: 0, fontSize: 28, fontWeight: 600 }}>Portfolio</h1>
        <p style={{ color: "var(--pb-text-sec)", marginTop: 6 }}>
          {bets.length} positions across {new Set(bets.map((b) => b.marketId)).size} markets
        </p>
      </header>

      <div style={{ display: "flex", gap: 8, padding: "0 20px 16px", flexWrap: "wrap" }}>
        <MetricPill label="STAKED" value={`$${compactDecimal(totalStaked, 0)}`} />
        <MetricPill
          label="RESOLVED P/L"
          value={`${realizedPnl >= 0 ? "+" : ""}$${compactDecimal(realizedPnl, 2)}`}
          accent={realizedPnl >= 0 ? "var(--pb-long)" : "var(--pb-short)"}
        />
        <MetricPill label="OPEN" value={String(open.length)} />
        <MetricPill label="DONE" value={String(resolved.length)} />
      </div>

      {open.length > 0 ? (
        <>
          <div className="pb-section-label" style={{ padding: "0 20px", marginBottom: 8 }}>
            OPEN POSITIONS
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {open.map((b) => (
              <BetCard key={b.id} bet={b} market={allMarkets.find((m) => m.id === b.marketId)} />
            ))}
          </div>
        </>
      ) : null}

      {resolved.length > 0 ? (
        <>
          <div className="pb-section-label" style={{ padding: "16px 20px 8px" }}>
            RESOLVED
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {resolved.map((b) => (
              <BetCard key={b.id} bet={b} market={allMarkets.find((m) => m.id === b.marketId)} />
            ))}
          </div>
        </>
      ) : null}

      <div style={{ padding: "12px 20px 24px" }}>
        <GhostButton label="Clear all bets" onClick={() => clearAllBets()} />
      </div>
    </div>
  );
}
