use axum::{Json, Router, extract::State, http::StatusCode, response::IntoResponse, routing::get};
use distribution_markets::{
    Fixed, FixedNormalDistribution, fixed_calculate_f, fixed_calculate_lambda,
};
use normal_v1_sdk::{
    TradeQuoteRequestV1, android_trade_intent, build_trade_quote, seeded_demo_market,
};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use std::{
    env, fs,
    net::SocketAddr,
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::{net::TcpListener, sync::RwLock, time::sleep};

const DEFAULT_BIND_ADDR: &str = "0.0.0.0:8787";
const DEFAULT_ASSET_PATH: &str = "apps/android-demo/app/src/main/assets/demo_ufc328.json";
const DEFAULT_HERMES_BASE: &str = "https://hermes.pyth.network";
const DEFAULT_SOL_QUERY: &str = "Crypto.SOL/USD";
const DEFAULT_SOLANA_RPC: &str = "https://api.devnet.solana.com";
const DEFAULT_POLY_GAMMA_BASE: &str = "https://gamma-api.polymarket.com";
const DEFAULT_POLY_CLOB_BASE: &str = "https://clob.polymarket.com";
const DEFAULT_POLY_SLUG: &str = "ufc-sea2-kha7-2026-05-09";
const DEFAULT_POLY_OUTCOME_INDEX: usize = 1;
const DEFAULT_FUNDING_INTERVAL_SLOTS: u64 = 20;
const DEMO_MARKET_ID: [u8; 32] = [4_u8; 32];
const DEMO_QUOTE_EXPIRY_DELTA: u64 = 10;

#[derive(Clone)]
struct AppContext {
    snapshot: Arc<RwLock<BackendSnapshot>>,
}

#[derive(Clone, Debug)]
struct BackendSnapshot {
    payload: Value,
    status: StatusPayload,
}

#[derive(Clone, Debug, Serialize)]
struct StatusPayload {
    mode: String,
    status: String,
    source: String,
    symbol: String,
    endpoint: String,
    chain: String,
    feed_id: Option<String>,
    slot: Option<u64>,
    last_update_unix_ms: Option<u64>,
    execution_mode: Option<String>,
    message: String,
}

#[derive(Clone, Debug)]
struct FeedConfig {
    hermes_base: String,
    symbol_query: String,
    solana_rpc: String,
    feed_id: String,
}

#[derive(Clone, Debug)]
struct PolyConfig {
    gamma_base: String,
    clob_base: String,
    market_slug: String,
    outcome_index: usize,
}

#[derive(Clone, Debug)]
struct OracleSnapshot {
    price: f64,
    confidence: f64,
    ema_price: f64,
    publish_time: u64,
}

#[derive(Clone, Debug)]
struct PerpFundingPoint {
    slot: u64,
    amm_mu: f64,
    anchor_mu: f64,
    kl: f64,
    funding_rate: f64,
}

#[derive(Clone, Debug)]
struct PolyMarketSnapshot {
    title: String,
    slug: String,
    description: String,
    resolution_source: String,
    resolves_at: String,
    probability: f64,
    no_probability: f64,
    sigma: f64,
    outcome_label: String,
    volume_usd: f64,
    liquidity_usd: f64,
    best_bid: Option<f64>,
    best_ask: Option<f64>,
    spread: Option<f64>,
    updated_at_millis: u64,
}

#[derive(Clone, Deserialize)]
struct HermesFeedMetadata {
    id: String,
}

#[derive(Deserialize)]
struct HermesLatestPrice {
    price: HermesPriceComponent,
    ema_price: HermesPriceComponent,
}

#[derive(Deserialize)]
struct HermesPriceComponent {
    price: String,
    conf: String,
    expo: i32,
    publish_time: u64,
}

#[derive(Clone, Deserialize)]
struct PolyGammaMarket {
    question: String,
    slug: String,
    description: String,
    #[serde(rename = "resolutionSource")]
    resolution_source: String,
    #[serde(rename = "endDate")]
    end_date: String,
    #[serde(rename = "volumeNum")]
    volume_num: Option<f64>,
    #[serde(rename = "liquidityNum")]
    liquidity_num: Option<f64>,
    outcomes: String,
    #[serde(rename = "outcomePrices")]
    outcome_prices: String,
    #[serde(rename = "clobTokenIds")]
    clob_token_ids: String,
}

#[derive(Deserialize)]
struct ClobMidpointResponse {
    mid: String,
}

#[derive(Deserialize)]
struct ClobSpreadResponse {
    spread: String,
}

#[derive(Deserialize)]
struct ClobBookResponse {
    bids: Vec<ClobBookLevel>,
    asks: Vec<ClobBookLevel>,
}

#[derive(Deserialize)]
struct ClobBookLevel {
    price: String,
}

#[derive(Deserialize)]
struct RpcSlotResponse {
    result: Option<u64>,
}

#[tokio::main]
async fn main() -> Result<(), String> {
    let bind_addr =
        env::var("PARABOLA_LIVE_BIND").unwrap_or_else(|_| DEFAULT_BIND_ADDR.to_string());
    let asset_path =
        env::var("PARABOLA_BASE_PAYLOAD").unwrap_or_else(|_| DEFAULT_ASSET_PATH.to_string());
    let hermes_base =
        env::var("PARABOLA_HERMES_BASE").unwrap_or_else(|_| DEFAULT_HERMES_BASE.to_string());
    let symbol_query =
        env::var("PARABOLA_PYTH_SYMBOL").unwrap_or_else(|_| DEFAULT_SOL_QUERY.to_string());
    let solana_rpc =
        env::var("PARABOLA_SOLANA_RPC").unwrap_or_else(|_| DEFAULT_SOLANA_RPC.to_string());
    let poly_gamma_base =
        env::var("PARABOLA_POLY_GAMMA_BASE").unwrap_or_else(|_| DEFAULT_POLY_GAMMA_BASE.to_string());
    let poly_clob_base =
        env::var("PARABOLA_POLY_CLOB_BASE").unwrap_or_else(|_| DEFAULT_POLY_CLOB_BASE.to_string());
    let poly_market_slug =
        env::var("PARABOLA_POLY_SLUG").unwrap_or_else(|_| DEFAULT_POLY_SLUG.to_string());
    let poly_outcome_index = env::var("PARABOLA_POLY_OUTCOME_INDEX")
        .ok()
        .and_then(|value| value.parse::<usize>().ok())
        .unwrap_or(DEFAULT_POLY_OUTCOME_INDEX);

    let base_payload_raw = fs::read_to_string(&asset_path)
        .map_err(|error| format!("failed to read {asset_path}: {error}"))?;
    let base_payload: Value = serde_json::from_str(&base_payload_raw)
        .map_err(|error| format!("invalid base payload JSON: {error}"))?;

    let client = Client::builder()
        .user_agent("parabola-live-perp/0.1")
        .timeout(Duration::from_secs(5))
        .build()
        .map_err(|error| format!("failed to build HTTP client: {error}"))?;

    let feed_id = resolve_feed_id(&client, &hermes_base, &symbol_query).await?;
    let feed = FeedConfig {
        hermes_base: hermes_base.clone(),
        symbol_query: symbol_query.clone(),
        solana_rpc: solana_rpc.clone(),
        feed_id: feed_id.clone(),
    };
    let poly = PolyConfig {
        gamma_base: poly_gamma_base.clone(),
        clob_base: poly_clob_base.clone(),
        market_slug: poly_market_slug.clone(),
        outcome_index: poly_outcome_index,
    };

    let initial_status = StatusPayload {
        mode: "connecting".to_string(),
        status: "Connecting".to_string(),
        source: "Polymarket + Pyth Hermes".to_string(),
        symbol: format!("{poly_market_slug} + {symbol_query}"),
        endpoint: format!("{poly_gamma_base} | {hermes_base}"),
        chain: "Solana devnet".to_string(),
        feed_id: Some(feed_id.clone()),
        slot: None,
        last_update_unix_ms: None,
        execution_mode: Some("demo_memo".to_string()),
        message: "Resolving live Polymarket and perp snapshots.".to_string(),
    };
    let snapshot = Arc::new(RwLock::new(BackendSnapshot {
        payload: inject_status(base_payload.clone(), &initial_status),
        status: initial_status,
    }));

    let update_state = snapshot.clone();
    let update_client = client.clone();
    let update_base_payload = base_payload.clone();
    let update_feed = feed.clone();
    let update_poly = poly.clone();
    tokio::spawn(async move {
        let mut history = Vec::<PerpFundingPoint>::new();
        loop {
            match refresh_snapshot(
                &update_client,
                &update_base_payload,
                &update_poly,
                &update_feed,
                &mut history,
            )
            .await
            {
                Ok(next) => {
                    let mut guard = update_state.write().await;
                    *guard = next;
                }
                Err(error) => {
                    let mut guard = update_state.write().await;
                    let error_status = StatusPayload {
                        mode: "degraded".to_string(),
                        status: "Degraded".to_string(),
                        source: "Polymarket + Pyth Hermes".to_string(),
                        symbol: format!("{} + {}", update_poly.market_slug, update_feed.symbol_query),
                        endpoint: format!("{} | {}", update_poly.gamma_base, update_feed.hermes_base),
                        chain: "Solana devnet".to_string(),
                        feed_id: Some(update_feed.feed_id.clone()),
                        slot: guard.status.slot,
                        last_update_unix_ms: guard.status.last_update_unix_ms,
                        execution_mode: Some("demo_memo".to_string()),
                        message: format!("Live update failed: {error}"),
                    };
                    let next_payload = inject_status(guard.payload.clone(), &error_status);
                    guard.status = error_status;
                    guard.payload = next_payload;
                }
            }
            sleep(Duration::from_secs(3)).await;
        }
    });

    let app = Router::new()
        .route("/healthz", get(healthz))
        .route("/api/demo-payload", get(get_payload))
        .route("/api/live/status", get(get_status))
        .with_state(AppContext { snapshot });

    let addr: SocketAddr = bind_addr
        .parse()
        .map_err(|error| format!("invalid bind address {bind_addr}: {error}"))?;
    let listener = TcpListener::bind(addr)
        .await
        .map_err(|error| format!("failed to bind {addr}: {error}"))?;

    println!("Parabola live perp backend listening on http://{addr}");
    println!("Using Polymarket market slug {poly_market_slug}");
    println!("Using Hermes feed {symbol_query} ({feed_id})");

    axum::serve(listener, app)
        .await
        .map_err(|error| format!("server error: {error}"))
}

async fn healthz(State(context): State<AppContext>) -> impl IntoResponse {
    let guard = context.snapshot.read().await;
    (
        StatusCode::OK,
        Json(json!({
            "ok": true,
            "status": guard.status,
        })),
    )
}

async fn get_payload(State(context): State<AppContext>) -> impl IntoResponse {
    let guard = context.snapshot.read().await;
    (StatusCode::OK, Json(guard.payload.clone()))
}

async fn get_status(State(context): State<AppContext>) -> impl IntoResponse {
    let guard = context.snapshot.read().await;
    (StatusCode::OK, Json(json!(guard.status)))
}

async fn refresh_snapshot(
    client: &Client,
    base_payload: &Value,
    poly: &PolyConfig,
    feed: &FeedConfig,
    history: &mut Vec<PerpFundingPoint>,
) -> Result<BackendSnapshot, String> {
    let live_event = fetch_polymarket_snapshot(client, poly).await?;
    let oracle = fetch_latest_price(client, &feed.hermes_base, &feed.feed_id).await?;
    let slot = fetch_solana_slot(client, &feed.solana_rpc)
        .await
        .unwrap_or(oracle.publish_time);

    let live_payload = build_live_payload(base_payload, &live_event, feed, &oracle, slot, history)?;
    let status = StatusPayload {
        mode: "live".to_string(),
        status: "Connected".to_string(),
        source: "Polymarket + Pyth Hermes".to_string(),
        symbol: format!("{} + {}", poly.market_slug, feed.symbol_query),
        endpoint: format!("{} | {}", poly.gamma_base, feed.hermes_base),
        chain: "Solana devnet".to_string(),
        feed_id: Some(feed.feed_id.clone()),
        slot: Some(slot),
        last_update_unix_ms: Some(now_unix_ms()),
        execution_mode: Some("demo_memo".to_string()),
        message: "Featured Polymarket event and SOL perp are live.".to_string(),
    };

    Ok(BackendSnapshot {
        payload: inject_status(live_payload, &status),
        status,
    })
}

fn inject_status(mut payload: Value, status: &StatusPayload) -> Value {
    if let Some(root) = payload.as_object_mut() {
        root.insert(
            "live_feed".to_string(),
            serde_json::to_value(status).unwrap_or_else(|_| json!({})),
        );
    }
    payload
}

async fn resolve_feed_id(
    client: &Client,
    hermes_base: &str,
    symbol_query: &str,
) -> Result<String, String> {
    let response = client
        .get(format!("{hermes_base}/v2/price_feeds"))
        .query(&[("query", symbol_query)])
        .send()
        .await
        .map_err(|error| format!("failed to query Hermes feed metadata: {error}"))?;

    let status = response.status();
    if !status.is_success() {
        let body = response.text().await.unwrap_or_default();
        return Err(format!(
            "Hermes feed metadata query failed with {}: {}",
            status, body
        ));
    }

    let feeds: Vec<HermesFeedMetadata> = response
        .json()
        .await
        .map_err(|error| format!("invalid Hermes feed metadata response: {error}"))?;

    feeds
        .iter()
        .find(|feed| feed.id.starts_with("ef0d8b") || symbol_query == DEFAULT_SOL_QUERY)
        .cloned()
        .or_else(|| feeds.first().cloned())
        .map(|feed| feed.id)
        .ok_or_else(|| format!("no Hermes feed found for query {symbol_query}"))
}

async fn fetch_latest_price(
    client: &Client,
    hermes_base: &str,
    feed_id: &str,
) -> Result<OracleSnapshot, String> {
    let response = client
        .get(format!("{hermes_base}/api/latest_price_feeds"))
        .query(&[("ids[]", format!("0x{feed_id}"))])
        .send()
        .await
        .map_err(|error| format!("failed to fetch Hermes latest price: {error}"))?;

    let status = response.status();
    if !status.is_success() {
        let body = response.text().await.unwrap_or_default();
        return Err(format!(
            "Hermes latest price request failed with {}: {}",
            status, body
        ));
    }

    let prices: Vec<HermesLatestPrice> = response
        .json()
        .await
        .map_err(|error| format!("invalid Hermes latest price response: {error}"))?;
    let latest = prices
        .into_iter()
        .next()
        .ok_or_else(|| "Hermes returned no price data".to_string())?;

    Ok(OracleSnapshot {
        price: scaled_component_to_f64(&latest.price)?,
        confidence: scaled_confidence_to_f64(&latest.price)?,
        ema_price: scaled_component_to_f64(&latest.ema_price)?,
        publish_time: latest.price.publish_time,
    })
}

async fn fetch_solana_slot(client: &Client, rpc_url: &str) -> Result<u64, String> {
    let response = client
        .post(rpc_url)
        .json(&json!({
            "jsonrpc": "2.0",
            "id": 1,
            "method": "getSlot",
            "params": [{"commitment": "processed"}]
        }))
        .send()
        .await
        .map_err(|error| format!("failed to fetch Solana slot: {error}"))?;

    if !response.status().is_success() {
        return Err(format!("slot RPC returned {}", response.status()));
    }

    let body: RpcSlotResponse = response
        .json()
        .await
        .map_err(|error| format!("invalid slot RPC response: {error}"))?;
    body.result
        .ok_or_else(|| "slot RPC returned no result".to_string())
}

async fn fetch_polymarket_snapshot(
    client: &Client,
    config: &PolyConfig,
) -> Result<PolyMarketSnapshot, String> {
    let response = client
        .get(format!("{}/markets", config.gamma_base))
        .query(&[("slug", config.market_slug.as_str())])
        .send()
        .await
        .map_err(|error| format!("failed to fetch Polymarket market: {error}"))?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("Polymarket Gamma returned {status}: {body}"));
    }

    let markets: Vec<PolyGammaMarket> = response
        .json()
        .await
        .map_err(|error| format!("invalid Polymarket Gamma response: {error}"))?;
    let market = markets
        .into_iter()
        .next()
        .ok_or_else(|| format!("no Polymarket market found for slug {}", config.market_slug))?;

    let outcomes = parse_embedded_string_array(&market.outcomes)?;
    let prices = parse_embedded_number_array(&market.outcome_prices)?;
    let token_ids = parse_embedded_string_array(&market.clob_token_ids)?;
    if config.outcome_index >= outcomes.len()
        || config.outcome_index >= prices.len()
        || config.outcome_index >= token_ids.len()
    {
        return Err(format!(
            "Polymarket outcome index {} is out of bounds for slug {}",
            config.outcome_index, config.market_slug
        ));
    }

    let token_id = &token_ids[config.outcome_index];
    let midpoint = fetch_clob_midpoint(client, &config.clob_base, token_id).await?;
    let spread = fetch_clob_spread(client, &config.clob_base, token_id).await.ok();
    let (best_bid, best_ask) = fetch_clob_best_bid_ask(client, &config.clob_base, token_id).await?;
    let probability = midpoint.unwrap_or(prices[config.outcome_index]).clamp(0.0, 1.0);
    let no_probability = (1.0 - probability).clamp(0.0, 1.0);
    let spread_value = spread.or_else(|| match (best_bid, best_ask) {
        (Some(bid), Some(ask)) => Some((ask - bid).max(0.0)),
        _ => None,
    });
    let sigma = estimate_probability_sigma(probability, spread_value.unwrap_or(0.02));

    Ok(PolyMarketSnapshot {
        title: market.question,
        slug: market.slug,
        description: market.description,
        resolution_source: market.resolution_source,
        resolves_at: market.end_date,
        probability,
        no_probability,
        sigma,
        outcome_label: outcomes[config.outcome_index].clone(),
        volume_usd: market.volume_num.unwrap_or_default(),
        liquidity_usd: market.liquidity_num.unwrap_or_default(),
        best_bid,
        best_ask,
        spread: spread_value,
        updated_at_millis: now_unix_ms(),
    })
}

