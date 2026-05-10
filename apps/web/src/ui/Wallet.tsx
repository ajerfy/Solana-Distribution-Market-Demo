import { useParabolaStore } from "../state/parabolaStore";
import { shortHash } from "../domain/format";
import { Card, GhostButton, SectionLabel } from "./shared";

export function Wallet() {
  const walletAddress = useParabolaStore((s) => s.walletAddress);
  const payload = useParabolaStore((s) => s.payload);
  const liveSync = useParabolaStore((s) => s.liveSync);
  const lastSubmit = useParabolaStore((s) => s.lastSubmit);
  const themeMode = useParabolaStore((s) => s.themeMode);
  const setThemeMode = useParabolaStore((s) => s.setThemeMode);

  return (
    <div className="pb-scroll">
      <header style={{ padding: "22px 20px 12px" }}>
        <h1 style={{ margin: 0, fontSize: 28, fontWeight: 600 }}>Wallet</h1>
        <p style={{ color: "var(--pb-text-sec)", marginTop: 6 }}>
          Connection status & devnet identity.
        </p>
      </header>

      <Card style={{ margin: "0 20px 14px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span
            style={{
              width: 10,
              height: 10,
              borderRadius: 99,
              background: walletAddress ? "var(--pb-long)" : "var(--pb-text-dim)",
            }}
          />
          <span style={{ fontWeight: 700, fontSize: 17 }}>
            {walletAddress ? "Connected" : "Not connected"}
          </span>
        </div>
        <p className="pb-mono" style={{ color: "var(--pb-text-sec)", marginTop: 10 }}>
          {walletAddress
            ? shortHash(walletAddress, 6, 6)
            : "Sign a bet on the on-chain market to connect."}
        </p>
      </Card>

      <Card style={{ margin: "0 20px 14px" }}>
        <SectionLabel>NETWORK</SectionLabel>
        <div style={{ fontWeight: 700, marginTop: 6 }}>Solana devnet</div>
        <div className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 13 }}>
          api.devnet.solana.com
        </div>
        <div style={{ marginTop: 14 }}>
          <SectionLabel>DATA SOURCE</SectionLabel>
          <div style={{ fontWeight: 700, marginTop: 6 }}>
            {liveSync.mode === "live"
              ? "Live oracle feed"
              : liveSync.mode === "connecting"
                ? "Connecting to live backend"
                : liveSync.mode === "error"
                  ? "Live backend error"
                  : "Bundled demo payload"}
          </div>
          <p style={{ color: "var(--pb-text-sec)", fontSize: 14, marginTop: 4 }}>
            {liveSync.source}
          </p>
          {liveSync.endpoint ? (
            <p className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 12, wordBreak: "break-all" }}>
              {liveSync.endpoint}
            </p>
          ) : null}
          {liveSync.lastUpdatedMillis != null ? (
            <p style={{ color: "var(--pb-text-dim)", fontSize: 11, marginTop: 8 }}>
              Updated{" "}
              {new Date(liveSync.lastUpdatedMillis).toLocaleTimeString(undefined, {
                timeStyle: "short",
              })}
            </p>
          ) : null}
        </div>

        {payload ? (
          <div style={{ marginTop: 14 }}>
            <SectionLabel>PRIMARY MARKET</SectionLabel>
            <p style={{ marginTop: 6 }}>{payload.market.title}</p>
            <p className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 12 }}>
              id {shortHash(payload.market.market_id_hex, 6, 6)} · v{payload.market.state_version}
            </p>
          </div>
        ) : null}
      </Card>

      <Card style={{ margin: "0 20px 14px" }}>
        <SectionLabel>APPEARANCE</SectionLabel>
        <div
          style={{
            display: "flex",
            gap: 4,
            padding: 4,
            marginTop: 10,
            borderRadius: 12,
            background: "var(--pb-surface-el)",
          }}
        >
          {(["dark", "light"] as const).map((mode) => {
            const selected = themeMode === mode;
            return (
              <button
                key={mode}
                type="button"
                onClick={() => setThemeMode(mode)}
                style={{
                  flex: 1,
                  padding: "10px 8px",
                  borderRadius: 10,
                  border: "none",
                  cursor: "pointer",
                  fontWeight: 700,
                  background: selected ? "var(--pb-you)" : "transparent",
                  color: selected ? "var(--pb-on-accent)" : "var(--pb-text)",
                }}
              >
                {mode === "light" ? "☀ Light" : "☾ Dark"}
              </button>
            );
          })}
        </div>
      </Card>

      <Card style={{ margin: "0 20px 14px" }}>
        <SectionLabel>HELP</SectionLabel>
        <div style={{ marginTop: 8 }}>
          <GhostButton label="Replay tutorials" onClick={() => {}} disabled />
        </div>
        <p style={{ color: "var(--pb-text-dim)", fontSize: 11, marginTop: 8 }}>
          Tutorial overlays are Android-only in the reference app.
        </p>
      </Card>

      {lastSubmit ? (
        <Card style={{ margin: "0 20px 96px" }}>
          <SectionLabel>LAST SUBMISSION</SectionLabel>
          <p style={{ color: lastSubmit.isError ? "var(--pb-short)" : "var(--pb-long)", marginTop: 8 }}>
            {lastSubmit.message}
          </p>
        </Card>
      ) : (
        <div style={{ height: 96 }} />
      )}
    </div>
  );
}
