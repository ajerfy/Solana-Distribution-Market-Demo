import type {
  DemoLiveFeed,
  DemoMarket,
  DemoPayload,
  DemoPerpMarket,
  DemoPreset,
  DemoRegimeIndex,
  DemoSimulation,
} from "../domain/types";

function apiBase(): string {
  const explicit = import.meta.env.VITE_PARABOLA_API_BASE?.trim();
  if (explicit) {
    return explicit.replace(/\/$/, "");
  }
  if (import.meta.env.DEV) {
    return "";
  }
  return "http://127.0.0.1:8787".replace(/\/$/, "");
}

/** URL used for `fetch` (no UX suffix). */
export function demoPayloadFetchUrl(): string {
  return `${apiBase()}/api/demo-payload`;
}

/** Display string for UI (includes proxy hint in dev). */
export function demoPayloadUrl(): string {
  const base = apiBase();
  const path = `${base}/api/demo-payload`;
  if (import.meta.env.DEV && base === "") {
    return `${path} (via Vite proxy → http://127.0.0.1:8787)`;
  }
  return path;
}

/** Normalize GET /api/demo-payload JSON (snake_case keys). */
export function normalizeDemoPayload(raw: Record<string, unknown>): DemoPayload {
  return {
    market: raw.market as DemoMarket,
    presets: (raw.presets as DemoPreset[]) ?? [],
    quote_grid: (raw.quote_grid as DemoPreset[]) ?? [],
    regime_indexes: (raw.regime_indexes as DemoRegimeIndex[]) ?? [],
    perps: (raw.perps as DemoPerpMarket | null | undefined) ?? null,
    live_feed: raw.live_feed as DemoLiveFeed | undefined,
    simulation: raw.simulation as DemoSimulation | undefined,
  };
}

export async function fetchDemoPayload(): Promise<DemoPayload> {
  const r = await fetch(demoPayloadFetchUrl(), {
    headers: { Accept: "application/json" },
  });
  if (!r.ok) {
    throw new Error(`demo-payload HTTP ${r.status}`);
  }
  const json = (await r.json()) as Record<string, unknown>;
  return normalizeDemoPayload(json);
}

export async function fetchHealthz(): Promise<{ ok: boolean }> {
  const r = await fetch(`${apiBase()}/healthz`, {
    headers: { Accept: "application/json" },
  });
  if (!r.ok) {
    throw new Error(`healthz HTTP ${r.status}`);
  }
  return r.json() as Promise<{ ok: boolean }>;
}

/** POST /api/simulation/{start|pause|reset|...} — body is a `DemoSimulation` JSON object. */
export async function postSimulationCommand(subpath: string): Promise<DemoSimulation> {
  const r = await fetch(`${apiBase()}/api/simulation/${subpath}`, {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  if (!r.ok) {
    throw new Error(`simulation ${subpath} HTTP ${r.status}`);
  }
  return r.json() as Promise<DemoSimulation>;
}