async fn fetch_clob_midpoint(
    client: &Client,
    clob_base: &str,
    token_id: &str,
) -> Result<Option<f64>, String> {
    let response = client
        .get(format!("{clob_base}/midpoint"))
        .query(&[("token_id", token_id)])
        .send()
        .await
        .map_err(|error| format!("failed to fetch Polymarket midpoint: {error}"))?;
    if !response.status().is_success() {
        return Ok(None);
    }
    let body: ClobMidpointResponse = response
        .json()
        .await
        .map_err(|error| format!("invalid Polymarket midpoint response: {error}"))?;
    Ok(body.mid.parse::<f64>().ok())
}

async fn fetch_clob_spread(
    client: &Client,
    clob_base: &str,
    token_id: &str,
) -> Result<f64, String> {
    let response = client
        .get(format!("{clob_base}/spread"))
        .query(&[("token_id", token_id)])
        .send()
        .await
        .map_err(|error| format!("failed to fetch Polymarket spread: {error}"))?;
    if !response.status().is_success() {
        return Err(format!("Polymarket spread returned {}", response.status()));
    }
    let body: ClobSpreadResponse = response
        .json()
        .await
        .map_err(|error| format!("invalid Polymarket spread response: {error}"))?;
    body.spread
        .parse::<f64>()
        .map_err(|error| format!("invalid Polymarket spread {}: {error}", body.spread))
}

