import { useEffect, useRef } from "react";
import { demoPayloadFetchUrl, normalizeDemoPayload } from "../api/client";
import type { DemoSimulation } from "../domain/types";
import {
  liveModeFromFeed,
  useParabolaStore,
} from "../state/parabolaStore";

function simulationStreamUrl(): string {
  if (import.meta.env.DEV) return "/api/simulation/stream";
  const b = import.meta.env.VITE_PARABOLA_API_BASE?.trim().replace(/\/$/, "");
  return b ? `${b}/api/simulation/stream` : "/api/simulation/stream";
}

/**
 * Polls live payload every 3s (matching Android) and subscribes to SSE simulation stream.
 */
export function useBackendSync(enabled: boolean): void {
  const mergePayloadFromLive = useParabolaStore((s) => s.mergePayloadFromLive);
  const mergeSimulation = useParabolaStore((s) => s.mergeSimulation);
  const setLiveSync = useParabolaStore((s) => s.setLiveSync);

  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled) return;

    let cancelled = false;

    const poll = async () => {
      try {
        const response = await fetch(demoPayloadFetchUrl(), {
          headers: { Accept: "application/json" },
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const json = (await response.json()) as Record<string, unknown>;
        const payload = normalizeDemoPayload(json);
        if (cancelled) return;
        mergePayloadFromLive(payload);
        const feed = payload.live_feed;
        const mode = liveModeFromFeed(feed?.mode);
        const now = Date.now();
        setLiveSync({
          mode,
          source: feed?.source ?? "Parabola live backend",
          endpoint: feed?.endpoint ?? demoPayloadFetchUrl(),
          lastUpdatedMillis:
            feed?.last_update_unix_ms && feed.last_update_unix_ms > 0
              ? feed.last_update_unix_ms
              : now,
          message: feed?.message ?? "Oracle data is live.",
        });
      } catch (e) {
        if (cancelled) return;
        const msg = e instanceof Error ? e.message : String(e);
        const cur = useParabolaStore.getState().liveSync;
        if (cur.mode !== "live") {
          setLiveSync({
            mode: "demo",
            source: "Bundled demo asset",
            endpoint: demoPayloadFetchUrl(),
            lastUpdatedMillis: null,
            message: `Live backend unavailable: ${msg}`,
          });
        }
      }
    };

    const interval = window.setInterval(() => {
      void poll();
    }, 3000);
    void poll();

    let reconnectTimer: number | undefined;

    const connectStream = () => {
      const url = simulationStreamUrl();
      try {
        const es = new EventSource(url);
        esRef.current = es;
        es.addEventListener("simulation", (ev: MessageEvent) => {
          try {
            const sim = JSON.parse(ev.data) as DemoSimulation;
            if (sim?.revision != null) {
              mergeSimulation(sim);
              const ls = useParabolaStore.getState().liveSync;
              if (ls.mode !== "live") {
                setLiveSync({
                  mode: "live",
                  source: ls.source,
                  endpoint: url,
                  lastUpdatedMillis: Date.now(),
                  message: "Simulation stream is live.",
                });
              }
            }
          } catch {
            /* ignore */
          }
        });
        es.onerror = () => {
          es.close();
          reconnectTimer = window.setTimeout(connectStream, 1200);
        };
      } catch {
        reconnectTimer = window.setTimeout(connectStream, 1200);
      }
    };

    connectStream();

    return () => {
      cancelled = true;
      window.clearInterval(interval);
      if (reconnectTimer) window.clearTimeout(reconnectTimer);
      esRef.current?.close();
      esRef.current = null;
    };
  }, [enabled, mergePayloadFromLive, mergeSimulation, setLiveSync]);
}
