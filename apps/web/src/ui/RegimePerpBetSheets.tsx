import { useState } from "react";
import type { DemoPerpMarket } from "../domain/types";
import type { DemoRegimeIndex } from "../domain/types";
import type { MarketListing } from "../domain/types";
import { compactDecimal } from "../domain/format";
import { BetPrefill } from "../domain/betPrefill";
import {
  CompactDivider,
  PrimaryButton,
  SectionLabel,
  StatRow,
  StatusBlockSimple,
  TagPill,
} from "./shared";

const STAKE_OPTS = [10, 25, 50, 100, 250, 500];

export function RegimeBetSheet({
  market,
  regime,
  onDismiss,
}: {
  market: MarketListing;
  regime: DemoRegimeIndex;
  onDismiss: () => void;
}) {
  const [side, setSide] = useState<"Long" | "Short">(
    () => BetPrefill.consumeRegimeSide() ?? "Long",
  );
  const [stake, setStake] = useState(50);
  const [status, setStatus] = useState<
    { message: string; variant: "error" | "working" | "ok" } | null
  >(null);

  const quote = side === "Long" ? regime.long_quote : regime.short_quote;
  const collateral = Number.parseFloat(quote.collateral_required_display) || 0;
  const fee = Number.parseFloat(quote.fee_paid_display) || 0;
  const maxWin = stake * 2;
  const maxLoss = stake;

  const onSubmit = () => {
    setStatus({
      message:
        "Wallet memo signing is available in the Android demo (Solana Mobile Wallet Adapter).",
      variant: "error",
    });
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
          <TagPill colorVar="var(--pb-long)" filled>
            THEME
          </TagPill>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 12 }}>
            {regime.symbol}
          </span>
        </div>
        <h2 style={{ margin: "8px 0 14px", fontSize: 20 }}>{market.title}</h2>

        <div
          style={{
            display: "flex",
            gap: 4,
            padding: 4,
            borderRadius: 12,
            background: "var(--pb-surface-el)",
          }}
        >
          {(["Long", "Short"] as const).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => {
                setSide(s);
                setStatus(null);
              }}
              style={{
                flex: 1,
                padding: "12px 8px",
                borderRadius: 10,
                border: "none",
                cursor: "pointer",
                fontWeight: 800,
                background:
                  side === s
                    ? s === "Long"
                      ? "var(--pb-long)"
                      : "var(--pb-short)"
                    : "transparent",
                color: side === s ? "#fff" : "var(--pb-text)",
              }}
            >
              {s}
            </button>
          ))}
        </div>

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
          <StatRow
            label="Entry level"
            value={quote.entry_level_display.slice(0, 8)}
            accent="var(--pb-crowd)"
          />
          <StatRow label="Token price" value={quote.token_price_display.slice(0, 8)} />
          <StatRow label="Collateral" value={compactDecimal(collateral, 4)} />
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

        {status ? (
          <div style={{ marginTop: 12 }}>
            <StatusBlockSimple message={status.message} variant={status.variant} />
          </div>
        ) : null}

        <div style={{ marginTop: 14 }}>
          <PrimaryButton
            label={side === "Long" ? "Sign long memo · devnet" : "Sign short memo · devnet"}
            accent={side === "Long" ? "var(--pb-long)" : "var(--pb-short)"}
            foreground="#fff"
            onClick={onSubmit}
          />
        </div>

        <p style={{ color: "var(--pb-text-dim)", fontSize: 12, marginTop: 12 }}>
          {side} the basket of{" "}
          {regime.constituents
            .slice(0, 3)
            .map((c) => c.label)
            .join(" / ")}
          {regime.constituents.length > 3
            ? ` + ${regime.constituents.length - 3} more`
            : ""}
        </p>
      </div>
    </div>
  );
}

export function PerpBetSheet({
  market,
  perp,
  onDismiss,
}: {
  market: MarketListing;
  perp: DemoPerpMarket;
  onDismiss: () => void;
}) {
  const [side, setSide] = useState<"Long" | "Short">(
    () => BetPrefill.consumePerpSide() ?? "Long",
  );
  const [stake, setStake] = useState(50);
  const [status, setStatus] = useState<
    { message: string; variant: "error" | "working" | "ok" } | null
  >(null);

  const quote = side === "Long" ? perp.long_quote : perp.short_quote;
  const collateral = Number.parseFloat(quote.collateral_required_display) || 0;
  const fee = Number.parseFloat(quote.fee_paid_display) || 0;
  const estFunding = Number.parseFloat(quote.estimated_funding_display) || 0;
  const maxWin = stake * 1.6;
  const maxLoss = stake;

  const onSubmit = () => {
    setStatus({
      message:
        "Perp memo signing is available in the Android demo (Solana Mobile Wallet Adapter).",
      variant: "error",
    });
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
          <TagPill colorVar="var(--pb-warn)" filled>
            PERP
          </TagPill>
          <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 12 }}>
            {perp.symbol}
          </span>
        </div>
        <h2 style={{ margin: "8px 0 14px", fontSize: 20 }}>{market.title}</h2>

        <div
          style={{
            display: "flex",
            gap: 4,
            padding: 4,
            borderRadius: 12,
            background: "var(--pb-surface-el)",
          }}
        >
          {(["Long", "Short"] as const).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => {
                setSide(s);
                setStatus(null);
              }}
              style={{
                flex: 1,
                padding: "12px 8px",
                borderRadius: 10,
                border: "none",
                cursor: "pointer",
                fontWeight: 800,
                background:
                  side === s
                    ? s === "Long"
                      ? "var(--pb-long)"
                      : "var(--pb-short)"
                    : "transparent",
                color: side === s ? "#fff" : "var(--pb-text)",
              }}
            >
              {s}
            </button>
          ))}
        </div>

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
          <StatRow label="Mark" value={quote.close_mark_display.slice(0, 10)} />
          <StatRow label="Est. funding" value={compactDecimal(estFunding, 4)} />
          <StatRow label="Collateral" value={compactDecimal(collateral, 4)} />
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

        {status ? (
          <div style={{ marginTop: 12 }}>
            <StatusBlockSimple message={status.message} variant={status.variant} />
          </div>
        ) : null}

        <div style={{ marginTop: 14 }}>
          <PrimaryButton
            label={
              side === "Long"
                ? "Sign long perp memo · devnet"
                : "Sign short perp memo · devnet"
            }
            accent={side === "Long" ? "var(--pb-long)" : "var(--pb-short)"}
            foreground="#fff"
            onClick={onSubmit}
          />
        </div>

        <p style={{ color: "var(--pb-text-dim)", fontSize: 12, marginTop: 12 }}>
          Funding and marks stream from the same demo curve as the Android app.
        </p>
      </div>
    </div>
  );
}