async fn fetch_clob_best_bid_ask(
    client: &Client,
    clob_base: &str,
    token_id: &str,
) -> Result<(Option<f64>, Option<f64>), String> {
    let response = client
        .get(format!("{clob_base}/book"))
        .query(&[("token_id", token_id)])
        .send()
        .await
        .map_err(|error| format!("failed to fetch Polymarket order book: {error}"))?;
    if !response.status().is_success() {
        return Ok((None, None));
    }
    let book: ClobBookResponse = response
        .json()
        .await
        .map_err(|error| format!("invalid Polymarket order book response: {error}"))?;
    let best_bid = book
        .bids
        .last()
        .and_then(|level| level.price.parse::<f64>().ok());
    let best_ask = book
        .asks
        .last()
        .and_then(|level| level.price.parse::<f64>().ok());
    Ok((best_bid, best_ask))
}

fn parse_embedded_string_array(raw: &str) -> Result<Vec<String>, String> {
    serde_json::from_str::<Vec<String>>(raw)
        .map_err(|error| format!("invalid embedded JSON string array {raw}: {error}"))
}

fn parse_embedded_number_array(raw: &str) -> Result<Vec<f64>, String> {
    let values: Vec<String> = parse_embedded_string_array(raw)?;
    values
        .into_iter()
        .map(|value| {
            value
                .parse::<f64>()
                .map_err(|error| format!("invalid embedded numeric value {value}: {error}"))
        })
        .collect()
}

