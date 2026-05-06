use distribution_markets::{
    Fixed, FixedNormalDistribution, FixedNormalTradeQuote, InitializeAccountsV1, OracleConfigV1,
    QuoteEnvelopeV1, SolanaInstructionV1, TradeArgsV1, fixed_calculate_f,
};
use normal_v1_program::{
    NormalV1Program, ProgramInitializeArgsV1, ProgramTokenOperationV1, pack_instruction,
};

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
    pub current_mu_display: String,
    pub current_sigma_display: String,
    pub backing_display: String,
    pub maker_fees_earned_display: String,
    pub maker_deposit_display: String,
    pub total_trades: u64,
    pub max_open_trades: u64,
    pub expiry_slot: u64,
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
pub struct DemoAppPayloadV1 {
    pub market: DemoMarketSnapshotV1,
    pub presets: Vec<DemoQuotePresetV1>,
    pub quote_grid: Vec<DemoQuotePresetV1>,
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
    let market = DemoMarketSnapshotV1 {
        title: "Seeded SOL price market".to_string(),
        status: format!("{:?}", program.state.market_account.status),
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
        backing_display: program.state.market_account.b.to_string(),
        maker_fees_earned_display: program.state.market_account.fees_accrued.to_string(),
        maker_deposit_display: program.state.market_account.b.to_string(),
        total_trades: program.state.market_account.total_trades,
        max_open_trades: program.state.market_account.max_open_trades,
        expiry_slot: program.state.market_account.expiry_slot,
    };

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

    Ok(DemoAppPayloadV1 {
        market,
        presets,
        quote_grid,
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
        "    \"current_mu_display\": \"{}\",\n",
        escape_json(&payload.market.current_mu_display)
    ));
    json.push_str(&format!(
        "    \"current_sigma_display\": \"{}\",\n",
        escape_json(&payload.market.current_sigma_display)
    ));
    json.push_str(&format!(
        "    \"backing_display\": \"{}\",\n",
        escape_json(&payload.market.backing_display)
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
        "    \"expiry_slot\": {}\n",
        payload.market.expiry_slot
    ));
    json.push_str("  },\n");
    json.push_str("  \"presets\": [\n");
    push_quote_list_json(&mut json, &payload.presets);
    json.push_str("  ],\n");
    json.push_str("  \"quote_grid\": [\n");
    push_quote_list_json(&mut json, &payload.quote_grid);
    json.push_str("  ]\n");
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
            market: [4_u8; 32],
            target_distribution,
            quote_slot: 2,
            quote_expiry_slot: 12,
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
        TradeQuoteRequestV1, android_trade_intent, build_trade_quote, demo_app_payload,
        demo_app_payload_json, demo_market_summary, preview_trade_token_operation,
        seeded_demo_market,
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
                market: [4_u8; 32],
                target_distribution: distribution(100.0, 10.0),
                quote_slot: 2,
                quote_expiry_slot: 12,
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
                market: [4_u8; 32],
                target_distribution: distribution(100.0, 10.0),
                quote_slot: 2,
                quote_expiry_slot: 12,
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
                market: [4_u8; 32],
                target_distribution: distribution(100.0, 10.0),
                quote_slot: 2,
                quote_expiry_slot: 12,
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
        let json = demo_app_payload_json().unwrap();
        assert!(json.contains("\"presets\""));
        assert!(json.contains("\"quote_grid\""));
        assert!(json.contains("\"curve_points\""));
        assert!(json.contains("\"total_debit_display\""));
        assert!(json.contains("serialized_instruction_hex"));
    }
}
