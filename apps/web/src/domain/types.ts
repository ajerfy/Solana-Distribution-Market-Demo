/** Mirrors Kotlin `Models.kt` / JSON payload shape (`Payload.kt`). */

export type ThemeMode = "light" | "dark";

export type NavTab = "markets" | "portfolio" | "engine" | "wallet";

export type MarketType = "Estimation" | "RegimeIndex" | "Perp";

export type LiveSyncMode = "demo" | "connecting" | "live" | "error";

export interface LiveSyncStatus {
  mode: LiveSyncMode;
  source: string;
  endpoint: string | null;
  lastUpdatedMillis: number | null;
  message: string;
}

export type MarketCategory =
  | "All"
  | "Events"
  | "Weather"
  | "Crypto"
  | "Sports"
  | "PopCulture"
  | "Climate"
  | "Macro"
  | "Equities"
  | "Politics";

export const CATEGORY_META: Record<
  Exclude<MarketCategory, "All">,
  { label: string; emoji: string }
> = {
  Events: { label: "Events", emoji: "◆" },
  Weather: { label: "Weather", emoji: "☁" },
  Crypto: { label: "Crypto", emoji: "◈" },
  Sports: { label: "Sports", emoji: "◎" },
  PopCulture: { label: "Pop", emoji: "♫" },
  Climate: { label: "Climate", emoji: "◉" },
  Macro: { label: "Macro", emoji: "▤" },
  Equities: { label: "Equities", emoji: "◇" },
  Politics: { label: "Politics", emoji: "◐" },
};

export type MarketTypeFilterKey = "All" | "Estimates" | "Perps" | "RegimeIndexes";

export interface LiveEventStats {
  outcomeLabel: string;
  yesPrice: number | null;
  noPrice: number | null;
  bestBid: number | null;
  bestAsk: number | null;
  spread: number | null;
  updatedAtMillis: number | null;
}

export interface DemoRegimeHistoryPoint {
  slot: number;
  level_display: string;
}

export interface DemoRegimeConstituent {
  id: string;
  label: string;
  side: string;
  weight_bps: number;
  probability_display: string;
  previous_probability_display: string;
  level_contribution_display: string;
  signed_pressure_display: string;
  status: string;
  expiry_slot: number;
}

export interface DemoRegimeQuote {
  side: string;
  size_display: string;
  entry_level_display: string;
  token_price_display: string;
  collateral_required_display: string;
  fee_paid_display: string;
  total_debit_display: string;
  memo_payload: string;
}

export interface DemoRegimeIndex {
  id: string;
  symbol: string;
  title: string;
  thesis: string;
  status: string;
  level_display: string;
  previous_level_display: string;
  change_display: string;
  rebalance_slot: number;
  next_rebalance_slot: number;
  quote_expiry_slot: number;
  constituents: DemoRegimeConstituent[];
  history: DemoRegimeHistoryPoint[];
  long_quote: DemoRegimeQuote;
  short_quote: DemoRegimeQuote;
}

export interface DemoPerpCurvePoint {
  x: number;
  amm: number;
  anchor: number;
  edge: number;
}

export interface DemoPerpFundingPoint {
  slot: number;
  amm_mu_display: string;
  anchor_mu_display: string;
  kl_display: string;
  funding_rate_display: string;
}

export interface DemoPerpQuote {
  side: string;
  target_mu_display: string;
  target_sigma_display: string;
  collateral_required_display: string;
  fee_paid_display: string;
  total_debit_display: string;
  estimated_funding_display: string;
  close_mark_display: string;
  memo_payload: string;
}

export interface DemoPerpPosition {
  id: string;
  side: string;
  entry_mu_display: string;
  collateral_display: string;
  funding_paid_display: string;
  funding_received_display: string;
  mark_payout_display: string;
  status: string;
}

export interface DemoPerpMarket {
  symbol: string;
  title: string;
  status: string;
  slot: number;
  next_funding_slot: number;
  funding_interval: number;
  mark_price_display: string;
  anchor_mu_display: string;
  anchor_sigma_display: string;
  amm_mu_display: string;
  amm_sigma_display: string;
  kl_display: string;
  spot_funding_rate_display: string;
  vault_cash_display: string;
  lp_nav_display: string;
  available_lp_cash_display: string;
  open_positions: number;
  total_lp_shares_display: string;
  curve_points: DemoPerpCurvePoint[];
  funding_path: DemoPerpFundingPoint[];
  long_quote: DemoPerpQuote;
  short_quote: DemoPerpQuote;
  positions: DemoPerpPosition[];
}

export interface DemoCurvePoint {
  x: number;
  current: number;
  proposed: number;
  edge: number;
}