fn estimate_probability_sigma(probability: f64, spread: f64) -> f64 {
    let center_weight = 0.5 - (probability - 0.5).abs();
    (8.0 + center_weight * 8.0 + spread * 125.0).clamp(4.0, 18.0)
}

fn build_live_payload(
    base_payload: &Value,
    live_event: &PolyMarketSnapshot,
    feed: &FeedConfig,
    oracle: &OracleSnapshot,
    slot: u64,
    history: &mut Vec<PerpFundingPoint>,
) -> Result<Value, String> {
    let mut payload = base_payload.clone();
    let market_id_hex = payload
        .get("market")
        .and_then(|market| market.get("market_id_hex"))
        .and_then(Value::as_str)
        .unwrap_or("fefefefefefefefefefefefefefefefefefefefefefefefefefefefefefefefe");
    let state_version = payload
        .get("market")
        .and_then(|market| market.get("state_version"))
        .and_then(Value::as_u64)
        .unwrap_or(0);
    let funding_interval = DEFAULT_FUNDING_INTERVAL_SLOTS;
    let anchor_mu = oracle.price;
    let amm_mu = oracle.ema_price;
    let anchor_sigma = (oracle.confidence * 12.0)
        .max(anchor_mu.abs() * 0.035)
        .clamp(4.0, 100.0);
    let amm_sigma = (anchor_sigma * 1.1).clamp(4.0, 120.0);
    let kl = normal_kl(amm_mu, amm_sigma, anchor_mu, anchor_sigma);
    let funding_rate = demo_perp_funding_rate(kl, funding_interval);

    history.push(PerpFundingPoint {
        slot,
        amm_mu,
        anchor_mu,
        kl,
        funding_rate,
    });
    if history.len() > 24 {
        let drop_count = history.len() - 24;
        history.drain(0..drop_count);
    }

    let event_mu = live_event.probability * 100.0;
    let event_sigma = live_event.sigma;
    let mut event_program = seeded_demo_market()?;
    set_program_distribution(&mut event_program, event_mu, event_sigma)?;
    let event_presets =
        build_live_event_presets(&event_program, live_event, slot)?;
    let event_quote_grid = build_live_event_quote_grid(&event_program, live_event, slot)?;

    let mut program = seeded_demo_market()?;
    set_program_distribution(&mut program, amm_mu, amm_sigma)?;

    let long_target_mu = anchor_mu + anchor_sigma * 0.75;
    let short_target_mu = anchor_mu - anchor_sigma * 0.75;
    let long_target_sigma = (anchor_sigma * 1.1).clamp(0.5, 120.0);
    let short_target_sigma = (anchor_sigma * 1.2).clamp(0.5, 120.0);

    let long_quote = build_live_quote(
        &program,
        "Long",
        long_target_mu,
        long_target_sigma,
        anchor_mu,
        anchor_sigma,
        slot,
        funding_interval,
    )?;
    let short_quote = build_live_quote(
        &program,
        "Short",
        short_target_mu,
        short_target_sigma,
        anchor_mu,
        anchor_sigma,
        slot,
        funding_interval,
    )?;

    let fees_estimate = long_quote["fee_paid_display"]
        .as_str()
        .and_then(|value| value.parse::<f64>().ok())
        .unwrap_or(0.0)
        + short_quote["fee_paid_display"]
            .as_str()
            .and_then(|value| value.parse::<f64>().ok())
            .unwrap_or(0.0);
    let vault_cash = 50.0 + fees_estimate;
    let available_lp_cash = vault_cash.max(0.0);

    let live_perp = json!({
        "symbol": "SOL-PERP",
        "title": "Perpetual SOL distribution market",
        "status": "Live oracle mode",
        "slot": slot,
        "next_funding_slot": slot + funding_interval,
        "funding_interval": funding_interval,
        "mark_price_display": format9(amm_mu),
        "anchor_mu_display": format9(anchor_mu),
        "anchor_sigma_display": format9(anchor_sigma),
        "amm_mu_display": format9(amm_mu),
        "amm_sigma_display": format9(amm_sigma),
        "kl_display": format9(kl),
        "spot_funding_rate_display": format9(funding_rate),
        "vault_cash_display": format9(vault_cash),
        "lp_nav_display": format9(available_lp_cash),
        "available_lp_cash_display": format9(available_lp_cash),
        "open_positions": 0,
        "total_lp_shares_display": format9(50.0),
        "curve_points": build_curve_points(amm_mu, amm_sigma, anchor_mu, anchor_sigma)?,
        "funding_path": history.iter().map(|point| json!({
            "slot": point.slot,
            "amm_mu_display": format9(point.amm_mu),
            "anchor_mu_display": format9(point.anchor_mu),
            "kl_display": format9(point.kl),
            "funding_rate_display": format9(point.funding_rate),
        })).collect::<Vec<_>>(),
        "long_quote": long_quote,
        "short_quote": short_quote,
        "positions": [],
    });

    let live_market = json!({
        "title": live_event.title,
        "status": if live_event.spread.unwrap_or(0.0) <= 0.02 { "Live consensus" } else { "Live market" },
        "market_id_hex": market_id_hex,
        "state_version": state_version,
        "current_mu_display": format9(event_mu),
        "current_sigma_display": format9(event_sigma),
        "k_display": "2.105026040",
        "backing_display": format9(live_event.liquidity_usd.max(50.0)),
        "taker_fee_bps": 100,
        "min_taker_fee_display": "0.001000000",
        "maker_fees_earned_display": format9((live_event.volume_usd * 0.0005).max(0.0)),
        "maker_deposit_display": format9(live_event.liquidity_usd.max(50.0)),
        "total_trades": 0,
        "max_open_trades": 64,
        "expiry_slot": 1_000_000,
        "demo_quote_slot": slot,
        "demo_quote_expiry_slot": slot + DEMO_QUOTE_EXPIRY_DELTA,
        "coarse_samples": 4096,
        "refine_samples": 4096,
        "subtitle": "Live Polymarket consensus remapped into a Parabola probability curve",
        "category_label": "Sports",
        "unit_label": "%",
        "resolves_at_label": live_event.resolves_at,
        "volume_usd": live_event.volume_usd,
        "bettor_count": ((live_event.volume_usd / 1200.0).round() as i64).max(120),
        "resolution_source_label": if live_event.resolution_source.is_empty() { "Polymarket" } else { &live_event.resolution_source },
        "resolution_rule_text": format!(
            "Parabola tracks the live Polymarket price for '{}'. You still trade a Normal curve around that probability, not a direct YES/NO order. Source context: {}",
            live_event.outcome_label,
            live_event.description
        ),
        "source_badge": "POLYMARKET",
        "source_url": format!("https://polymarket.com/event/{}", live_event.slug),
        "market_slug": live_event.slug,
        "outcome_label": live_event.outcome_label,
        "yes_price_display": format9(live_event.probability),
        "no_price_display": format9(live_event.no_probability),
        "best_bid_display": live_event.best_bid.map(format9),
        "best_ask_display": live_event.best_ask.map(format9),
        "spread_display": live_event.spread.map(format9),
        "updated_at_millis": live_event.updated_at_millis,
        "featured_live": true
    });

    if let Some(root) = payload.as_object_mut() {
        root.insert("market".to_string(), live_market);
        root.insert("presets".to_string(), Value::Array(event_presets));
        root.insert("quote_grid".to_string(), Value::Array(event_quote_grid));
        root.insert("perps".to_string(), live_perp);
        root.insert(
            "live_feed".to_string(),
            json!({
                "mode": "live",
                "source": "Polymarket + Pyth Hermes",
                "symbol": format!("{} + {}", live_event.slug, feed.symbol_query),
                "status": "Connected",
                "endpoint": format!("https://polymarket.com/event/{} | {}", live_event.slug, feed.hermes_base),
                "feed_id": feed.feed_id,
                "chain": "Solana devnet",
                "last_update_unix_ms": now_unix_ms(),
                "execution_mode": "demo_memo",
                "message": "Featured Polymarket event and live SOL perp are streaming. Execution remains memo-based."
            }),
        );
    }

    Ok(payload)
}

