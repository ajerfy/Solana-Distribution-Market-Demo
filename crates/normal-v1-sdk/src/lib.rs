use distribution_markets::{
    Fixed, FixedNormalDistribution, FixedNormalTradeQuote, InitializeAccountsV1, OracleConfigV1,
    QuoteEnvelopeV1, SolanaInstructionV1, TradeArgsV1,
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
    pub target_distribution: FixedNormalDistribution,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AndroidTradeIntentV1 {
    pub serialized_instruction_hex: String,
    pub collateral_required_display: String,
    pub mu_display: String,
    pub sigma_display: String,
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
            max_slippage_collateral: quote.collateral_quote.collateral_required,
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
        target_distribution: request.target_distribution,
        quote,
        serialized_instruction,
    })
}

pub fn android_trade_intent(quote: &TraderQuoteEnvelopeV1) -> AndroidTradeIntentV1 {
    AndroidTradeIntentV1 {
        serialized_instruction_hex: encode_hex(&quote.serialized_instruction),
        collateral_required_display: quote.collateral_required.to_string(),
        mu_display: quote.target_distribution.mu.to_string(),
        sigma_display: quote.target_distribution.sigma.to_string(),
    }
}

pub fn preview_trade_token_operation(
    quote: &TraderQuoteEnvelopeV1,
) -> Vec<ProgramTokenOperationV1> {
    vec![ProgramTokenOperationV1::TransferToVault {
        owner: quote.trader,
        amount: quote.collateral_required,
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

fn encode_hex(bytes: &[u8]) -> String {
    let mut value = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        value.push(hex_nibble(byte >> 4));
        value.push(hex_nibble(byte & 0x0f));
    }
    value
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
        TradeQuoteRequestV1, android_trade_intent, build_trade_quote, demo_market_summary,
        preview_trade_token_operation, seeded_demo_market,
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
        let unpacked = unpack_instruction(&quote.serialized_instruction).unwrap();
        assert!(matches!(unpacked, distribution_markets::SolanaInstructionV1::Trade(_)));
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
}
