import { useCallback, useEffect, useState } from "react";
import "./App.css";
import { demoPayloadUrl, fetchDemoPayload, fetchHealthz } from "./api/client";
import type { DemoPayload } from "./types/demoPayload";

export default function App() {
  const [payload, setPayload] = useState<DemoPayload | null>(null);
  const [healthOk, setHealthOk] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [h, p] = await Promise.all([fetchHealthz(), fetchDemoPayload()]);
      setHealthOk(h.ok);
      setPayload(p);
    } catch (e) {
      setHealthOk(null);
      setPayload(null);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="app">
      <div className="panel">
        <h1>Parabola · Web</h1>
        <p className="meta">
          API: <code>{demoPayloadUrl()}</code>
        </p>
        <div className="row">
          <button type="button" disabled={loading} onClick={() => void load()}>
            {loading ? "Loading…" : "Refresh"}
          </button>
          {healthOk !== null && (
            <span className={healthOk ? "success" : "error"}>
              backend healthz: {healthOk ? "ok" : "unexpected"}
            </span>
          )}
        </div>
        {error && <p className="error">{error}</p>}
      </div>

      {payload && (
        <div className="panel">
          <h2 style={{ margin: "0 0 0.5rem", fontSize: "1.1rem" }}>
            {payload.market.title}
          </h2>
          <p className="meta">{payload.market.status}</p>
          <dl className="grid">
            <dt>μ / σ</dt>
            <dd>
              {payload.market.current_mu_display} · {payload.market.current_sigma_display}
            </dd>
            <dt>k</dt>
            <dd>{payload.market.k_display}</dd>
            <dt>Backing</dt>
            <dd>{payload.market.backing_display}</dd>
            <dt>Market id</dt>
            <dd>{payload.market.market_id_hex}</dd>
          </dl>
          {payload.presets.length > 0 && (
            <>
              <p style={{ margin: "1rem 0 0.35rem", fontSize: "0.85rem", color: "#9aa3b5" }}>
                Presets ({payload.presets.length})
              </p>
              <ul style={{ margin: 0, paddingLeft: "1.2rem", fontSize: "0.9rem" }}>
                {payload.presets.slice(0, 5).map((p) => (
                  <li key={p.id}>
                    {p.label}: μ {p.target_mu_display}, σ {p.target_sigma_display}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </div>
  );
}
