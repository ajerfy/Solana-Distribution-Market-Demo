import { useState } from "react";
import { postSimulationCommand } from "../api/client";
import { shortHash } from "../domain/format";
import { useParabolaStore } from "../state/parabolaStore";
import { DistributionChart } from "./DistributionChart";
import {
  Card,
  CompactDivider,
  GhostButton,
  MetricPill,
  PrimaryButton,
  StatRow,
  TagPill,
} from "./shared";

export function Engine() {
  const payload = useParabolaStore((s) => s.payload);
  const mergeSimulation = useParabolaStore((s) => s.mergeSimulation);
  const setLastSubmit = useParabolaStore((s) => s.setLastSubmit);
  const lastSubmit = useParabolaStore((s) => s.lastSubmit);
  const walletAddress = useParabolaStore((s) => s.walletAddress);

  const [pending, setPending] = useState<string | null>(null);

  if (!payload) {
    return (
      <div className="pb-scroll" style={{ padding: 24 }}>
        <p style={{ color: "var(--pb-text-sec)" }}>Loading engine data…</p>
      </div>
    );
  }

  const market = payload.market;
  const regimes = payload.regime_indexes ?? [];
  const perp = payload.perps ?? null;
  const simulation = payload.simulation ?? null;
  const feePercent = `${(market.taker_fee_bps / 100).toFixed(2)}%`;
  const unit = market.unit_label ?? "%";

  async function sendSimulationCommand(subpath: string) {
    if (pending) return;
    setPending(subpath);
    try {
      const next = await postSimulationCommand(subpath);
      mergeSimulation(next);
      setLastSubmit({ message: `Simulation command sent: ${subpath}`, isError: false });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setLastSubmit({
        message: `Simulation command failed: ${msg}`,
        isError: true,
      });
    } finally {
      setPending(null);
    }
  }

  const prevMu = simulation?.previous_mu_display
    ? Number.parseFloat(simulation.previous_mu_display)
    : null;
  const prevSig = simulation?.previous_sigma_display
    ? Number.parseFloat(simulation.previous_sigma_display)
    : null;
  const curMu = simulation?.current_mu_display
    ? Number.parseFloat(simulation.current_mu_display)
    : null;
  const curSig = simulation?.current_sigma_display
    ? Number.parseFloat(simulation.current_sigma_display)
    : null;
  const fallbackMu = curMu ?? prevMu ?? 50;
  const fallbackSig = curSig ?? prevSig ?? 10;

  return (
    <div className="pb-scroll">
      <header style={{ padding: "22px 20px 12px" }}>
        <h1 style={{ margin: 0, fontSize: 28, fontWeight: 600 }}>Engine</h1>
        <p style={{ color: "var(--pb-text-sec)", marginTop: 8, maxWidth: 560 }}>
          A plain-language view of how Parabola prices markets, where liquidity comes from, and how money moves after a trade.
        </p>
      </header>

      {simulation ? (
        <Card style={{ margin: "0 20px 14px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <div style={{ fontWeight: 700 }}>Live market movement</div>
              <div style={{ color: "var(--pb-text-sec)", fontSize: 13, marginTop: 4 }}>
                {simulation.scenario} · {simulation.regime} · {simulation.speed}x
              </div>
            </div>
            <TagPill colorVar={simulation.running ? "var(--pb-long)" : "var(--pb-text-dim)"} filled={simulation.running}>
              {simulation.running ? "RUNNING" : "PAUSED"}
            </TagPill>
          </div>

          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <div style={{ flex: 1 }}>
              <PrimaryButton
                label={simulation.running ? "Pause" : "Start"}
                accent={simulation.running ? "var(--pb-warn)" : "var(--pb-you)"}
                onClick={() =>
                  void sendSimulationCommand(simulation.running ? "pause" : "start")
                }
                disabled={pending !== null}
              />
            </div>
            <div style={{ flex: 1 }}>
              <GhostButton
                label={`Speed ${simulation.speed}x`}
                onClick={() => void sendSimulationCommand("speed")}
                disabled={pending != null}
              />
            </div>
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <div style={{ flex: 1 }}>
              <GhostButton
                label="Reset"
                onClick={() => void sendSimulationCommand("reset")}
                disabled={pending != null}
              />
            </div>
            <div style={{ flex: 1 }}>
              <GhostButton
                label="News shock"
                onClick={() => void sendSimulationCommand("shock")}
                disabled={pending != null}
              />
            </div>
          </div>

          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            {(
              [
                { label: "Bullish", regime: "bullish", accent: "var(--pb-long)" },
                { label: "Bearish", regime: "bearish", accent: "var(--pb-short)" },
                { label: "Volatile", regime: "volatile", accent: "var(--pb-warn)" },
              ] as const
            ).map(({ label, regime, accent }) => (
              <div key={regime} style={{ flex: 1 }}>
                {simulation.regime === regime ? (
                  <PrimaryButton
                    label={label}
                    accent={accent}
                    foreground="#fff"
                    onClick={() => void sendSimulationCommand(`regime/${regime}`)}
                    disabled={pending != null}
                  />
                ) : (
                  <GhostButton
                    label={label}
                    onClick={() => void sendSimulationCommand(`regime/${regime}`)}
                    disabled={pending != null}
                  />
                )}
              </div>
            ))}
          </div>

          <p style={{ color: "var(--pb-text-sec)", fontSize: 14, marginTop: 12 }}>
            Pick a regime to show clustered higher beliefs, clustered lower beliefs, or aggressive two-sided flow. The asymmetric shading is a visual pressure layer; settlement still uses the Normal quote engine.
          </p>

          {prevMu != null && prevSig != null && curMu != null && curSig != null ? (
            <div style={{ marginTop: 14 }}>
              <DistributionChart
                crowdMu={prevMu}
                crowdSigma={prevSig}
                yourMu={curMu}
                yourSigma={curSig}
                height={150}
              />
            </div>
          ) : (
            <div style={{ marginTop: 14 }}>
              <DistributionChart
                crowdMu={fallbackMu}
                crowdSigma={fallbackSig}
                yourMu={curMu ?? fallbackMu}
                yourSigma={curSig ?? fallbackSig}
                height={150}
              />
            </div>
          )}

          <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
            <MetricPill label="TRADES" value={String(simulation.trade_count)} accent="var(--pb-crowd)" />
            <MetricPill label="FEES" value={simulation.fees_earned_display.slice(0, 8)} accent="var(--pb-long)" />
            <MetricPill label="VOLUME" value={simulation.total_volume_display.slice(0, 8)} accent="var(--pb-warn)" />
          </div>

          {simulation.trade_tape.length > 0 ? (
            <>
              <CompactDivider style={{ marginTop: 16 }} />
              <div style={{ fontWeight: 700, marginBottom: 8 }}>Recent crowd trades</div>
              {simulation.trade_tape.slice(0, 4).map((t) => (
                <div
                  key={t.id}
                  style={{
                    padding: "10px 12px",
                    borderRadius: 12,
                    background: "var(--pb-surface-el)",
                    marginBottom: 8,
                  }}
                >
                  <div style={{ fontWeight: 600 }}>
                    {t.handle} · {t.agent_type}
                  </div>
                  <div style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>{t.action}</div>
                  <div className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 12 }}>
                    avg {t.target_mu_display.slice(0, 6)} {unit} · confidence width {t.target_sigma_display.slice(0, 6)}
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", marginTop: 6 }}>
                    <span style={{ color: t.accepted ? "var(--pb-long)" : "var(--pb-short)", fontWeight: 700 }}>
                      {t.total_debit_display.slice(0, 7)}
                    </span>
                    <span className="pb-mono" style={{ fontSize: 11, color: "var(--pb-text-dim)" }}>
                      fee {t.fee_display.slice(0, 6)}
                    </span>
                  </div>
                </div>
              ))}
            </>
          ) : null}

          {simulation.last_error ? (
            <p style={{ color: "var(--pb-short)", fontSize: 13, marginTop: 12 }}>
              {simulation.last_error}
            </p>
          ) : null}
        </Card>
      ) : (
        <Card style={{ margin: "0 20px 14px" }}>
          <div style={{ fontWeight: 700 }}>Live movement demo</div>
          <p style={{ color: "var(--pb-text-sec)", marginTop: 8 }}>
            Start the local backend to stream simulated crowd trades into this screen. The bundled asset still works, but it does not animate market movement.
          </p>
        </Card>
      )}

      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ fontWeight: 700 }}>Live feeds now</span>
          <span style={{ flex: 1 }} />
          <TagPill colorVar="var(--pb-chain)">DEVNET</TagPill>
        </div>
        <p style={{ color: "var(--pb-text-sec)", marginTop: 10 }}>
          Parabola is currently anchoring one live Polymarket event and one live SOL perp.
        </p>
        <CompactDivider />
        <StatRow label="Featured event" value={market.title} accent="var(--pb-crowd)" strong />
        <StatRow label="Event source" value={market.source_badge ?? "Live market feed"} />
        <StatRow label="Live event estimate" value={market.current_mu_display.slice(0, 10)} accent="var(--pb-crowd)" />
        <StatRow label="Confidence width" value={market.current_sigma_display.slice(0, 10)} />
        {market.best_bid_display ? (
          <StatRow
            label="Best bid / ask"
            value={`${market.best_bid_display.slice(0, 6)} / ${market.best_ask_display?.slice(0, 6) ?? "—"}`}
          />
        ) : null}
        {perp ? (
          <>
            <CompactDivider />
            <StatRow label="Featured perp" value={perp.symbol} accent="var(--pb-warn)" strong />
            <StatRow label="Perp source" value="Pyth Hermes" />
            <StatRow label="Current mark" value={`$${perp.mark_price_display.slice(0, 8)}`} />
            <StatRow
              label="Funding rate"
              value={perp.spot_funding_rate_display.slice(0, 10)}
              accent={
                (Number.parseFloat(perp.spot_funding_rate_display) || 0) >= 0
                  ? "var(--pb-long)"
                  : "var(--pb-short)"
              }
            />
          </>
        ) : null}
        <CompactDivider />
        <StatRow label="Trading fee" value={feePercent} />
        <CompactDivider />
        <StatRow label="Status" value={market.status.toUpperCase()} accent="var(--pb-long)" strong />
      </Card>

      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ fontWeight: 700 }}>Where liquidity comes from</div>
        <p style={{ color: "var(--pb-text-sec)", marginTop: 10 }}>
          A maker seeds the pool with starting cash. Each trade adds collateral and pays a fee into the same pool.
        </p>
        <CompactDivider />
        <StatRow label="Starting maker seed" value={market.maker_deposit_display.slice(0, 12)} />
        <StatRow label="Total backing now" value={market.backing_display.slice(0, 12)} strong />
        <StatRow label="Fees earned so far" value={market.maker_fees_earned_display.slice(0, 12)} accent="var(--pb-long)" />
        <StatRow label="Open trades" value={`${market.total_trades} / ${market.max_open_trades}`} />
        <CompactDivider />
        <p style={{ color: "var(--pb-text-dim)", fontSize: 13, margin: 0 }}>
          On estimate markets, the closer your curve is to the eventual outcome, the more of that pool you can claim when the market resolves.
        </p>
      </Card>

      {perp ? (
        <Card style={{ margin: "0 20px 14px" }}>
          <div style={{ fontWeight: 700 }}>Perpetual markets</div>
          <p style={{ color: "var(--pb-text-sec)", marginTop: 8 }}>
            Perps let traders stay long or short without waiting for one final resolution event.
          </p>
          <CompactDivider />
          <StatRow label="Current mark" value={`$${perp.mark_price_display.slice(0, 8)}`} strong />
          <StatRow label="Open perp positions" value={String(perp.open_positions)} />
        </Card>
      ) : null}

      {regimes.length > 0 ? (
        <Card style={{ margin: "0 20px 14px" }}>
          <div style={{ fontWeight: 700 }}>Regime indexes</div>
          <p style={{ color: "var(--pb-text-sec)", marginTop: 8 }}>
            A regime index bundles several markets into one tradeable theme.
          </p>
          <CompactDivider />
          <StatRow label="Live theme baskets" value={String(regimes.length)} strong />
          {regimes.slice(0, 3).map((r) => (
            <StatRow
              key={r.id}
              label={r.symbol}
              value={`${r.level_display.slice(0, 7)} (${r.change_display.slice(0, 8)})`}
              accent={
                (Number.parseFloat(r.change_display) || 0) >= 0 ? "var(--pb-long)" : "var(--pb-short)"
              }
            />
          ))}
          {regimes.length > 3 ? (
            <p style={{ color: "var(--pb-text-dim)", fontSize: 12, marginTop: 8 }}>
              {regimes.length - 3} more baskets are available from the Markets tab.
            </p>
          ) : null}
        </Card>
      ) : null}

      <Card style={{ margin: "0 20px 96px" }}>
        <div style={{ fontWeight: 700 }}>Wallet + last action</div>
        {lastSubmit == null ? (
          <p style={{ color: "var(--pb-text-sec)", marginTop: 8 }}>
            No trade or demo transaction has been submitted in this session yet.
          </p>
        ) : (
          <p style={{ color: lastSubmit.isError ? "var(--pb-short)" : "var(--pb-long)", marginTop: 8 }}>
            {lastSubmit.message}
          </p>
        )}
        <CompactDivider />
        {walletAddress ? (
          <StatRow label="Connected wallet" value={shortHash(walletAddress, 6, 6)} accent="var(--pb-chain)" />
        ) : (
          <p style={{ color: "var(--pb-text-dim)", fontSize: 13 }}>
            Connect a wallet by signing from a market ticket. Until then, the app stays in local demo mode.
          </p>
        )}
      </Card>
    </div>
  );
}
