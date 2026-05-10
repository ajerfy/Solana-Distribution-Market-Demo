/** Subset of GET /api/demo-payload aligned with Android `Payload.kt`. Extend as UI grows. */

export interface DemoMarket {
  title: string;
  status: string;
  market_id_hex: string;
  state_version: number;
  current_mu_display: string;
  current_sigma_display: string;
  k_display: string;
  backing_display: string;
  subtitle?: string;
  category_label?: string;
}

export interface DemoPreset {
  id: string;
  label: string;
  target_mu_display: string;
  target_sigma_display: string;
}

export interface DemoPayload {
  market: DemoMarket;
  presets: DemoPreset[];
  quote_grid: DemoPreset[];
}
