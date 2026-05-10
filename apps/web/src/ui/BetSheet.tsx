import { useState } from "react";
import type { MarketListing } from "../domain/types";
import {
  buildContinuousQuotePreview,
  estimateCollateralOffline,
} from "../domain/quoteMath";
import { compactDecimal } from "../domain/format";
import { BetPrefill } from "../domain/betPrefill";
import { useParabolaStore } from "../state/parabolaStore";
import { DistributionChart } from "./DistributionChart";
import {
  CompactDivider,
  PrimaryButton,
  SectionLabel,
  StatRow,
  StatusBlockSimple,
  TagPill,
} from "./shared";

const STAKE_OPTS = [10, 25, 50, 100, 250, 500];

function estimateCollateral(
  market: MarketListing,
  mu: number,
  sigma: number,
  quoteCollateral: number | null,
): number {
  return quoteCollateral ?? estimateCollateralOffline(market.crowdMu, market.crowdSigma, mu, sigma);
}

export function EstimationBetSheet({
  market,
  onDismiss,
}: {
  market: MarketListing;
  onDismiss: () => void;
}) {
  const payload = useParabolaStore((s) => s.payload);
  const addBet = useParabolaStore((s) => s.addBet);
  const setLastSubmit = useParabolaStore((s) => s.setLastSubmit);

  const [{ mu, sigma }, setCurve] = useState(() => {
    const [m, s] = BetPrefill.consume();
    return {
      mu: m ?? market.crowdMu,
      sigma: s ?? market.crowdSigma,
    };
  });
  const [stake, setStake] = useState(50);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [status, setStatus] = useState<
    { message: string; variant: "error" | "working" | "ok" } | null
  >(null);

  let quote: ReturnType<typeof buildContinuousQuotePreview> | null = null;
  try {
    if (market.isOnChain && payload) {
      quote = buildContinuousQuotePreview(payload.market, mu, sigma);
    }
  } catch {
    quote = null;
  }

  const collateral = estimateCollateral(market, mu, sigma, quote?.collateralRequired ?? null);
  const fee =
    quote?.feePaid ?? Math.max(stake * 0.003, 0.05);
  const maxWin = stake * 1.85;
  const maxLoss = stake;

  const sigmaMin = market.sigmaMin;
  const sigmaMax = market.sigmaMax;
  const span = Math.max(sigmaMax - sigmaMin, 1e-4);
  const confidence = Math.min(1, Math.max(0, 1 - (sigma - sigmaMin) / span));

  const placeLocalBet = () => {
    const record = {
      id: `bet-${Date.now()}`,
      marketId: market.id,
      marketTitle: market.title,
      mu,
      sigma,
      stake,
      collateral,
      fee,
      placedAtMillis: Date.now(),
      resolved: false,
      realizedOutcome: null,
      realizedPnl: null,
      txSignatureHex: null,
      isOnChain: false,
    };
    addBet(record);
    setLastSubmit({
      message: "Bet placed locally · resolve from Portfolio.",
      isError: false,
    });
    onDismiss();
  };

  const onPrimary = () => {
    if (market.isOnChain) {
      setStatus({
        message:
          "On-chain signing uses Solana Mobile Wallet Adapter on Android. On web, place a local demo bet from a non-chain market, or use the Android app to sign.",
        variant: "error",
      });
      return;
    }
    placeLocalBet();
  };

  return (
    <div className="pb-sheet-overlay" onClick={onDismiss} role="presentation">
      <div className="pb-sheet" onClick={(e) => e.stopPropagation()}>
        <div
          style={{
            width: 40,
            height: 4,
            borderRadius: 99,
            background: "var(--pb-border-strong)",
            margin: "8px auto 14px",
          }}
        />
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          {market.isOnChain ? (
            <TagPill colorVar="var(--pb-chain)" filled>
              ON-CHAIN
            </TagPill>
          ) : null}
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 12 }}>
            BET
          </span>
        </div>
        <h2 style={{ margin: "8px 0 14px", fontSize: 20 }}>{market.title}</h2>

        <DistributionChart
          crowdMu={market.crowdMu}
          crowdSigma={market.crowdSigma}
          yourMu={mu}
          yourSigma={sigma}
          height={180}
        />

        <div style={{ marginTop: 14 }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
            <div>
              <div className="pb-section-label">AVERAGE</div>
              <div className="pb-mono" style={{ color: "var(--pb-you)", fontWeight: 700, fontSize: 16 }}>
                {compactDecimal(mu, 3)} {market.unit}
              </div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div className="pb-section-label">CROWD AVERAGE</div>
              <div className="pb-mono" style={{ color: "var(--pb-crowd)", fontWeight: 500, fontSize: 15 }}>
                {compactDecimal(market.crowdMu, 2)} {market.unit}
              </div>
            </div>
          </div>
          <input
            type="range"
            min={market.muMin}
            max={market.muMax}
            step={0.001}
            value={mu}
            onChange={(e) => {
              setCurve({ mu: Number(e.target.value), sigma });
              setStatus(null);
            }}
            style={{ width: "100%", accentColor: "var(--pb-you)" }}
          />
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: 14, marginBottom: 6 }}>
            <div>
              <div className="pb-section-label">CONFIDENCE</div>
              <div className="pb-mono" style={{ color: "var(--pb-you)", fontWeight: 700, fontSize: 16 }}>
                {(confidence * 100).toFixed(0)}%
              </div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div className="pb-section-label">CROWD&apos;S</div>
              <div className="pb-mono" style={{ color: "var(--pb-crowd)", fontWeight: 500, fontSize: 15 }}>
                {(
                  (1 -
                    (market.crowdSigma - sigmaMin) / span) *
                  100
                ).toFixed(0)}%
              </div>
            </div>
          </div>
          <input
            type="range"
            min={0}
            max={1}
            step={0.001}
            value={confidence}
            onChange={(e) => {
              const c = Number(e.target.value);
              setCurve({
                mu,
                sigma: sigmaMin + (1 - c) * span,
              });
              setStatus(null);
            }}
            style={{ width: "100%", accentColor: "var(--pb-crowd)" }}
          />
        </div>

        <div style={{ textAlign: "right", marginTop: 6 }}>
          <button
            type="button"
            style={{
              border: "none",
              background: "none",
              color: showAdvanced ? "var(--pb-long)" : "var(--pb-crowd)",
              fontWeight: 700,
              fontSize: 11,
              cursor: "pointer",
            }}
            onClick={() => setShowAdvanced(!showAdvanced)}
          >
            {showAdvanced ? "hide σ" : "advanced · show σ"}
          </button>
        </div>

        {showAdvanced ? (
          <div style={{ marginTop: 8 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
              <div>
                <div className="pb-section-label">STANDARD DEVIATION</div>
                <div className="pb-mono" style={{ color: "var(--pb-long)", fontWeight: 700, fontSize: 16 }}>
                  {compactDecimal(sigma, 3)}
                </div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div className="pb-section-label">CROWD&apos;S σ</div>
                <div className="pb-mono" style={{ color: "var(--pb-crowd)", fontWeight: 500, fontSize: 15 }}>
                  {compactDecimal(market.crowdSigma, 2)}
                </div>
              </div>
            </div>
            <input
              type="range"
              min={sigmaMin}
              max={sigmaMax}
              step={0.001}
              value={sigma}
              onChange={(e) => {
                setCurve({ mu, sigma: Number(e.target.value) });
                setStatus(null);
              }}
              style={{ width: "100%", accentColor: "var(--pb-long)" }}
            />
          </div>
        ) : null}

        <SectionLabel>Stake</SectionLabel>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
          {STAKE_OPTS.map((opt) => (
            <button
              key={opt}
              type="button"
              onClick={() => setStake(opt)}
              className="pb-mono"
              style={{
                flex: "1 1 28%",
                padding: "10px 4px",
                borderRadius: 10,
                border: "none",
                cursor: "pointer",
                fontWeight: 700,
                background:
                  stake === opt ? "var(--pb-you)" : "var(--pb-surface-el)",
                color: stake === opt ? "var(--pb-on-accent)" : "var(--pb-text)",
              }}
            >
              ${compactDecimal(opt, 0)}
            </button>
          ))}
        </div>

        <div
          style={{
            marginTop: 14,
            padding: 14,
            borderRadius: 14,
            background: "var(--pb-surface-el)",
          }}
        >
          <StatRow label="Stake locked" value={compactDecimal(collateral, 4)} />
          <StatRow label="Fee" value={compactDecimal(fee, 4)} />
          <CompactDivider />
          <StatRow
            label="Max win"
            value={`+$${compactDecimal(maxWin, 2)}`}
            accent="var(--pb-long)"
            strong
          />
          <StatRow
            label="Max loss"
            value={`−$${compactDecimal(maxLoss, 2)}`}
            accent="var(--pb-short)"
          />
        </div>

        {status ? <div style={{ marginTop: 12 }}><StatusBlockSimple message={status.message} variant={status.variant} /></div> : null}

        <div style={{ marginTop: 14 }}>
          <PrimaryButton
            label={
              market.isOnChain
                ? "Sign & place bet · devnet"
                : `Place bet · $${compactDecimal(stake, 0)}`
            }
            accent={market.isOnChain ? "var(--pb-you)" : "var(--pb-long)"}
            onClick={onPrimary}
          />
        </div>

        <p style={{ color: "var(--pb-text-dim)", fontSize: 12, marginTop: 12 }}>
          {market.isOnChain
            ? "Sign on devnet to lock collateral. Quote envelope is the same one the program expects."
            : "Demo market. Bet records locally — use Resolve to draw a synthetic outcome."}
        </p>
      </div>
    </div>
  );
}