fn build_live_event_presets(
    program: &normal_v1_program::NormalV1Program,
    live_event: &PolyMarketSnapshot,
    slot: u64,
) -> Result<Vec<Value>, String> {
    let center_mu = (live_event.probability * 100.0).clamp(1.0, 99.0);
    let base_sigma = live_event.sigma.clamp(4.0, 18.0);
    let favorite_mu = clamp_probability_percent(center_mu + 4.0);
    let hedge_mu = clamp_probability_percent(center_mu - 5.0);
    let tight_sigma = (base_sigma * 0.8).clamp(4.0, 18.0);
    let wide_sigma = (base_sigma * 1.3).clamp(4.0, 18.0);
    let candidates = [
        (
            format!("poly-consensus-{center_mu:.0}"),
            format!("Live consensus ({center_mu:.0}%)"),
            center_mu,
            base_sigma,
        ),
        (
            format!("poly-favorite-{favorite_mu:.0}"),
            format!("Favorite extends to {favorite_mu:.0}%"),
            favorite_mu,
            tight_sigma,
        ),
        (
            format!("poly-hedge-{hedge_mu:.0}"),
            format!("Odds compress to {hedge_mu:.0}%"),
            hedge_mu,
            wide_sigma,
        ),
        (
            format!("poly-tight-{center_mu:.0}"),
            format!("High conviction at {center_mu:.0}%"),
            center_mu,
            tight_sigma,
        ),
        (
            format!("poly-wide-{center_mu:.0}"),
            format!("Wider range at {center_mu:.0}%"),
            center_mu,
            wide_sigma,
        ),
    ];

    let mut presets = Vec::new();
    for (id, label, mu, sigma) in candidates {
        if let Ok(preset) = build_live_event_quote_preset(program, &id, &label, mu, sigma, slot) {
            presets.push(preset);
        }
    }

    if presets.is_empty() {
        Err("failed to build any live Polymarket presets".to_string())
    } else {
        Ok(presets)
    }
}