export interface DemoPreset {
  id: string;
  label: string;
  target_mu_display: string;
  target_sigma_display: string;
  collateral_required_display: string;
  fee_paid_display: string;
  total_debit_display: string;
  max_total_debit_display: string;
  quote_expiry_slot: number;
  serialized_instruction_hex: string;
  curve_points: DemoCurvePoint[];
}

export interface DemoMarket {
  title: string;
  status: string;
  market_id_hex: string;
  state_version: number;
  current_mu_display: string;
  current_sigma_display: string;
  k_display: string;
  backing_display: string;
  taker_fee_bps: number;
  min_taker_fee_display: string;
  maker_fees_earned_display: string;
  maker_deposit_display: string;
  total_trades: number;
  max_open_trades: number;
  expiry_slot: number;
  demo_quote_slot: number;
  demo_quote_expiry_slot: number;
  coarse_samples: number;
  refine_samples: number;
  subtitle?: string;
  category_label?: string;
  unit_label?: string;
  resolves_at_label?: string;
  volume_usd?: number;
  bettor_count?: number;
  resolution_source_label?: string;
  resolution_rule_text?: string;
  source_badge?: string;
  source_url?: string;
  market_slug?: string;
  outcome_label?: string;
  yes_price_display?: string;
  no_price_display?: string;
  best_bid_display?: string;
  best_ask_display?: string;
  spread_display?: string;
  updated_at_millis?: number;
  featured_live?: boolean;
}

export interface DemoSimulationPathPoint {
  slot: number;
  tick: number;
  mu_display: string;
  sigma_display: string;
  volume_display: string;
  fees_display: string;
  reason: string;
}

export interface DemoSimulationTrade {
  id: string;
  slot: number;
  tick: number;
  agent_type: string;
  handle: string;
  action: string;
  target_mu_display: string;
  target_sigma_display: string;
  collateral_display: string;
  fee_display: string;
  total_debit_display: string;
  accepted: boolean;
  reason: string;
}

export interface DemoSimulation {
  running: boolean;
  regime: string;
  scenario: string;
  speed: number;
  tick: number;
  revision: number;
  trade_count: number;
  accepted_count: number;
  current_mu_display: string;
  current_sigma_display: string;
  current_skew_display: string;
  previous_mu_display: string;
  previous_sigma_display: string;
  previous_skew_display: string;
  total_volume_display: string;
  fees_earned_display: string;
  last_error: string | null;
  market_path: DemoSimulationPathPoint[];
  trade_tape: DemoSimulationTrade[];
}

export interface DemoLiveFeed {
  mode: string;
  source: string;
  symbol: string;
  status: string;
  endpoint: string;
  chain: string;
  feed_id?: string;
  last_update_unix_ms: number;
  execution_mode?: string;
  message?: string;
}

export interface DemoPayload {
  market: DemoMarket;
  presets: DemoPreset[];
  quote_grid: DemoPreset[];
  regime_indexes?: DemoRegimeIndex[];
  perps?: DemoPerpMarket | null;
  live_feed?: DemoLiveFeed | null;
  simulation?: DemoSimulation | null;
}

export interface MarketListing {
  id: string;
  title: string;
  subtitle: string;
  category: Exclude<MarketCategory, "All">;
  unit: string;
  resolvesAt: string;
  crowdMu: number;
  crowdSigma: number;
  muMin: number;
  muMax: number;
  sigmaMin: number;
  sigmaMax: number;
  volumeUsd: number;
  bettorCount: number;
  crowdHistory: number[];
  isOnChain: boolean;
  resolutionSource: string;
  resolutionRule: string;
  marketType: MarketType;
  sourceBadge?: string;
  sourceUrl?: string;
  isFeaturedLive: boolean;
  liveEventStats: LiveEventStats | null;
  regime?: DemoRegimeIndex;
  perp?: DemoPerpMarket;
}

export interface ActivityEvent {
  marketId: string;
  anonHandle: string;
  mu: number;
  sigma: number;
  stake: number;
  ageMinutes: number;
}

export interface BetRecord {
  id: string;
  marketId: string;
  marketTitle: string;
  mu: number;
  sigma: number;
  stake: number;
  collateral: number;
  fee: number;
  placedAtMillis: number;
  resolved: boolean;
  realizedOutcome: number | null;
  realizedPnl: number | null;
  txSignatureHex: string | null;
  isOnChain: boolean;
}

export interface SubmitStatus {
  message: string;
  isError?: boolean;
  isWorking?: boolean;
}

export interface ContinuousQuotePreview {
  targetMu: number;
  targetSigma: number;
  collateralRequired: number;
  feePaid: number;
  totalDebit: number;
  maxTotalDebit: number;
  quoteExpirySlot: number;
  serializedInstructionHex: string;
}
