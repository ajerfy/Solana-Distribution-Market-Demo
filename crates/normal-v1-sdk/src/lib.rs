use distribution_markets::{
    Fixed, FixedNormalDistribution, FixedNormalLiquiditySnapshot, FixedNormalQuoteTrace,
    FixedNormalRiskGridPoint, FixedNormalSettlementWaterfallPreview, FixedNormalTradeQuote,
    InitializeAccountsV1, OracleConfigV1, QuoteEnvelopeV1, RegimeConstituent,
    RegimeConstituentSide, RegimeConstituentStatus, RegimeIndex, RegimeIndexSnapshot,
    RegimeTokenSide, RegimeTradeQuote, SolanaInstructionV1, TradeArgsV1, fixed_calculate_f,
    quote_regime_trade,
};
use normal_v1_program::{
    NormalV1Program, ProgramInitializeArgsV1, ProgramTokenOperationV1, pack_instruction,
};

const DEMO_MARKET_ID: [u8; 32] = [4_u8; 32];
const DEMO_QUOTE_SLOT: u64 = 2;
const DEMO_QUOTE_EXPIRY_SLOT: u64 = 12;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct HackathonMarketSeed {
    pub initializer: [u8; 32],
    pub collateral_mint: [u8; 32],
    pub collateral_vault: [u8; 32],
    pub lp_mint: [u8; 32],
    pub market_authority: [u8; 32],
    pub oracle_program: [u8; 32],
    pub oracle_feed: [u8; 32],
    pub oracle_authority: [u8; 32],
    pub initial_b: Fixed,
    pub initial_k: Fixed,
    pub initial_distribution: FixedNormalDistribution,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct TradeQuoteRequestV1 {
    pub trader: [u8; 32],
    pub market: [u8; 32],
    pub target_distribution: FixedNormalDistribution,
    pub quote_slot: u64,
    pub quote_expiry_slot: u64,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct TraderQuoteEnvelopeV1 {
    pub trader: [u8; 32],
    pub quote: FixedNormalTradeQuote,
    pub serialized_instruction: Vec<u8>,
    pub collateral_required: Fixed,
    pub fee_paid: Fixed,
    pub total_debit: Fixed,
    pub quote_expiry_slot: u64,
    pub target_distribution: FixedNormalDistribution,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AndroidTradeIntentV1 {
    pub serialized_instruction_hex: String,
    pub collateral_required_display: String,
    pub fee_paid_display: String,
    pub total_debit_display: String,
    pub quote_expiry_slot: u64,
    pub mu_display: String,
    pub sigma_display: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoMarketSnapshotV1 {
    pub title: String,
    pub status: String,
    pub market_id_hex: String,
    pub state_version: u64,
    pub current_mu_display: String,
    pub current_sigma_display: String,
    pub k_display: String,
    pub backing_display: String,
    pub taker_fee_bps: u32,
    pub min_taker_fee_display: String,
    pub maker_fees_earned_display: String,
    pub maker_deposit_display: String,
    pub total_trades: u64,
    pub max_open_trades: u64,
    pub expiry_slot: u64,
    pub demo_quote_slot: u64,
    pub demo_quote_expiry_slot: u64,
    pub coarse_samples: u32,
    pub refine_samples: u32,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoCurvePointV1 {
    pub x: String,
    pub current: String,
    pub proposed: String,
    pub edge: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoQuotePresetV1 {
    pub id: String,
    pub label: String,
    pub target_mu_display: String,
    pub target_sigma_display: String,
    pub collateral_required_display: String,
    pub fee_paid_display: String,
    pub total_debit_display: String,
    pub max_total_debit_display: String,
    pub quote_expiry_slot: u64,
    pub serialized_instruction_hex: String,
    pub curve_points: Vec<DemoCurvePointV1>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoLiquiditySnapshotV1 {
    pub maker_deposit_display: String,
    pub vault_cash_display: String,
    pub accrued_fees_display: String,
    pub current_k_display: String,
    pub total_lp_shares_display: String,
    pub locked_trader_collateral_display: String,
    pub worst_case_trader_liability_display: String,
    pub available_maker_buffer_display: String,
    pub open_trades: u64,
    pub max_open_trades: u64,
    pub lp_controls_status: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoQuoteTraceV1 {
    pub market_version: u64,
    pub old_mu_display: String,
    pub old_sigma_display: String,
    pub new_mu_display: String,
    pub new_sigma_display: String,
    pub k_display: String,
    pub search_lower_bound_display: String,
    pub search_upper_bound_display: String,
    pub max_loss_outcome_display: String,
    pub max_directional_loss_display: String,
    pub collateral_required_display: String,
    pub fee_paid_display: String,
    pub total_debit_display: String,
    pub vault_cash_before_display: String,
    pub vault_cash_after_display: String,
    pub locked_collateral_before_display: String,
    pub locked_collateral_after_display: String,
    pub worst_case_liability_before_display: String,
    pub worst_case_liability_after_display: String,
    pub maker_buffer_before_display: String,
    pub maker_buffer_after_display: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoRiskGridPointV1 {
    pub outcome_display: String,
    pub trader_liability_display: String,
    pub lp_residual_after_traders_display: String,
    pub maker_buffer_display: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoSettlementWaterfallV1 {
    pub outcome_display: String,
    pub trader_claims_display: String,
    pub lp_residual_claim_display: String,
    pub protocol_dust_display: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoRegimeConstituentV1 {
    pub id: String,
    pub label: String,
    pub side: String,
    pub weight_bps: u32,
    pub probability_display: String,
    pub previous_probability_display: String,
    pub level_contribution_display: String,
    pub signed_pressure_display: String,
    pub status: String,
    pub expiry_slot: u64,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoRegimeHistoryPointV1 {
    pub slot: u64,
    pub level_display: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoRegimeQuoteV1 {
    pub side: String,
    pub size_display: String,
    pub entry_level_display: String,
    pub token_price_display: String,
    pub collateral_required_display: String,
    pub fee_paid_display: String,
    pub total_debit_display: String,
    pub memo_payload: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoRegimeIndexV1 {
    pub id: String,
    pub symbol: String,
    pub title: String,
    pub thesis: String,
    pub status: String,
    pub level_display: String,
    pub previous_level_display: String,
    pub change_display: String,
    pub rebalance_slot: u64,
    pub next_rebalance_slot: u64,
    pub quote_expiry_slot: u64,
    pub constituents: Vec<DemoRegimeConstituentV1>,
    pub history: Vec<DemoRegimeHistoryPointV1>,
    pub long_quote: DemoRegimeQuoteV1,
    pub short_quote: DemoRegimeQuoteV1,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoPerpCurvePointV1 {
    pub x: String,
    pub amm: String,
    pub anchor: String,
    pub edge: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoPerpFundingPointV1 {
    pub slot: u64,
    pub amm_mu_display: String,
    pub anchor_mu_display: String,
    pub kl_display: String,
    pub funding_rate_display: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoPerpQuoteV1 {
    pub side: String,
    pub target_mu_display: String,
    pub target_sigma_display: String,
    pub collateral_required_display: String,
    pub fee_paid_display: String,
    pub total_debit_display: String,
    pub estimated_funding_display: String,
    pub close_mark_display: String,
    pub memo_payload: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoPerpPositionV1 {
    pub id: String,
    pub side: String,
    pub entry_mu_display: String,
    pub collateral_display: String,
    pub funding_paid_display: String,
    pub funding_received_display: String,
    pub mark_payout_display: String,
    pub status: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoPerpMarketV1 {
    pub symbol: String,
    pub title: String,
    pub status: String,
    pub slot: u64,
    pub next_funding_slot: u64,
    pub funding_interval: u64,
    pub mark_price_display: String,
    pub anchor_mu_display: String,
    pub anchor_sigma_display: String,
    pub amm_mu_display: String,
    pub amm_sigma_display: String,
    pub kl_display: String,
    pub spot_funding_rate_display: String,
    pub vault_cash_display: String,
    pub lp_nav_display: String,
    pub available_lp_cash_display: String,
    pub open_positions: u64,
    pub total_lp_shares_display: String,
    pub curve_points: Vec<DemoPerpCurvePointV1>,
    pub funding_path: Vec<DemoPerpFundingPointV1>,
    pub long_quote: DemoPerpQuoteV1,
    pub short_quote: DemoPerpQuoteV1,
    pub positions: Vec<DemoPerpPositionV1>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DemoAppPayloadV1 {
    pub market: DemoMarketSnapshotV1,
    pub liquidity: DemoLiquiditySnapshotV1,
    pub preview_liquidity: DemoLiquiditySnapshotV1,
    pub backend_trace: DemoQuoteTraceV1,
    pub risk_grid: Vec<DemoRiskGridPointV1>,
    pub settlement_waterfall: DemoSettlementWaterfallV1,
    pub presets: Vec<DemoQuotePresetV1>,
    pub quote_grid: Vec<DemoQuotePresetV1>,
    pub regime_indexes: Vec<DemoRegimeIndexV1>,
    pub perps: DemoPerpMarketV1,
}

pub fn seeded_demo_market() -> Result<NormalV1Program, String> {
    let (program, _) = NormalV1Program::initialize(
        ProgramInitializeArgsV1 {
            accounts: InitializeAccountsV1 {
                initializer: [9_u8; 32],
                collateral_mint: [1_u8; 32],
                collateral_vault: [2_u8; 32],
                lp_mint: [3_u8; 32],
                market_authority: [4_u8; 32],
            },
            initial_b: Fixed::from_f64(50.0)?,
            initial_k: Fixed::from_f64(21.05026039569057)?,
            initial_distribution: FixedNormalDistribution::new(
                Fixed::from_f64(95.0)?,
                Fixed::from_f64(10.0)?,
            )?,
            oracle_config: OracleConfigV1 {
                oracle_program: [5_u8; 32],
                oracle_feed: [6_u8; 32],
                authority: [7_u8; 32],
            },
        },
        1,
    )?;
    Ok(program)
}

pub fn initialize_market_from_seed(seed: HackathonMarketSeed) -> Result<NormalV1Program, String> {
    let (program, _) = NormalV1Program::initialize(
        ProgramInitializeArgsV1 {
            accounts: InitializeAccountsV1 {
                initializer: seed.initializer,
                collateral_mint: seed.collateral_mint,
                collateral_vault: seed.collateral_vault,
                lp_mint: seed.lp_mint,
                market_authority: seed.market_authority,
            },
            initial_b: seed.initial_b,
            initial_k: seed.initial_k,
            initial_distribution: seed.initial_distribution,
            oracle_config: OracleConfigV1 {
                oracle_program: seed.oracle_program,
                oracle_feed: seed.oracle_feed,
                authority: seed.oracle_authority,
            },
        },
        1,
    )?;
    Ok(program)
}

pub fn build_trade_quote(
    program: &NormalV1Program,
    request: TradeQuoteRequestV1,
) -> Result<TraderQuoteEnvelopeV1, String> {
    let quote = program
        .state
        .core_market
        .quote_trade(request.target_distribution)?;
    let instruction = SolanaInstructionV1::Trade(TradeArgsV1 {
        quote: QuoteEnvelopeV1 {
            market: request.market,
            expected_market_version: quote.market_version,
            new_distribution: quote.new_distribution,
            collateral_required: quote.collateral_quote.collateral_required,
            fee_paid: quote.collateral_quote.fee_paid,
            total_debit: quote.collateral_quote.total_debit,
            max_total_debit: quote.collateral_quote.total_debit,
            taker_fee_bps: quote.taker_fee_bps,
            min_taker_fee: quote.min_taker_fee,
            search_lower_bound: quote.collateral_quote.lower_bound,
            search_upper_bound: quote.collateral_quote.upper_bound,
            coarse_samples: quote.collateral_quote.coarse_samples,
            refine_samples: quote.collateral_quote.refine_samples,
            quote_slot: request.quote_slot,
            quote_expiry_slot: request.quote_expiry_slot,
        },
    });
    let serialized_instruction = pack_instruction(&instruction);

    Ok(TraderQuoteEnvelopeV1 {
        trader: request.trader,
        collateral_required: quote.collateral_quote.collateral_required,
        fee_paid: quote.collateral_quote.fee_paid,
        total_debit: quote.collateral_quote.total_debit,
        quote_expiry_slot: request.quote_expiry_slot,
        target_distribution: request.target_distribution,
        quote,
        serialized_instruction,
    })
}

pub fn android_trade_intent(quote: &TraderQuoteEnvelopeV1) -> AndroidTradeIntentV1 {
    AndroidTradeIntentV1 {
        serialized_instruction_hex: encode_hex(&quote.serialized_instruction),
        collateral_required_display: quote.collateral_required.to_string(),
        fee_paid_display: quote.fee_paid.to_string(),
        total_debit_display: quote.total_debit.to_string(),
        quote_expiry_slot: quote.quote_expiry_slot,
        mu_display: quote.target_distribution.mu.to_string(),
        sigma_display: quote.target_distribution.sigma.to_string(),
    }
}

pub fn preview_trade_token_operation(
    quote: &TraderQuoteEnvelopeV1,
) -> Vec<ProgramTokenOperationV1> {
    vec![ProgramTokenOperationV1::TransferToVault {
        owner: quote.trader,
        amount: quote.total_debit,
    }]
}

pub fn demo_market_summary(program: &NormalV1Program) -> String {
    format!(
        "Normal market: mu={}, sigma={}, backing={}, trades={}",
        program.state.market_account.current_distribution.mu,
        program.state.market_account.current_distribution.sigma,
        program.state.market_account.b,
        program.state.market_account.total_trades
    )
}

pub fn demo_app_payload() -> Result<DemoAppPayloadV1, String> {
    let program = seeded_demo_market()?;
    let trace_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(100.0)?, Fixed::from_f64(10.0)?)?;
    let backend_trace = program.state.core_market.quote_trace(trace_distribution)?;
    let mut preview_market = program.state.core_market.clone();
    let preview_quote = preview_market.quote_trade(trace_distribution)?;
    preview_market.trade_with_quote(preview_quote)?;

    let market = DemoMarketSnapshotV1 {
        title: "Seeded SOL price market".to_string(),
        status: format!("{:?}", program.state.market_account.status),
        market_id_hex: encode_hex(&DEMO_MARKET_ID),
        state_version: program.state.market_account.state_version,
        current_mu_display: program
            .state
            .market_account
            .current_distribution
            .mu
            .to_string(),
        current_sigma_display: program
            .state
            .market_account
            .current_distribution
            .sigma
            .to_string(),
        k_display: program.state.market_account.k.to_string(),
        backing_display: program.state.market_account.b.to_string(),
        taker_fee_bps: program.state.market_account.taker_fee_bps,
        min_taker_fee_display: program.state.market_account.min_taker_fee.to_string(),
        maker_fees_earned_display: program.state.market_account.fees_accrued.to_string(),
        maker_deposit_display: program.state.market_account.b.to_string(),
        total_trades: program.state.market_account.total_trades,
        max_open_trades: program.state.market_account.max_open_trades,
        expiry_slot: program.state.market_account.expiry_slot,
        demo_quote_slot: DEMO_QUOTE_SLOT,
        demo_quote_expiry_slot: DEMO_QUOTE_EXPIRY_SLOT,
        coarse_samples: 4096,
        refine_samples: 4096,
    };
    let liquidity = demo_liquidity_snapshot(program.state.core_market.liquidity_snapshot()?);
    let preview_liquidity = demo_liquidity_snapshot(preview_market.liquidity_snapshot()?);
    let backend_trace = demo_quote_trace(backend_trace);
    let risk_grid = preview_market
        .risk_grid(25)?
        .into_iter()
        .map(demo_risk_grid_point)
        .collect();
    let settlement_waterfall = demo_settlement_waterfall(
        preview_market.settlement_waterfall_preview(Fixed::from_f64(107.6)?)?,
    );

    let presets = vec![
        quote_preset(&program, "base", "Base shift to 100 / 10", 100.0, 10.0)?,
        quote_preset(
            &program,
            "bullish",
            "Bullish shift to 105 / 12",
            105.0,
            12.0,
        )?,
        quote_preset(&program, "tight", "Tighter view at 92 / 8.5", 92.0, 8.5)?,
    ];

    let quote_grid = build_quote_grid(&program)?;
    let regime_indexes = demo_regime_indexes()?;
    let perps = demo_perp_market(&program)?;

    Ok(DemoAppPayloadV1 {
        market,
        liquidity,
        preview_liquidity,
        backend_trace,
        risk_grid,
        settlement_waterfall,
        presets,
        quote_grid,
        regime_indexes,
        perps,
    })
}

pub fn demo_app_payload_json() -> Result<String, String> {
    let payload = demo_app_payload()?;
    let mut json = String::new();
    json.push_str("{\n");
    json.push_str("  \"market\": {\n");
    json.push_str(&format!(
        "    \"title\": \"{}\",\n",
        escape_json(&payload.market.title)
    ));
    json.push_str(&format!(
        "    \"status\": \"{}\",\n",
        escape_json(&payload.market.status)
    ));
    json.push_str(&format!(
        "    \"market_id_hex\": \"{}\",\n",
        escape_json(&payload.market.market_id_hex)
    ));
    json.push_str(&format!(
        "    \"state_version\": {},\n",
        payload.market.state_version
    ));
    json.push_str(&format!(
        "    \"current_mu_display\": \"{}\",\n",
        escape_json(&payload.market.current_mu_display)
    ));
    json.push_str(&format!(
        "    \"current_sigma_display\": \"{}\",\n",
        escape_json(&payload.market.current_sigma_display)
    ));
    json.push_str(&format!(
        "    \"k_display\": \"{}\",\n",
        escape_json(&payload.market.k_display)
    ));
    json.push_str(&format!(
        "    \"backing_display\": \"{}\",\n",
        escape_json(&payload.market.backing_display)
    ));
    json.push_str(&format!(
        "    \"taker_fee_bps\": {},\n",
        payload.market.taker_fee_bps
    ));
    json.push_str(&format!(
        "    \"min_taker_fee_display\": \"{}\",\n",
        escape_json(&payload.market.min_taker_fee_display)
    ));
    json.push_str(&format!(
        "    \"maker_fees_earned_display\": \"{}\",\n",
        escape_json(&payload.market.maker_fees_earned_display)
    ));
    json.push_str(&format!(
        "    \"maker_deposit_display\": \"{}\",\n",
        escape_json(&payload.market.maker_deposit_display)
    ));
    json.push_str(&format!(
        "    \"total_trades\": {},\n",
        payload.market.total_trades
    ));
    json.push_str(&format!(
        "    \"max_open_trades\": {},\n",
        payload.market.max_open_trades
    ));
    json.push_str(&format!(
        "    \"expiry_slot\": {},\n",
        payload.market.expiry_slot
    ));
    json.push_str(&format!(
        "    \"demo_quote_slot\": {},\n",
        payload.market.demo_quote_slot
    ));
    json.push_str(&format!(
        "    \"demo_quote_expiry_slot\": {},\n",
        payload.market.demo_quote_expiry_slot
    ));
    json.push_str(&format!(
        "    \"coarse_samples\": {},\n",
        payload.market.coarse_samples
    ));
    json.push_str(&format!(
        "    \"refine_samples\": {}\n",
        payload.market.refine_samples
    ));
    json.push_str("  },\n");
    json.push_str("  \"liquidity\": ");
    push_liquidity_snapshot_json(&mut json, &payload.liquidity);
    json.push_str(",\n");
    json.push_str("  \"preview_liquidity\": ");
    push_liquidity_snapshot_json(&mut json, &payload.preview_liquidity);
    json.push_str(",\n");
    json.push_str("  \"backend_trace\": ");
    push_quote_trace_json(&mut json, &payload.backend_trace);
    json.push_str(",\n");
    json.push_str("  \"risk_grid\": [\n");
    push_risk_grid_json(&mut json, &payload.risk_grid);
    json.push_str("  ],\n");
    json.push_str("  \"settlement_waterfall\": ");
    push_settlement_waterfall_json(&mut json, &payload.settlement_waterfall);
    json.push_str(",\n");
    json.push_str("  \"presets\": [\n");
    push_quote_list_json(&mut json, &payload.presets);
    json.push_str("  ],\n");
    json.push_str("  \"quote_grid\": [\n");
    push_quote_list_json(&mut json, &payload.quote_grid);
    json.push_str("  ],\n");
    json.push_str("  \"regime_indexes\": [\n");
    push_regime_indexes_json(&mut json, &payload.regime_indexes);
    json.push_str("  ],\n");
    json.push_str("  \"perps\": ");
    push_perp_market_json(&mut json, &payload.perps);
    json.push('\n');
    json.push_str("}\n");
    Ok(json)
}

fn push_quote_list_json(json: &mut String, quotes: &[DemoQuotePresetV1]) {
    for (index, preset) in quotes.iter().enumerate() {
        json.push_str("    {\n");
        json.push_str(&format!("      \"id\": \"{}\",\n", escape_json(&preset.id)));
        json.push_str(&format!(
            "      \"label\": \"{}\",\n",
            escape_json(&preset.label)
        ));
        json.push_str(&format!(
            "      \"target_mu_display\": \"{}\",\n",
            escape_json(&preset.target_mu_display)
        ));
        json.push_str(&format!(
            "      \"target_sigma_display\": \"{}\",\n",
            escape_json(&preset.target_sigma_display)
        ));
        json.push_str(&format!(
            "      \"collateral_required_display\": \"{}\",\n",
            escape_json(&preset.collateral_required_display)
        ));
        json.push_str(&format!(
            "      \"fee_paid_display\": \"{}\",\n",
            escape_json(&preset.fee_paid_display)
        ));
        json.push_str(&format!(
            "      \"total_debit_display\": \"{}\",\n",
            escape_json(&preset.total_debit_display)
        ));
        json.push_str(&format!(
            "      \"max_total_debit_display\": \"{}\",\n",
            escape_json(&preset.max_total_debit_display)
        ));
        json.push_str(&format!(
            "      \"quote_expiry_slot\": {},\n",
            preset.quote_expiry_slot
        ));
        json.push_str(&format!(
            "      \"serialized_instruction_hex\": \"{}\",\n",
            escape_json(&preset.serialized_instruction_hex)
        ));
        json.push_str("      \"curve_points\": [\n");
        push_curve_points_json(json, &preset.curve_points);
        json.push_str("      ]\n");
        json.push_str("    }");
        if index + 1 != quotes.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn push_curve_points_json(json: &mut String, points: &[DemoCurvePointV1]) {
    for (index, point) in points.iter().enumerate() {
        json.push_str("        {\n");
        json.push_str(&format!(
            "          \"x\": \"{}\",\n",
            escape_json(&point.x)
        ));
        json.push_str(&format!(
            "          \"current\": \"{}\",\n",
            escape_json(&point.current)
        ));
        json.push_str(&format!(
            "          \"proposed\": \"{}\",\n",
            escape_json(&point.proposed)
        ));
        json.push_str(&format!(
            "          \"edge\": \"{}\"\n",
            escape_json(&point.edge)
        ));
        json.push_str("        }");
        if index + 1 != points.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn push_liquidity_snapshot_json(json: &mut String, snapshot: &DemoLiquiditySnapshotV1) {
    json.push_str("{\n");
    json.push_str(&format!(
        "    \"maker_deposit_display\": \"{}\",\n",
        escape_json(&snapshot.maker_deposit_display)
    ));
    json.push_str(&format!(
        "    \"vault_cash_display\": \"{}\",\n",
        escape_json(&snapshot.vault_cash_display)
    ));
    json.push_str(&format!(
        "    \"accrued_fees_display\": \"{}\",\n",
        escape_json(&snapshot.accrued_fees_display)
    ));
    json.push_str(&format!(
        "    \"current_k_display\": \"{}\",\n",
        escape_json(&snapshot.current_k_display)
    ));
    json.push_str(&format!(
        "    \"total_lp_shares_display\": \"{}\",\n",
        escape_json(&snapshot.total_lp_shares_display)
    ));
    json.push_str(&format!(
        "    \"locked_trader_collateral_display\": \"{}\",\n",
        escape_json(&snapshot.locked_trader_collateral_display)
    ));
    json.push_str(&format!(
        "    \"worst_case_trader_liability_display\": \"{}\",\n",
        escape_json(&snapshot.worst_case_trader_liability_display)
    ));
    json.push_str(&format!(
        "    \"available_maker_buffer_display\": \"{}\",\n",
        escape_json(&snapshot.available_maker_buffer_display)
    ));
    json.push_str(&format!("    \"open_trades\": {},\n", snapshot.open_trades));
    json.push_str(&format!(
        "    \"max_open_trades\": {},\n",
        snapshot.max_open_trades
    ));
    json.push_str(&format!(
        "    \"lp_controls_status\": \"{}\"\n",
        escape_json(&snapshot.lp_controls_status)
    ));
    json.push_str("  }");
}

fn push_quote_trace_json(json: &mut String, trace: &DemoQuoteTraceV1) {
    json.push_str("{\n");
    json.push_str(&format!(
        "    \"market_version\": {},\n",
        trace.market_version
    ));
    json.push_str(&format!(
        "    \"old_mu_display\": \"{}\",\n",
        escape_json(&trace.old_mu_display)
    ));
    json.push_str(&format!(
        "    \"old_sigma_display\": \"{}\",\n",
        escape_json(&trace.old_sigma_display)
    ));
    json.push_str(&format!(
        "    \"new_mu_display\": \"{}\",\n",
        escape_json(&trace.new_mu_display)
    ));
    json.push_str(&format!(
        "    \"new_sigma_display\": \"{}\",\n",
        escape_json(&trace.new_sigma_display)
    ));
    json.push_str(&format!(
        "    \"k_display\": \"{}\",\n",
        escape_json(&trace.k_display)
    ));
    json.push_str(&format!(
        "    \"search_lower_bound_display\": \"{}\",\n",
        escape_json(&trace.search_lower_bound_display)
    ));
    json.push_str(&format!(
        "    \"search_upper_bound_display\": \"{}\",\n",
        escape_json(&trace.search_upper_bound_display)
    ));
    json.push_str(&format!(
        "    \"max_loss_outcome_display\": \"{}\",\n",
        escape_json(&trace.max_loss_outcome_display)
    ));
    json.push_str(&format!(
        "    \"max_directional_loss_display\": \"{}\",\n",
        escape_json(&trace.max_directional_loss_display)
    ));
    json.push_str(&format!(
        "    \"collateral_required_display\": \"{}\",\n",
        escape_json(&trace.collateral_required_display)
    ));
    json.push_str(&format!(
        "    \"fee_paid_display\": \"{}\",\n",
        escape_json(&trace.fee_paid_display)
    ));
    json.push_str(&format!(
        "    \"total_debit_display\": \"{}\",\n",
        escape_json(&trace.total_debit_display)
    ));
    json.push_str(&format!(
        "    \"vault_cash_before_display\": \"{}\",\n",
        escape_json(&trace.vault_cash_before_display)
    ));
    json.push_str(&format!(
        "    \"vault_cash_after_display\": \"{}\",\n",
        escape_json(&trace.vault_cash_after_display)
    ));
    json.push_str(&format!(
        "    \"locked_collateral_before_display\": \"{}\",\n",
        escape_json(&trace.locked_collateral_before_display)
    ));
    json.push_str(&format!(
        "    \"locked_collateral_after_display\": \"{}\",\n",
        escape_json(&trace.locked_collateral_after_display)
    ));
    json.push_str(&format!(
        "    \"worst_case_liability_before_display\": \"{}\",\n",
        escape_json(&trace.worst_case_liability_before_display)
    ));
    json.push_str(&format!(
        "    \"worst_case_liability_after_display\": \"{}\",\n",
        escape_json(&trace.worst_case_liability_after_display)
    ));
    json.push_str(&format!(
        "    \"maker_buffer_before_display\": \"{}\",\n",
        escape_json(&trace.maker_buffer_before_display)
    ));
    json.push_str(&format!(
        "    \"maker_buffer_after_display\": \"{}\"\n",
        escape_json(&trace.maker_buffer_after_display)
    ));
    json.push_str("  }");
}

fn push_risk_grid_json(json: &mut String, points: &[DemoRiskGridPointV1]) {
    for (index, point) in points.iter().enumerate() {
        json.push_str("    {\n");
        json.push_str(&format!(
            "      \"outcome_display\": \"{}\",\n",
            escape_json(&point.outcome_display)
        ));
        json.push_str(&format!(
            "      \"trader_liability_display\": \"{}\",\n",
            escape_json(&point.trader_liability_display)
        ));
        json.push_str(&format!(
            "      \"lp_residual_after_traders_display\": \"{}\",\n",
            escape_json(&point.lp_residual_after_traders_display)
        ));
        json.push_str(&format!(
            "      \"maker_buffer_display\": \"{}\"\n",
            escape_json(&point.maker_buffer_display)
        ));
        json.push_str("    }");
        if index + 1 != points.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn push_settlement_waterfall_json(json: &mut String, waterfall: &DemoSettlementWaterfallV1) {
    json.push_str("{\n");
    json.push_str(&format!(
        "    \"outcome_display\": \"{}\",\n",
        escape_json(&waterfall.outcome_display)
    ));
    json.push_str(&format!(
        "    \"trader_claims_display\": \"{}\",\n",
        escape_json(&waterfall.trader_claims_display)
    ));
    json.push_str(&format!(
        "    \"lp_residual_claim_display\": \"{}\",\n",
        escape_json(&waterfall.lp_residual_claim_display)
    ));
    json.push_str(&format!(
        "    \"protocol_dust_display\": \"{}\"\n",
        escape_json(&waterfall.protocol_dust_display)
    ));
    json.push_str("  }");
}

fn push_regime_indexes_json(json: &mut String, indexes: &[DemoRegimeIndexV1]) {
    for (index, regime) in indexes.iter().enumerate() {
        json.push_str("    {\n");
        json.push_str(&format!("      \"id\": \"{}\",\n", escape_json(&regime.id)));
        json.push_str(&format!(
            "      \"symbol\": \"{}\",\n",
            escape_json(&regime.symbol)
        ));
        json.push_str(&format!(
            "      \"title\": \"{}\",\n",
            escape_json(&regime.title)
        ));
        json.push_str(&format!(
            "      \"thesis\": \"{}\",\n",
            escape_json(&regime.thesis)
        ));
        json.push_str(&format!(
            "      \"status\": \"{}\",\n",
            escape_json(&regime.status)
        ));
        json.push_str(&format!(
            "      \"level_display\": \"{}\",\n",
            escape_json(&regime.level_display)
        ));
        json.push_str(&format!(
            "      \"previous_level_display\": \"{}\",\n",
            escape_json(&regime.previous_level_display)
        ));
        json.push_str(&format!(
            "      \"change_display\": \"{}\",\n",
            escape_json(&regime.change_display)
        ));
        json.push_str(&format!(
            "      \"rebalance_slot\": {},\n",
            regime.rebalance_slot
        ));
        json.push_str(&format!(
            "      \"next_rebalance_slot\": {},\n",
            regime.next_rebalance_slot
        ));
        json.push_str(&format!(
            "      \"quote_expiry_slot\": {},\n",
            regime.quote_expiry_slot
        ));
        json.push_str("      \"constituents\": [\n");
        push_regime_constituents_json(json, &regime.constituents);
        json.push_str("      ],\n");
        json.push_str("      \"history\": [\n");
        push_regime_history_json(json, &regime.history);
        json.push_str("      ],\n");
        json.push_str("      \"long_quote\": ");
        push_regime_quote_json(json, &regime.long_quote);
        json.push_str(",\n");
        json.push_str("      \"short_quote\": ");
        push_regime_quote_json(json, &regime.short_quote);
        json.push('\n');
        json.push_str("    }");
        if index + 1 != indexes.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn push_regime_constituents_json(json: &mut String, constituents: &[DemoRegimeConstituentV1]) {
    for (index, constituent) in constituents.iter().enumerate() {
        json.push_str("        {\n");
        json.push_str(&format!(
            "          \"id\": \"{}\",\n",
            escape_json(&constituent.id)
        ));
        json.push_str(&format!(
            "          \"label\": \"{}\",\n",
            escape_json(&constituent.label)
        ));
        json.push_str(&format!(
            "          \"side\": \"{}\",\n",
            escape_json(&constituent.side)
        ));
        json.push_str(&format!(
            "          \"weight_bps\": {},\n",
            constituent.weight_bps
        ));
        json.push_str(&format!(
            "          \"probability_display\": \"{}\",\n",
            escape_json(&constituent.probability_display)
        ));
        json.push_str(&format!(
            "          \"previous_probability_display\": \"{}\",\n",
            escape_json(&constituent.previous_probability_display)
        ));
        json.push_str(&format!(
            "          \"level_contribution_display\": \"{}\",\n",
            escape_json(&constituent.level_contribution_display)
        ));
        json.push_str(&format!(
            "          \"signed_pressure_display\": \"{}\",\n",
            escape_json(&constituent.signed_pressure_display)
        ));
        json.push_str(&format!(
            "          \"status\": \"{}\",\n",
            escape_json(&constituent.status)
        ));
        json.push_str(&format!(
            "          \"expiry_slot\": {}\n",
            constituent.expiry_slot
        ));
        json.push_str("        }");
        if index + 1 != constituents.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn push_regime_history_json(json: &mut String, history: &[DemoRegimeHistoryPointV1]) {
    for (index, point) in history.iter().enumerate() {
        json.push_str("        {\n");
        json.push_str(&format!("          \"slot\": {},\n", point.slot));
        json.push_str(&format!(
            "          \"level_display\": \"{}\"\n",
            escape_json(&point.level_display)
        ));
        json.push_str("        }");
        if index + 1 != history.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn push_regime_quote_json(json: &mut String, quote: &DemoRegimeQuoteV1) {
    json.push_str("{\n");
    json.push_str(&format!(
        "        \"side\": \"{}\",\n",
        escape_json(&quote.side)
    ));
    json.push_str(&format!(
        "        \"size_display\": \"{}\",\n",
        escape_json(&quote.size_display)
    ));
    json.push_str(&format!(
        "        \"entry_level_display\": \"{}\",\n",
        escape_json(&quote.entry_level_display)
    ));
    json.push_str(&format!(
        "        \"token_price_display\": \"{}\",\n",
        escape_json(&quote.token_price_display)
    ));
    json.push_str(&format!(
        "        \"collateral_required_display\": \"{}\",\n",
        escape_json(&quote.collateral_required_display)
    ));
    json.push_str(&format!(
        "        \"fee_paid_display\": \"{}\",\n",
        escape_json(&quote.fee_paid_display)
    ));
    json.push_str(&format!(
        "        \"total_debit_display\": \"{}\",\n",
        escape_json(&quote.total_debit_display)
    ));
    json.push_str(&format!(
        "        \"memo_payload\": \"{}\"\n",
        escape_json(&quote.memo_payload)
    ));
    json.push_str("      }");
}

fn push_perp_market_json(json: &mut String, market: &DemoPerpMarketV1) {
    json.push_str("{\n");
    json.push_str(&format!(
        "    \"symbol\": \"{}\",\n",
        escape_json(&market.symbol)
    ));
    json.push_str(&format!(
        "    \"title\": \"{}\",\n",
        escape_json(&market.title)
    ));
    json.push_str(&format!(
        "    \"status\": \"{}\",\n",
        escape_json(&market.status)
    ));
    json.push_str(&format!("    \"slot\": {},\n", market.slot));
    json.push_str(&format!(
        "    \"next_funding_slot\": {},\n",
        market.next_funding_slot
    ));
    json.push_str(&format!(
        "    \"funding_interval\": {},\n",
        market.funding_interval
    ));
    json.push_str(&format!(
        "    \"mark_price_display\": \"{}\",\n",
        escape_json(&market.mark_price_display)
    ));
    json.push_str(&format!(
        "    \"anchor_mu_display\": \"{}\",\n",
        escape_json(&market.anchor_mu_display)
    ));
    json.push_str(&format!(
        "    \"anchor_sigma_display\": \"{}\",\n",
        escape_json(&market.anchor_sigma_display)
    ));
    json.push_str(&format!(
        "    \"amm_mu_display\": \"{}\",\n",
        escape_json(&market.amm_mu_display)
    ));
    json.push_str(&format!(
        "    \"amm_sigma_display\": \"{}\",\n",
        escape_json(&market.amm_sigma_display)
    ));
    json.push_str(&format!(
        "    \"kl_display\": \"{}\",\n",
        escape_json(&market.kl_display)
    ));
    json.push_str(&format!(
        "    \"spot_funding_rate_display\": \"{}\",\n",
        escape_json(&market.spot_funding_rate_display)
    ));
    json.push_str(&format!(
        "    \"vault_cash_display\": \"{}\",\n",
        escape_json(&market.vault_cash_display)
    ));
    json.push_str(&format!(
        "    \"lp_nav_display\": \"{}\",\n",
        escape_json(&market.lp_nav_display)
    ));
    json.push_str(&format!(
        "    \"available_lp_cash_display\": \"{}\",\n",
        escape_json(&market.available_lp_cash_display)
    ));
    json.push_str(&format!(
        "    \"open_positions\": {},\n",
        market.open_positions
    ));
    json.push_str(&format!(
        "    \"total_lp_shares_display\": \"{}\",\n",
        escape_json(&market.total_lp_shares_display)
    ));
    json.push_str("    \"curve_points\": [\n");
    push_perp_curve_points_json(json, &market.curve_points);
    json.push_str("    ],\n");
    json.push_str("    \"funding_path\": [\n");
    push_perp_funding_path_json(json, &market.funding_path);
    json.push_str("    ],\n");
    json.push_str("    \"long_quote\": ");
    push_perp_quote_json(json, &market.long_quote);
    json.push_str(",\n");
    json.push_str("    \"short_quote\": ");
    push_perp_quote_json(json, &market.short_quote);
    json.push_str(",\n");
    json.push_str("    \"positions\": [\n");
    push_perp_positions_json(json, &market.positions);
    json.push_str("    ]\n");
    json.push_str("  }");
}

fn push_perp_curve_points_json(json: &mut String, points: &[DemoPerpCurvePointV1]) {
    for (index, point) in points.iter().enumerate() {
        json.push_str("      {\n");
        json.push_str(&format!("        \"x\": \"{}\",\n", escape_json(&point.x)));
        json.push_str(&format!(
            "        \"amm\": \"{}\",\n",
            escape_json(&point.amm)
        ));
        json.push_str(&format!(
            "        \"anchor\": \"{}\",\n",
            escape_json(&point.anchor)
        ));
        json.push_str(&format!(
            "        \"edge\": \"{}\"\n",
            escape_json(&point.edge)
        ));
        json.push_str("      }");
        if index + 1 != points.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn push_perp_funding_path_json(json: &mut String, points: &[DemoPerpFundingPointV1]) {
    for (index, point) in points.iter().enumerate() {
        json.push_str("      {\n");
        json.push_str(&format!("        \"slot\": {},\n", point.slot));
        json.push_str(&format!(
            "        \"amm_mu_display\": \"{}\",\n",
            escape_json(&point.amm_mu_display)
        ));
        json.push_str(&format!(
            "        \"anchor_mu_display\": \"{}\",\n",
            escape_json(&point.anchor_mu_display)
        ));
        json.push_str(&format!(
            "        \"kl_display\": \"{}\",\n",
            escape_json(&point.kl_display)
        ));
        json.push_str(&format!(
            "        \"funding_rate_display\": \"{}\"\n",
            escape_json(&point.funding_rate_display)
        ));
        json.push_str("      }");
        if index + 1 != points.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn push_perp_quote_json(json: &mut String, quote: &DemoPerpQuoteV1) {
    json.push_str("{\n");
    json.push_str(&format!(
        "      \"side\": \"{}\",\n",
        escape_json(&quote.side)
    ));
    json.push_str(&format!(
        "      \"target_mu_display\": \"{}\",\n",
        escape_json(&quote.target_mu_display)
    ));
    json.push_str(&format!(
        "      \"target_sigma_display\": \"{}\",\n",
        escape_json(&quote.target_sigma_display)
    ));
    json.push_str(&format!(
        "      \"collateral_required_display\": \"{}\",\n",
        escape_json(&quote.collateral_required_display)
    ));
    json.push_str(&format!(
        "      \"fee_paid_display\": \"{}\",\n",
        escape_json(&quote.fee_paid_display)
    ));
    json.push_str(&format!(
        "      \"total_debit_display\": \"{}\",\n",
        escape_json(&quote.total_debit_display)
    ));
    json.push_str(&format!(
        "      \"estimated_funding_display\": \"{}\",\n",
        escape_json(&quote.estimated_funding_display)
    ));
    json.push_str(&format!(
        "      \"close_mark_display\": \"{}\",\n",
        escape_json(&quote.close_mark_display)
    ));
    json.push_str(&format!(
        "      \"memo_payload\": \"{}\"\n",
        escape_json(&quote.memo_payload)
    ));
    json.push_str("    }");
}

fn push_perp_positions_json(json: &mut String, positions: &[DemoPerpPositionV1]) {
    for (index, position) in positions.iter().enumerate() {
        json.push_str("      {\n");
        json.push_str(&format!(
            "        \"id\": \"{}\",\n",
            escape_json(&position.id)
        ));
        json.push_str(&format!(
            "        \"side\": \"{}\",\n",
            escape_json(&position.side)
        ));
        json.push_str(&format!(
            "        \"entry_mu_display\": \"{}\",\n",
            escape_json(&position.entry_mu_display)
        ));
        json.push_str(&format!(
            "        \"collateral_display\": \"{}\",\n",
            escape_json(&position.collateral_display)
        ));
        json.push_str(&format!(
            "        \"funding_paid_display\": \"{}\",\n",
            escape_json(&position.funding_paid_display)
        ));
        json.push_str(&format!(
            "        \"funding_received_display\": \"{}\",\n",
            escape_json(&position.funding_received_display)
        ));
        json.push_str(&format!(
            "        \"mark_payout_display\": \"{}\",\n",
            escape_json(&position.mark_payout_display)
        ));
        json.push_str(&format!(
            "        \"status\": \"{}\"\n",
            escape_json(&position.status)
        ));
        json.push_str("      }");
        if index + 1 != positions.len() {
            json.push(',');
        }
        json.push('\n');
    }
}

fn quote_preset(
    program: &NormalV1Program,
    id: &str,
    label: &str,
    mu: f64,
    sigma: f64,
) -> Result<DemoQuotePresetV1, String> {
    let target_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(mu)?, Fixed::from_f64(sigma)?)?;
    let quote = build_trade_quote(
        program,
        TradeQuoteRequestV1 {
            trader: [8_u8; 32],
            market: DEMO_MARKET_ID,
            target_distribution,
            quote_slot: DEMO_QUOTE_SLOT,
            quote_expiry_slot: DEMO_QUOTE_EXPIRY_SLOT,
        },
    )?;
    let intent = android_trade_intent(&quote);
    Ok(DemoQuotePresetV1 {
        id: id.to_string(),
        label: label.to_string(),
        target_mu_display: intent.mu_display,
        target_sigma_display: intent.sigma_display,
        collateral_required_display: intent.collateral_required_display,
        fee_paid_display: intent.fee_paid_display,
        total_debit_display: intent.total_debit_display,
        max_total_debit_display: quote.quote.max_total_debit.to_string(),
        quote_expiry_slot: intent.quote_expiry_slot,
        serialized_instruction_hex: intent.serialized_instruction_hex,
        curve_points: build_curve_points(program, target_distribution)?,
    })
}

fn build_quote_grid(program: &NormalV1Program) -> Result<Vec<DemoQuotePresetV1>, String> {
    let mu_values = [88.0, 92.0, 95.0, 100.0, 105.0];
    let sigma_values = [8.5, 10.0, 12.0];
    let mut quotes = Vec::new();

    for sigma in sigma_values {
        for mu in mu_values {
            if let Ok(quote) = quote_preset(
                program,
                &format!("grid-{mu:.1}-{sigma:.1}"),
                &format!("Grid quote for mu {mu:.1}, sigma {sigma:.1}"),
                mu,
                sigma,
            ) {
                quotes.push(quote);
            }
        }
    }

    Ok(quotes)
}

fn build_curve_points(
    program: &NormalV1Program,
    target_distribution: FixedNormalDistribution,
) -> Result<Vec<DemoCurvePointV1>, String> {
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
        let current_value = fixed_calculate_f(x_fixed, current, k)?;
        let proposed_value = fixed_calculate_f(x_fixed, target_distribution, k)?;
        points.push(DemoCurvePointV1 {
            x: Fixed::from_f64(x)?.to_string(),
            current: current_value.to_string(),
            proposed: proposed_value.to_string(),
            edge: (proposed_value - current_value).to_string(),
        });
    }

    Ok(points)
}

fn demo_liquidity_snapshot(snapshot: FixedNormalLiquiditySnapshot) -> DemoLiquiditySnapshotV1 {
    DemoLiquiditySnapshotV1 {
        maker_deposit_display: snapshot.maker_deposit.to_string(),
        vault_cash_display: snapshot.vault_cash.to_string(),
        accrued_fees_display: snapshot.accrued_fees.to_string(),
        current_k_display: snapshot.current_k.to_string(),
        total_lp_shares_display: snapshot.total_lp_shares.to_string(),
        locked_trader_collateral_display: snapshot.locked_trader_collateral.to_string(),
        worst_case_trader_liability_display: snapshot.worst_case_trader_liability.to_string(),
        available_maker_buffer_display: snapshot.available_maker_buffer.to_string(),
        open_trades: snapshot.open_trades,
        max_open_trades: snapshot.max_open_trades,
        lp_controls_status: if snapshot.lp_add_remove_locked {
            "Locked after first trade".to_string()
        } else {
            "Open before first trade".to_string()
        },
    }
}

fn demo_quote_trace(trace: FixedNormalQuoteTrace) -> DemoQuoteTraceV1 {
    DemoQuoteTraceV1 {
        market_version: trace.market_version,
        old_mu_display: trace.old_distribution.mu.to_string(),
        old_sigma_display: trace.old_distribution.sigma.to_string(),
        new_mu_display: trace.new_distribution.mu.to_string(),
        new_sigma_display: trace.new_distribution.sigma.to_string(),
        k_display: trace.k.to_string(),
        search_lower_bound_display: trace.search_lower_bound.to_string(),
        search_upper_bound_display: trace.search_upper_bound.to_string(),
        max_loss_outcome_display: trace.max_loss_outcome.to_string(),
        max_directional_loss_display: trace.max_directional_loss.to_string(),
        collateral_required_display: trace.collateral_required.to_string(),
        fee_paid_display: trace.fee_paid.to_string(),
        total_debit_display: trace.total_debit.to_string(),
        vault_cash_before_display: trace.vault_cash_before.to_string(),
        vault_cash_after_display: trace.vault_cash_after.to_string(),
        locked_collateral_before_display: trace.locked_collateral_before.to_string(),
        locked_collateral_after_display: trace.locked_collateral_after.to_string(),
        worst_case_liability_before_display: trace.worst_case_liability_before.to_string(),
        worst_case_liability_after_display: trace.worst_case_liability_after.to_string(),
        maker_buffer_before_display: trace.maker_buffer_before.to_string(),
        maker_buffer_after_display: trace.maker_buffer_after.to_string(),
    }
}

fn demo_risk_grid_point(point: FixedNormalRiskGridPoint) -> DemoRiskGridPointV1 {
    DemoRiskGridPointV1 {
        outcome_display: point.outcome.to_string(),
        trader_liability_display: point.trader_liability.to_string(),
        lp_residual_after_traders_display: point.lp_residual_after_traders.to_string(),
        maker_buffer_display: point.maker_buffer.to_string(),
    }
}

fn demo_settlement_waterfall(
    waterfall: FixedNormalSettlementWaterfallPreview,
) -> DemoSettlementWaterfallV1 {
    DemoSettlementWaterfallV1 {
        outcome_display: waterfall.outcome.to_string(),
        trader_claims_display: waterfall.trader_claims.to_string(),
        lp_residual_claim_display: waterfall.lp_residual_claim.to_string(),
        protocol_dust_display: waterfall.protocol_dust.to_string(),
    }
}

fn demo_regime_indexes() -> Result<Vec<DemoRegimeIndexV1>, String> {
    let indexes = vec![
        RegimeIndex {
            id: "hawkish-fed".to_string(),
            symbol: "HAWKFED".to_string(),
            title: "Hawkish Fed".to_string(),
            thesis: "Rate-hike and hot-CPI markets weighted against recession risk.".to_string(),
            status: "Active".to_string(),
            rebalance_slot: 64,
            next_rebalance_slot: 96,
            constituents: vec![
                regime_constituent(
                    "fed-hike-next",
                    "Rate hike by next FOMC",
                    [11_u8; 32],
                    RegimeConstituentSide::Long,
                    4_000,
                    0.68,
                    0.61,
                    128,
                )?,
                regime_constituent(
                    "cpi-above-35",
                    "CPI above 3.5%",
                    [12_u8; 32],
                    RegimeConstituentSide::Long,
                    3_500,
                    0.57,
                    0.52,
                    132,
                )?,
                regime_constituent(
                    "recession-q4",
                    "Recession by Q4",
                    [13_u8; 32],
                    RegimeConstituentSide::Short,
                    2_500,
                    0.31,
                    0.36,
                    188,
                )?,
            ],
        },
        RegimeIndex {
            id: "ai-acceleration".to_string(),
            symbol: "AIFAST".to_string(),
            title: "AI Acceleration".to_string(),
            thesis: "Capability benchmarks and compute spend weighted against AGI-delay markets."
                .to_string(),
            status: "Active".to_string(),
            rebalance_slot: 64,
            next_rebalance_slot: 96,
            constituents: vec![
                regime_constituent(
                    "capability-benchmark",
                    "Frontier benchmark beat",
                    [21_u8; 32],
                    RegimeConstituentSide::Long,
                    4_500,
                    0.73,
                    0.66,
                    146,
                )?,
                regime_constituent(
                    "compute-spend",
                    "Compute spend acceleration",
                    [22_u8; 32],
                    RegimeConstituentSide::Long,
                    3_500,
                    0.64,
                    0.60,
                    152,
                )?,
                regime_constituent(
                    "agi-delay",
                    "AGI delay narrative",
                    [23_u8; 32],
                    RegimeConstituentSide::Short,
                    2_000,
                    0.42,
                    0.48,
                    220,
                )?,
            ],
        },
        RegimeIndex {
            id: "escalation".to_string(),
            symbol: "ESCAL8".to_string(),
            title: "Escalation".to_string(),
            thesis: "Conflict and oil shock markets weighted against peace-deal probability."
                .to_string(),
            status: "Active".to_string(),
            rebalance_slot: 64,
            next_rebalance_slot: 96,
            constituents: vec![
                regime_constituent(
                    "conflict-expansion",
                    "Conflict expansion",
                    [31_u8; 32],
                    RegimeConstituentSide::Long,
                    4_500,
                    0.46,
                    0.43,
                    116,
                )?,
                regime_constituent(
                    "oil-above-100",
                    "Oil above 100",
                    [32_u8; 32],
                    RegimeConstituentSide::Long,
                    2_500,
                    0.39,
                    0.35,
                    124,
                )?,
                regime_constituent(
                    "peace-deal",
                    "Peace deal signed",
                    [33_u8; 32],
                    RegimeConstituentSide::Short,
                    3_000,
                    0.28,
                    0.34,
                    172,
                )?,
            ],
        },
    ];

    indexes.into_iter().map(demo_regime_index).collect()
}

fn demo_regime_index(index: RegimeIndex) -> Result<DemoRegimeIndexV1, String> {
    let snapshot = index.snapshot()?;
    let size = Fixed::ONE;
    let min_fee = Fixed::from_f64(0.001)?;
    let long_quote = quote_regime_trade(&snapshot, RegimeTokenSide::Long, size, 100, min_fee)?;
    let short_quote = quote_regime_trade(&snapshot, RegimeTokenSide::Short, size, 100, min_fee)?;

    Ok(DemoRegimeIndexV1 {
        id: snapshot.index.id.clone(),
        symbol: snapshot.index.symbol.clone(),
        title: snapshot.index.title.clone(),
        thesis: snapshot.index.thesis.clone(),
        status: snapshot.index.status.clone(),
        level_display: snapshot.level.to_string(),
        previous_level_display: snapshot.previous_level.to_string(),
        change_display: snapshot.change.to_string(),
        rebalance_slot: snapshot.index.rebalance_slot,
        next_rebalance_slot: snapshot.index.next_rebalance_slot,
        quote_expiry_slot: DEMO_QUOTE_EXPIRY_SLOT,
        constituents: snapshot
            .constituents
            .iter()
            .map(|constituent| DemoRegimeConstituentV1 {
                id: constituent.constituent.id.clone(),
                label: constituent.constituent.label.clone(),
                side: regime_constituent_side_label(constituent.constituent.side).to_string(),
                weight_bps: constituent.constituent.weight_bps,
                probability_display: fixed_percent(constituent.constituent.probability).to_string(),
                previous_probability_display: fixed_percent(
                    constituent.constituent.previous_probability,
                )
                .to_string(),
                level_contribution_display: constituent.level_contribution.to_string(),
                signed_pressure_display: constituent.signed_pressure.to_string(),
                status: regime_constituent_status_label(constituent.constituent.status).to_string(),
                expiry_slot: constituent.constituent.expiry_slot,
            })
            .collect(),
        history: build_regime_history(&snapshot)?,
        long_quote: demo_regime_quote(&snapshot, &long_quote),
        short_quote: demo_regime_quote(&snapshot, &short_quote),
    })
}

fn regime_constituent(
    id: &str,
    label: &str,
    market_id: [u8; 32],
    side: RegimeConstituentSide,
    weight_bps: u32,
    probability: f64,
    previous_probability: f64,
    expiry_slot: u64,
) -> Result<RegimeConstituent, String> {
    Ok(RegimeConstituent {
        id: id.to_string(),
        label: label.to_string(),
        market_id,
        side,
        weight_bps,
        probability: Fixed::from_f64(probability)?,
        previous_probability: Fixed::from_f64(previous_probability)?,
        status: RegimeConstituentStatus::Active,
        expiry_slot,
    })
}

fn build_regime_history(
    snapshot: &RegimeIndexSnapshot,
) -> Result<Vec<DemoRegimeHistoryPointV1>, String> {
    let samples = 6_u64;
    let mut history = Vec::with_capacity(samples as usize + 1);
    for step in 0..=samples {
        let t = Fixed::from_f64(step as f64 / samples as f64)?;
        let level = snapshot.previous_level + (snapshot.level - snapshot.previous_level) * t;
        history.push(DemoRegimeHistoryPointV1 {
            slot: snapshot
                .index
                .rebalance_slot
                .saturating_sub((samples - step) * 8),
            level_display: level.to_string(),
        });
    }
    Ok(history)
}

fn demo_regime_quote(
    snapshot: &RegimeIndexSnapshot,
    quote: &RegimeTradeQuote,
) -> DemoRegimeQuoteV1 {
    let side = regime_token_side_label(quote.side);
    DemoRegimeQuoteV1 {
        side: side.to_string(),
        size_display: quote.size.to_string(),
        entry_level_display: quote.entry_level.to_string(),
        token_price_display: quote.token_price.to_string(),
        collateral_required_display: quote.collateral_required.to_string(),
        fee_paid_display: quote.fee_paid.to_string(),
        total_debit_display: quote.total_debit.to_string(),
        memo_payload: format!(
            "regime-index-demo|symbol={}|side={}|size={}|entry_level={}|token_price={}|total_debit={}",
            snapshot.index.symbol,
            side.to_ascii_lowercase(),
            quote.size,
            quote.entry_level,
            quote.token_price,
            quote.total_debit,
        ),
    }
}

struct PerpQuoteNumbers {
    target_mu: f64,
    collateral: f64,
    total_debit: f64,
    estimated_funding: f64,
    mark_payout: f64,
}

fn demo_perp_market(program: &NormalV1Program) -> Result<DemoPerpMarketV1, String> {
    let slot = 24_u64;
    let funding_interval = 10_u64;
    let next_funding_slot = 30_u64;
    let anchor_mu = 100.0;
    let anchor_sigma = 8.0;
    let amm = program.state.market_account.current_distribution;
    let amm_mu = amm.mu.to_f64();
    let amm_sigma = amm.sigma.to_f64();
    let current_kl = normal_kl(amm_mu, amm_sigma, anchor_mu, anchor_sigma);
    let spot_funding_rate = demo_perp_funding_rate(current_kl, funding_interval);

    let (long_quote, long_numbers) = demo_perp_quote(
        program,
        "Long",
        103.0,
        9.5,
        anchor_mu,
        anchor_sigma,
        funding_interval,
    )?;
    let (short_quote, short_numbers) = demo_perp_quote(
        program,
        "Short",
        90.0,
        10.5,
        anchor_mu,
        anchor_sigma,
        funding_interval,
    )?;

    let open_collateral = long_numbers.collateral + short_numbers.collateral;
    let net_funding = long_numbers.estimated_funding + short_numbers.estimated_funding;
    let vault_cash = 50.0 + long_numbers.total_debit + short_numbers.total_debit + net_funding;
    let available_lp_cash = (vault_cash - open_collateral).max(0.0);

    Ok(DemoPerpMarketV1 {
        symbol: "SOL-PERP".to_string(),
        title: "Perpetual SOL distribution market".to_string(),
        status: "Active".to_string(),
        slot,
        next_funding_slot,
        funding_interval,
        mark_price_display: fixed_display(amm_mu)?,
        anchor_mu_display: fixed_display(anchor_mu)?,
        anchor_sigma_display: fixed_display(anchor_sigma)?,
        amm_mu_display: amm.mu.to_string(),
        amm_sigma_display: amm.sigma.to_string(),
        kl_display: fixed_display(current_kl)?,
        spot_funding_rate_display: fixed_display(spot_funding_rate)?,
        vault_cash_display: fixed_display(vault_cash)?,
        lp_nav_display: fixed_display(available_lp_cash)?,
        available_lp_cash_display: fixed_display(available_lp_cash)?,
        open_positions: 2,
        total_lp_shares_display: fixed_display(50.0)?,
        curve_points: build_perp_curve_points(program, amm_mu, amm_sigma, anchor_mu, anchor_sigma)?,
        funding_path: build_perp_funding_path(anchor_mu, anchor_sigma, funding_interval)?,
        long_quote,
        short_quote,
        positions: vec![
            demo_perp_position("perp-long-001", "Long", &long_numbers)?,
            demo_perp_position("perp-short-002", "Short", &short_numbers)?,
        ],
    })
}

fn demo_perp_quote(
    program: &NormalV1Program,
    side: &str,
    target_mu: f64,
    target_sigma: f64,
    anchor_mu: f64,
    anchor_sigma: f64,
    funding_interval: u64,
) -> Result<(DemoPerpQuoteV1, PerpQuoteNumbers), String> {
    let target_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(target_mu)?, Fixed::from_f64(target_sigma)?)?;
    let envelope = build_trade_quote(
        program,
        TradeQuoteRequestV1 {
            trader: [8_u8; 32],
            market: DEMO_MARKET_ID,
            target_distribution,
            quote_slot: DEMO_QUOTE_SLOT,
            quote_expiry_slot: DEMO_QUOTE_EXPIRY_SLOT,
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
    let mark_payout = demo_perp_mark_payout(
        program,
        target_mu,
        target_sigma,
        anchor_mu,
        estimated_funding.max(0.0),
        collateral,
    )?;
    let numbers = PerpQuoteNumbers {
        target_mu,
        collateral,
        total_debit: envelope.total_debit.to_f64(),
        estimated_funding,
        mark_payout,
    };
    let memo_estimated_funding = fixed_display(estimated_funding)?;
    let memo_payload = format!(
        "perp-market-demo|side={}|target_mu={}|target_sigma={}|collateral={}|fee={}|total_debit={}|est_funding={}",
        side.to_ascii_lowercase(),
        intent.mu_display,
        intent.sigma_display,
        intent.collateral_required_display,
        intent.fee_paid_display,
        intent.total_debit_display,
        memo_estimated_funding,
    );

    Ok((
        DemoPerpQuoteV1 {
            side: side.to_string(),
            target_mu_display: intent.mu_display,
            target_sigma_display: intent.sigma_display,
            collateral_required_display: intent.collateral_required_display,
            fee_paid_display: intent.fee_paid_display,
            total_debit_display: intent.total_debit_display,
            estimated_funding_display: fixed_display(estimated_funding)?,
            close_mark_display: fixed_display(anchor_mu)?,
            memo_payload,
        },
        numbers,
    ))
}

fn demo_perp_position(
    id: &str,
    side: &str,
    quote: &PerpQuoteNumbers,
) -> Result<DemoPerpPositionV1, String> {
    Ok(DemoPerpPositionV1 {
        id: id.to_string(),
        side: side.to_string(),
        entry_mu_display: fixed_display(quote.target_mu)?,
        collateral_display: fixed_display(quote.collateral)?,
        funding_paid_display: fixed_display(quote.estimated_funding.max(0.0))?,
        funding_received_display: fixed_display((-quote.estimated_funding).max(0.0))?,
        mark_payout_display: fixed_display(quote.mark_payout)?,
        status: "Open".to_string(),
    })
}

fn build_perp_curve_points(
    program: &NormalV1Program,
    amm_mu: f64,
    amm_sigma: f64,
    anchor_mu: f64,
    anchor_sigma: f64,
) -> Result<Vec<DemoPerpCurvePointV1>, String> {
    let amm_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(amm_mu)?, Fixed::from_f64(amm_sigma)?)?;
    let anchor_distribution =
        FixedNormalDistribution::new(Fixed::from_f64(anchor_mu)?, Fixed::from_f64(anchor_sigma)?)?;
    let k = program.state.market_account.k;
    let lower = amm_mu.min(anchor_mu) - amm_sigma.max(anchor_sigma) * 4.0;
    let upper = amm_mu.max(anchor_mu) + amm_sigma.max(anchor_sigma) * 4.0;
    let samples = 56_usize;
    let mut points = Vec::with_capacity(samples + 1);

    for step in 0..=samples {
        let x = lower + (upper - lower) * step as f64 / samples as f64;
        let x_fixed = Fixed::from_f64(x)?;
        let amm_value = fixed_calculate_f(x_fixed, amm_distribution, k)?;
        let anchor_value = fixed_calculate_f(x_fixed, anchor_distribution, k)?;
        points.push(DemoPerpCurvePointV1 {
            x: Fixed::from_f64(x)?.to_string(),
            amm: amm_value.to_string(),
            anchor: anchor_value.to_string(),
            edge: (amm_value - anchor_value).to_string(),
        });
    }

    Ok(points)
}

fn build_perp_funding_path(
    anchor_mu: f64,
    anchor_sigma: f64,
    funding_interval: u64,
) -> Result<Vec<DemoPerpFundingPointV1>, String> {
    let slots = [0_u64, 4, 8, 12, 16, 20, 24, 30];
    let mut points = Vec::with_capacity(slots.len());
    for (index, slot) in slots.iter().enumerate() {
        let progress = index as f64 / (slots.len() - 1) as f64;
        let amm_mu = 91.5 + (96.4 - 91.5) * progress;
        let kl = normal_kl(amm_mu, 10.0, anchor_mu, anchor_sigma);
        let rate = demo_perp_funding_rate(kl, funding_interval);
        points.push(DemoPerpFundingPointV1 {
            slot: *slot,
            amm_mu_display: fixed_display(amm_mu)?,
            anchor_mu_display: fixed_display(anchor_mu)?,
            kl_display: fixed_display(kl)?,
            funding_rate_display: fixed_display(rate)?,
        });
    }
    Ok(points)
}

fn demo_perp_mark_payout(
    program: &NormalV1Program,
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
    let kl_clamped = kl.min(10.0);
    if kl_clamped < 0.001 {
        0.0
    } else {
        200.0 / 10_000.0 * kl_clamped / funding_interval as f64
    }
}

fn fixed_display(value: f64) -> Result<String, String> {
    Ok(Fixed::from_f64(value)?.to_string())
}

fn regime_constituent_side_label(side: RegimeConstituentSide) -> &'static str {
    match side {
        RegimeConstituentSide::Long => "Long",
        RegimeConstituentSide::Short => "Short",
    }
}

fn regime_constituent_status_label(status: RegimeConstituentStatus) -> &'static str {
    match status {
        RegimeConstituentStatus::Active => "Active",
        RegimeConstituentStatus::Resolved => "Resolved",
        RegimeConstituentStatus::PendingSpawn => "Pending spawn",
    }
}

fn regime_token_side_label(side: RegimeTokenSide) -> &'static str {
    match side {
        RegimeTokenSide::Long => "Long",
        RegimeTokenSide::Short => "Short",
    }
}

fn fixed_percent(value: Fixed) -> Fixed {
    value * Fixed::from_raw(100 * Fixed::SCALE)
}

fn encode_hex(bytes: &[u8]) -> String {
    let mut value = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        value.push(hex_nibble(byte >> 4));
        value.push(hex_nibble(byte & 0x0f));
    }
    value
}

fn escape_json(value: &str) -> String {
    value.replace('\\', "\\\\").replace('"', "\\\"")
}

fn hex_nibble(value: u8) -> char {
    match value {
        0..=9 => (b'0' + value) as char,
        10..=15 => (b'a' + (value - 10)) as char,
        _ => '0',
    }
}

#[cfg(test)]
mod tests {
    use super::{
        DEMO_MARKET_ID, DEMO_QUOTE_EXPIRY_SLOT, DEMO_QUOTE_SLOT, TradeQuoteRequestV1,
        android_trade_intent, build_trade_quote, demo_app_payload, demo_app_payload_json,
        demo_market_summary, preview_trade_token_operation, seeded_demo_market,
    };
    use distribution_markets::{Fixed, FixedNormalDistribution};
    use normal_v1_program::unpack_instruction;

    fn distribution(mu: f64, sigma: f64) -> FixedNormalDistribution {
        FixedNormalDistribution::new(
            Fixed::from_f64(mu).unwrap(),
            Fixed::from_f64(sigma).unwrap(),
        )
        .unwrap()
    }

    #[test]
    fn sdk_builds_trade_quote_and_serialized_instruction() {
        let program = seeded_demo_market().unwrap();
        let quote = build_trade_quote(
            &program,
            TradeQuoteRequestV1 {
                trader: [8_u8; 32],
                market: DEMO_MARKET_ID,
                target_distribution: distribution(100.0, 10.0),
                quote_slot: DEMO_QUOTE_SLOT,
                quote_expiry_slot: DEMO_QUOTE_EXPIRY_SLOT,
            },
        )
        .unwrap();

        assert!(quote.collateral_required.raw() > 0);
        assert!(quote.fee_paid.raw() > 0);
        assert_eq!(
            quote.total_debit,
            quote.collateral_required + quote.fee_paid
        );
        let unpacked = unpack_instruction(&quote.serialized_instruction).unwrap();
        assert!(matches!(
            unpacked,
            distribution_markets::SolanaInstructionV1::Trade(_)
        ));
    }

    #[test]
    fn sdk_exposes_android_ready_trade_intent() {
        let program = seeded_demo_market().unwrap();
        let quote = build_trade_quote(
            &program,
            TradeQuoteRequestV1 {
                trader: [8_u8; 32],
                market: DEMO_MARKET_ID,
                target_distribution: distribution(100.0, 10.0),
                quote_slot: DEMO_QUOTE_SLOT,
                quote_expiry_slot: DEMO_QUOTE_EXPIRY_SLOT,
            },
        )
        .unwrap();

        let intent = android_trade_intent(&quote);
        assert!(!intent.serialized_instruction_hex.is_empty());
        assert_eq!(intent.mu_display, "100.000000000");
    }

    #[test]
    fn sdk_previews_market_and_token_flow() {
        let program = seeded_demo_market().unwrap();
        let summary = demo_market_summary(&program);
        assert!(summary.contains("Normal market"));

        let quote = build_trade_quote(
            &program,
            TradeQuoteRequestV1 {
                trader: [8_u8; 32],
                market: DEMO_MARKET_ID,
                target_distribution: distribution(100.0, 10.0),
                quote_slot: DEMO_QUOTE_SLOT,
                quote_expiry_slot: DEMO_QUOTE_EXPIRY_SLOT,
            },
        )
        .unwrap();
        let operations = preview_trade_token_operation(&quote);
        assert_eq!(operations.len(), 1);
    }

    #[test]
    fn sdk_emits_demo_payload_for_mobile_app() {
        let payload = demo_app_payload().unwrap();
        assert_eq!(payload.market.title, "Seeded SOL price market");
        assert_eq!(payload.presets.len(), 3);
        assert_eq!(payload.quote_grid.len(), 14);
        assert_eq!(payload.regime_indexes.len(), 3);
        assert_eq!(payload.perps.symbol, "SOL-PERP");
        assert_eq!(payload.perps.positions.len(), 2);
        assert_eq!(payload.regime_indexes[0].symbol, "HAWKFED");
        assert_eq!(payload.regime_indexes[0].constituents.len(), 3);
        assert_eq!(payload.liquidity.open_trades, 0);
        assert_eq!(payload.preview_liquidity.open_trades, 1);
        assert_eq!(payload.risk_grid.len(), 25);
        let json = demo_app_payload_json().unwrap();
        assert!(json.contains("\"presets\""));
        assert!(json.contains("\"quote_grid\""));
        assert!(json.contains("\"regime_indexes\""));
        assert!(json.contains("\"perps\""));
        assert!(json.contains("\"funding_path\""));
        assert!(json.contains("\"liquidity\""));
        assert!(json.contains("\"backend_trace\""));
        assert!(json.contains("\"settlement_waterfall\""));
        assert!(json.contains("\"long_quote\""));
        assert!(json.contains("\"curve_points\""));
        assert!(json.contains("\"total_debit_display\""));
        assert!(json.contains("serialized_instruction_hex"));
    }
}