fn build_live_event_quote_grid(
    program: &normal_v1_program::NormalV1Program,
    live_event: &PolyMarketSnapshot,
    slot: u64,
) -> Result<Vec<Value>, String> {
    let center_mu = live_event.probability * 100.0;
    let base_sigma = live_event.sigma;
    let mu_offsets = [-8.0, -4.0, 0.0, 4.0, 8.0];
    let sigma_offsets = [-2.0, 0.0, 2.0];
    let mut grid = Vec::new();

    for sigma_offset in sigma_offsets {
        let sigma = (base_sigma + sigma_offset).clamp(4.0, 18.0);
        for mu_offset in mu_offsets {
            let mu = clamp_probability_percent(center_mu + mu_offset);
            let id = format!("grid-{mu:.1}-{sigma:.1}");
            let label = format!("Grid quote for mu {mu:.1}, sigma {sigma:.1}");
            if let Ok(quote) = build_live_event_quote_preset(program, &id, &label, mu, sigma, slot) {
                grid.push(quote);
            }
        }
    }

    if grid.is_empty() {
        Err("failed to build any live Polymarket quote grid entries".to_string())
    } else {
        Ok(grid)
    }
}

fn build_live_event_quote_preset(
    program: &normal_v1_program::NormalV1Program,
    id: &str,
    label: &str,
    mu: f64,
    sigma: f64,
    slot: u64,
) -> Result<Value, String> {
    let target_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(mu)?, Fixed::from_f64(sigma)?)?;
    let quote = build_trade_quote(
        program,
        TradeQuoteRequestV1 {
            trader: [8_u8; 32],
            market: DEMO_MARKET_ID,
            target_distribution,
            quote_slot: slot,
            quote_expiry_slot: slot + DEMO_QUOTE_EXPIRY_DELTA,
        },
    )?;
    let intent = android_trade_intent(&quote);
    Ok(json!({
        "id": id,
        "label": label,
        "target_mu_display": intent.mu_display,
        "target_sigma_display": intent.sigma_display,
        "collateral_required_display": intent.collateral_required_display,
        "fee_paid_display": intent.fee_paid_display,
        "total_debit_display": intent.total_debit_display,
        "max_total_debit_display": quote.quote.max_total_debit.to_string(),
        "quote_expiry_slot": intent.quote_expiry_slot,
        "serialized_instruction_hex": intent.serialized_instruction_hex,
        "curve_points": build_live_event_curve_points(program, target_distribution)?,
    }))
}

