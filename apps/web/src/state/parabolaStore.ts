import { create } from "zustand";
import type {
  BetRecord,
  DemoPayload,
  DemoSimulation,
  LiveSyncMode,
  LiveSyncStatus,
  MarketCategory,
  MarketListing,
  NavTab,
  SubmitStatus,
  ThemeMode,
} from "../domain/types";
import { buildMarketListings } from "../domain/mockMarkets";
import { STATIC_PAYLOAD } from "../domain/staticPayload";

const LS_BETS = "parabola.web.bets.v1";

let _marketCache: { payload: DemoPayload | null; listings: MarketListing[] } = {
  payload: null,
  listings: [],
};
const LS_THEME = "parabola.web.theme.v1";

function loadBets(): BetRecord[] {
  try {
    const raw = localStorage.getItem(LS_BETS);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as BetRecord[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveBets(bets: BetRecord[]) {
  localStorage.setItem(LS_BETS, JSON.stringify(bets));
}

function loadTheme(): ThemeMode {
  const t = localStorage.getItem(LS_THEME);
  return t === "light" ? "light" : "dark";
}

/** True if `next` is stale compared to `baseline` (Kotlin `isOlderThan`). */
function simulationIsStale(next: DemoSimulation, baseline: DemoSimulation): boolean {
  return (
    next.revision < baseline.revision ||
    (next.revision === baseline.revision && next.tick < baseline.tick)
  );
}

function displayDelta(previous: string | undefined, next: string): number {
  const nextValue = Number.parseFloat(next);
  if (Number.isNaN(nextValue)) return 0;
  const prevValue = previous ? Number.parseFloat(previous) : nextValue;
  if (Number.isNaN(prevValue)) return 0;
  return nextValue - prevValue;
}

function addDisplayValue(current: string, delta: number): string {
  const currentValue = Number.parseFloat(current);
  if (Number.isNaN(currentValue)) return current;
  return `${(currentValue + delta).toFixed(9)}`;
}

export type MarketTypeFilterKey = "All" | "Estimates" | "Perps" | "RegimeIndexes";

interface ParabolaState {
  entered: boolean;
  payload: DemoPayload | null;
  selectedMarketId: string | null;
  selectedCategory: MarketCategory;
  selectedMarketTypeFilter: MarketTypeFilterKey;
  activeTab: NavTab;
  showBetSheet: boolean;
  themeMode: ThemeMode;
  bets: BetRecord[];
  liveSync: LiveSyncStatus;
  lastSubmit: SubmitStatus | null;
  walletAddress: string | null;

  setEntered: (v: boolean) => void;
  setPayload: (p: DemoPayload) => void;
  mergePayloadFromLive: (next: DemoPayload) => void;
  mergeSimulation: (next: DemoSimulation) => void;
  resolveBet: (
    record: BetRecord,
    realized: number,
    pnl: number,
  ) => void;
  setLiveSync: (s: LiveSyncStatus) => void;
  openMarket: (id: string) => void;
  closeMarket: () => void;
  setShowBetSheet: (v: boolean) => void;
  setActiveTab: (t: NavTab) => void;
  setSelectedCategory: (c: MarketCategory) => void;
  setSelectedMarketTypeFilter: (f: MarketTypeFilterKey) => void;
  setThemeMode: (m: ThemeMode) => void;
  addBet: (b: BetRecord) => void;
  clearAllBets: () => void;
  setLastSubmit: (s: SubmitStatus | null) => void;
  setWalletAddress: (a: string | null) => void;

  markets: () => MarketListing[];
  marketById: (id: string | null | undefined) => MarketListing | undefined;
}

export const useParabolaStore = create<ParabolaState>((set, get) => ({
  entered: false,
  payload: STATIC_PAYLOAD,
  selectedMarketId: null,
  selectedCategory: "All",
  selectedMarketTypeFilter: "All",
  activeTab: "markets",
  showBetSheet: false,
  themeMode: typeof localStorage !== "undefined" ? loadTheme() : "dark",
  bets: typeof localStorage !== "undefined" ? loadBets() : [],
  liveSync: {
    mode: "demo",
    source: "Bundled demo asset",
    endpoint: null,
    lastUpdatedMillis: null,
    message: "Using seeded payload until a live backend responds.",
  },
  lastSubmit: null,
  walletAddress: null,

  setEntered: (v) => set({ entered: v }),
  setPayload: (p) => set({ payload: p }),

  mergePayloadFromLive: (next) =>
    set((s) => {
      if (!s.payload) return { payload: next };
      const curSim = s.payload.simulation;
      const nextSim = next.simulation;
      const keepSim =
        curSim != null &&
        (nextSim == null || simulationIsStale(nextSim, curSim));
      const merged: DemoPayload = keepSim
        ? {
            ...next,
            market: {
              ...next.market,
              status: curSim.running
                ? "Live crowd simulation"
                : next.market.status,
              current_mu_display: curSim.current_mu_display,
              current_sigma_display: curSim.current_sigma_display,
              total_trades: curSim.trade_count,
            },
            simulation: curSim,
          }
        : next;
      const sel = s.selectedMarketId;
      const listings = buildMarketListings(merged);
      const stillExists = sel && listings.some((m) => m.id === sel);
      return {
        payload: merged,
        selectedMarketId: stillExists ? sel : null,
        showBetSheet: stillExists ? s.showBetSheet : false,
      };
    }),

  mergeSimulation: (next) =>
    set((s) => {
      if (!s.payload) return {};
      const prevSim = s.payload.simulation;
      if (prevSim != null && simulationIsStale(next, prevSim)) return {};
      const feeDelta = displayDelta(
        prevSim?.fees_earned_display,
        next.fees_earned_display,
      );
      const volumeDelta = displayDelta(
        prevSim?.total_volume_display,
        next.total_volume_display,
      );
      const nextMarket = {
        ...s.payload.market,
        status: next.running
          ? "Live crowd simulation"
          : s.payload.market.status,
        current_mu_display: next.current_mu_display,
        current_sigma_display: next.current_sigma_display,
        backing_display: addDisplayValue(
          s.payload.market.backing_display,
          volumeDelta,
        ),
        maker_fees_earned_display: addDisplayValue(
          s.payload.market.maker_fees_earned_display,
          feeDelta,
        ),
        total_trades: next.trade_count,
      };
      return {
        payload: {
          ...s.payload,
          market: nextMarket,
          simulation: next,
        },
      };
    }),

  setLiveSync: (liveSync) => set({ liveSync }),

  openMarket: (id) =>
    set({ selectedMarketId: id, showBetSheet: false }),
  closeMarket: () => set({ selectedMarketId: null, showBetSheet: false }),
  setShowBetSheet: (v) => set({ showBetSheet: v }),
  setActiveTab: (activeTab) => set({ activeTab }),
  setSelectedCategory: (selectedCategory) => set({ selectedCategory }),
  setSelectedMarketTypeFilter: (selectedMarketTypeFilter) =>
    set({ selectedMarketTypeFilter }),
  setThemeMode: (themeMode) => {
    localStorage.setItem(LS_THEME, themeMode);
    set({ themeMode });
  },

  addBet: (b) =>
    set((s) => {
      const bets = [b, ...s.bets];
      saveBets(bets);
      return { bets };
    }),

  resolveBet: (record, realized, pnl) =>
    set((s) => {
      const bets = s.bets.map((x) =>
        x.id === record.id
          ? {
              ...record,
              resolved: true,
              realizedOutcome: realized,
              realizedPnl: pnl,
            }
          : x,
      );
      saveBets(bets);
      return { bets };
    }),

  clearAllBets: () => {
    saveBets([]);
    set({ bets: [] });
  },

  setLastSubmit: (lastSubmit) => set({ lastSubmit }),
  setWalletAddress: (walletAddress) => set({ walletAddress }),

  markets: () => {
    const p = get().payload;
    if (!p) return [];
    if (p === _marketCache.payload) return _marketCache.listings;
    const listings = buildMarketListings(p);
    _marketCache = { payload: p, listings };
    return listings;
  },

  marketById: (id) => {
    if (!id) return undefined;
    return get().markets().find((m) => m.id === id);
  },
}));

export function liveModeFromFeed(mode: string | undefined): LiveSyncMode {
  const m = mode?.toLowerCase();
  if (m === "live") return "live";
  if (m === "connecting") return "connecting";
  if (m === "degraded" || m === "error") return "error";
  return "demo";
}
