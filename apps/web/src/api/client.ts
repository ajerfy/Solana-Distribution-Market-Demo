import type { DemoPayload } from "../types/demoPayload";

function apiBase(): string {
  const raw = import.meta.env.VITE_PARABOLA_API_BASE ?? "http://127.0.0.1:8787";
  return raw.replace(/\/$/, "");
}

export function demoPayloadUrl(): string {
  return `${apiBase()}/api/demo-payload`;
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