fn build_live_event_curve_points(
    program: &normal_v1_program::NormalV1Program,
    target_distribution: FixedNormalDistribution,
) -> Result<Vec<Value>, String> {
    let current = program.state.market_account.current_distribution;
    let k = program.state.market_account.k;
    let current_mu = current.mu.to_f64();
    let target_mu = target_distribution.mu.to_f64();
    let current_sigma = current.sigma.to_f64();
    let target_sigma = target_distribution.sigma.to_f64();
    let lower = current_mu.min(target_mu) - current_sigma.max(target_sigma) * 3.0;
    let upper = current_mu.max(target_mu) + current_sigma.max(target_sigma) * 3.0;
    let samples = 48_usize;
    let mut points = Vec::with_capacity(samples + 1);

    for step in 0..=samples {
        let x = lower + (upper - lower) * step as f64 / samples as f64;
        let x_fixed = Fixed::from_f64(x)?;
        let current_value = fixed_calculate_f(x_fixed, current, k)?.to_f64();
        let proposed_value = fixed_calculate_f(x_fixed, target_distribution, k)?.to_f64();
        points.push(json!({
            "x": format9(x),
            "current": format9(current_value),
            "proposed": format9(proposed_value),
            "edge": format9(proposed_value - current_value),
        }));
    }

    Ok(points)
}

fn clamp_probability_percent(value: f64) -> f64 {
    value.clamp(1.0, 99.0)
}

fn set_program_distribution(
    program: &mut normal_v1_program::NormalV1Program,
    mu: f64,
    sigma: f64,
) -> Result<(), String> {
    let distribution = FixedNormalDistribution::new(Fixed::from_f64(mu)?, Fixed::from_f64(sigma)?)?;
    let lambda = fixed_calculate_lambda(distribution.sigma, program.state.market_account.k)?;
    program.state.core_market.current_distribution = distribution;
    program.state.core_market.current_lambda = lambda;
    program.state.market_account.current_distribution = distribution;
    program.state.market_account.current_lambda = lambda;
    Ok(())
}

fn build_live_quote(
    program: &normal_v1_program::NormalV1Program,
    side: &str,
    target_mu: f64,
    target_sigma: f64,
    anchor_mu: f64,
    anchor_sigma: f64,
    slot: u64,
    funding_interval: u64,
) -> Result<Value, String> {
    let current = program.state.market_account.current_distribution;
    let current_mu = current.mu.to_f64();
    let current_sigma = current.sigma.to_f64();
    let sigma_floor = current_sigma.max(anchor_sigma).max(4.0);
    let direction = if target_mu >= current_mu { 1.0 } else { -1.0 };
    let desired_shift = (target_mu - current_mu).abs().max(sigma_floor * 0.04);
    let shift_scales = [1.0, 0.7, 0.5, 0.35, 0.2, 0.1];
    let sigma_scales = [1.0, 1.2, 1.5, 1.8, 2.2, 2.8];
    let mut last_error = String::new();

    for sigma_scale in sigma_scales {
        let candidate_sigma =
            (target_sigma.max(sigma_floor) * sigma_scale).clamp(sigma_floor, 120.0);
        for shift_scale in shift_scales {
            let candidate_mu = current_mu + direction * desired_shift * shift_scale;
            match build_live_quote_candidate(
                program,
                side,
                candidate_mu,
                candidate_sigma,
                anchor_mu,
                anchor_sigma,
                slot,
                funding_interval,
            ) {
                Ok(quote) => return Ok(quote),
                Err(error) => last_error = error,
            }
        }
    }

    Err(format!(
        "unable to fit live perp quote within market caps: {last_error}"
    ))
}

