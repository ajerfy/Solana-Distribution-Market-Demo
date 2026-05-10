import type { DemoPayload } from "../types/demoPayload";

/**
 * In `vite dev`, use same-origin URLs so the dev server proxy can reach the Rust backend.
 * Set `VITE_PARABOLA_API_BASE` to override (e.g. remote staging API).
 */
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

/** Label for UI: what URL is effectively used for demo JSON. */
export function demoPayloadUrl(): string {
  const base = apiBase();
  const path = `${base}/api/demo-payload`;
  if (import.meta.env.DEV && base === "") {
    return `${path} (via Vite proxy → http://127.0.0.1:8787)`;
  }
  return path;
}

export async function fetchDemoPayload(): Promise<DemoPayload> {
  const r = await fetch(`${apiBase()}/api/demo-payload`, {
    headers: { Accept: "application/json" },
  });
  if (!r.ok) {
    throw new Error(`demo-payload HTTP ${r.status}`);
  }
  return r.json() as Promise<DemoPayload>;
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