fn build_live_quote_candidate(
    program: &normal_v1_program::NormalV1Program,
    side: &str,
    target_mu: f64,
    target_sigma: f64,
    anchor_mu: f64,
    anchor_sigma: f64,
    slot: u64,
    funding_interval: u64,
) -> Result<Value, String> {
    let target_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(target_mu)?, Fixed::from_f64(target_sigma)?)?;
    let envelope = build_trade_quote(
        program,
        TradeQuoteRequestV1 {
            trader: [8_u8; 32],
            market: DEMO_MARKET_ID,
            target_distribution,
            quote_slot: slot,
            quote_expiry_slot: slot + DEMO_QUOTE_EXPIRY_DELTA,
        },
    )?;
    let intent = android_trade_intent(&envelope);
    let current = program.state.market_account.current_distribution;
    let old_kl = normal_kl(
        current.mu.to_f64(),
        current.sigma.to_f64(),
        anchor_mu,
        anchor_sigma,
    );
    let new_kl = normal_kl(target_mu, target_sigma, anchor_mu, anchor_sigma);
    let funding_rate = demo_perp_funding_rate(new_kl, funding_interval);
    let collateral = envelope.collateral_required.to_f64();
    let estimated_funding = (new_kl - old_kl) * funding_rate * funding_interval as f64 * collateral;
    let _mark_payout = demo_perp_mark_payout(
        program,
        target_mu,
        target_sigma,
        anchor_mu,
        estimated_funding.max(0.0),
        collateral,
    )?;

    Ok(json!({
        "side": side,
        "target_mu_display": intent.mu_display,
        "target_sigma_display": intent.sigma_display,
        "collateral_required_display": intent.collateral_required_display,
        "fee_paid_display": intent.fee_paid_display,
        "total_debit_display": intent.total_debit_display,
        "estimated_funding_display": format9(estimated_funding),
        "close_mark_display": format9(anchor_mu),
        "memo_payload": format!(
            "perp-live-demo|side={}|target_mu={}|target_sigma={}|collateral={}|fee={}|total_debit={}|est_funding={}|oracle_live=true",
            side.to_ascii_lowercase(),
            intent.mu_display,
            intent.sigma_display,
            intent.collateral_required_display,
            intent.fee_paid_display,
            intent.total_debit_display,
            format9(estimated_funding),
        ),
    }))
}

fn build_curve_points(
    amm_mu: f64,
    amm_sigma: f64,
    anchor_mu: f64,
    anchor_sigma: f64,
) -> Result<Vec<Value>, String> {
    let amm_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(amm_mu)?, Fixed::from_f64(amm_sigma)?)?;
    let anchor_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(anchor_mu)?, Fixed::from_f64(anchor_sigma)?)?;
    let k = Fixed::from_f64(21.05026039569057)?;
    let lower = amm_mu.min(anchor_mu) - amm_sigma.max(anchor_sigma) * 4.0;
    let upper = amm_mu.max(anchor_mu) + amm_sigma.max(anchor_sigma) * 4.0;
    let samples = 56_usize;
    let mut points = Vec::with_capacity(samples + 1);

    for step in 0..=samples {
        let x = lower + (upper - lower) * step as f64 / samples as f64;
        let x_fixed = Fixed::from_f64(x)?;
        let amm_value = fixed_calculate_f(x_fixed, amm_distribution, k)?.to_f64();
        let anchor_value = fixed_calculate_f(x_fixed, anchor_distribution, k)?.to_f64();
        points.push(json!({
            "x": format9(x),
            "amm": format9(amm_value),
            "anchor": format9(anchor_value),
            "edge": format9(amm_value - anchor_value),
        }));
    }

    Ok(points)
}

fn demo_perp_mark_payout(
    program: &normal_v1_program::NormalV1Program,
    target_mu: f64,
    target_sigma: f64,
    mark: f64,
    funding_paid: f64,
    collateral: f64,
) -> Result<f64, String> {
    let current = program.state.market_account.current_distribution;
    let k = program.state.market_account.k;
    let target_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(target_mu)?, Fixed::from_f64(target_sigma)?)?;
    let mark_fixed = Fixed::from_f64(mark)?;
    let old_value = fixed_calculate_f(mark_fixed, current, k)?.to_f64();
    let new_value = fixed_calculate_f(mark_fixed, target_distribution, k)?.to_f64();
    Ok((collateral + (new_value - old_value) - funding_paid).max(0.0))
}

fn normal_kl(mu_p: f64, sigma_p: f64, mu_q: f64, sigma_q: f64) -> f64 {
    (sigma_q / sigma_p).ln()
        + (sigma_p * sigma_p + (mu_p - mu_q) * (mu_p - mu_q)) / (2.0 * sigma_q * sigma_q)
        - 0.5
}

fn demo_perp_funding_rate(kl: f64, funding_interval: u64) -> f64 {
    let kl_clamped = kl.clamp(0.0, 5.0);
    if kl_clamped < 0.005 {
        0.0
    } else {
        200.0 / 10_000.0 * kl_clamped / funding_interval as f64
    }
}

fn scaled_component_to_f64(component: &HermesPriceComponent) -> Result<f64, String> {
    let price = component
        .price
        .parse::<f64>()
        .map_err(|error| format!("invalid scaled price {}: {error}", component.price))?;
    Ok(price * 10_f64.powi(component.expo))
}

fn scaled_confidence_to_f64(component: &HermesPriceComponent) -> Result<f64, String> {
    let conf = component
        .conf
        .parse::<f64>()
        .map_err(|error| format!("invalid scaled confidence {}: {error}", component.conf))?;
    Ok(conf * 10_f64.powi(component.expo))
}

fn format9(value: f64) -> String {
    format!("{value:.9}")
}

fn now_unix_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_else(|_| Duration::from_secs(0))
        .as_millis() as u64
}
