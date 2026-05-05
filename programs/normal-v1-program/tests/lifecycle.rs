use distribution_markets::{
    Fixed, FixedNormalDistribution, InitializeAccountsV1, LiquidityAction, OracleConfigV1,
    QuoteEnvelopeV1, ResolveMarketArgsV1, SettleLpArgsV1, SettlePositionArgsV1,
    SolanaInstructionV1, TradeArgsV1,
};
use normal_v1_program::{
    NormalV1Program, ProgramInitializeArgsV1, ProgramTokenOperationV1, pack_instruction,
    unpack_market_account, unpack_position_account,
};

fn fixed(value: f64) -> Fixed {
    Fixed::from_f64(value).unwrap()
}

fn distribution(mu: f64, sigma: f64) -> FixedNormalDistribution {
    FixedNormalDistribution::new(fixed(mu), fixed(sigma)).unwrap()
}

fn init_accounts() -> InitializeAccountsV1 {
    InitializeAccountsV1 {
        initializer: [9_u8; 32],
        collateral_mint: [1_u8; 32],
        collateral_vault: [2_u8; 32],
        lp_mint: [3_u8; 32],
        market_authority: [4_u8; 32],
    }
}

fn oracle() -> OracleConfigV1 {
    OracleConfigV1 {
        oracle_program: [5_u8; 32],
        oracle_feed: [6_u8; 32],
        authority: [7_u8; 32],
    }
}

#[test]
fn full_program_lifecycle_survives_serialized_instruction_flow() {
    let initializer = [9_u8; 32];
    let trader = [8_u8; 32];
    let oracle_authority = [7_u8; 32];

    let (mut program, init_execution) = NormalV1Program::initialize(
        ProgramInitializeArgsV1 {
            accounts: init_accounts(),
            initial_b: fixed(50.0),
            initial_k: fixed(21.05026039569057),
            initial_distribution: distribution(95.0, 10.0),
            oracle_config: oracle(),
        },
        1,
    )
    .unwrap();
    assert_eq!(init_execution.token_operations.len(), 2);

    let quote = program
        .state
        .core_market
        .quote_trade(distribution(100.0, 10.0))
        .unwrap();
    let trade_instruction = SolanaInstructionV1::Trade(TradeArgsV1 {
        quote: QuoteEnvelopeV1 {
            market: [4_u8; 32],
            expected_market_version: quote.market_version,
            new_distribution: quote.new_distribution,
            collateral_required: quote.collateral_quote.collateral_required,
            max_slippage_collateral: quote.collateral_quote.collateral_required,
            search_lower_bound: quote.collateral_quote.lower_bound,
            search_upper_bound: quote.collateral_quote.upper_bound,
            coarse_samples: quote.collateral_quote.coarse_samples,
            refine_samples: quote.collateral_quote.refine_samples,
            quote_slot: 2,
            quote_expiry_slot: 12,
        },
    });
    let trade_execution = program
        .execute_serialized(trader, 3, &pack_instruction(&trade_instruction))
        .unwrap();
    assert_eq!(
        trade_execution.token_operations,
        vec![ProgramTokenOperationV1::TransferToVault {
            owner: trader,
            amount: quote.collateral_quote.collateral_required,
        }]
    );

    let add_liquidity_instruction = SolanaInstructionV1::ManageLiquidity {
        action: LiquidityAction::Add,
        owner: initializer,
        amount_or_shares: fixed(5.0),
    };
    let add_execution = program
        .execute_serialized(initializer, 4, &pack_instruction(&add_liquidity_instruction))
        .unwrap();
    assert_eq!(add_execution.token_operations.len(), 2);

    let remove_liquidity_instruction = SolanaInstructionV1::ManageLiquidity {
        action: LiquidityAction::Remove,
        owner: initializer,
        amount_or_shares: fixed(0.1),
    };
    let remove_execution = program
        .execute_serialized(initializer, 5, &pack_instruction(&remove_liquidity_instruction))
        .unwrap();
    assert_eq!(remove_execution.token_operations.len(), 2);

    program
        .execute_serialized(
            oracle_authority,
            10,
            &pack_instruction(&SolanaInstructionV1::ResolveMarket(ResolveMarketArgsV1 {
                outcome: fixed(107.6),
                oracle_observation_slot: 10,
            })),
        )
        .unwrap();

    let settle_trade_execution = program
        .execute_serialized(
            trader,
            11,
            &pack_instruction(&SolanaInstructionV1::SettlePosition(SettlePositionArgsV1 {
                position_id: 1,
            })),
        )
        .unwrap();
    assert_eq!(settle_trade_execution.token_operations.len(), 1);

    let settle_lp_execution = program
        .execute_serialized(
            initializer,
            12,
            &pack_instruction(&SolanaInstructionV1::SettleLp(SettleLpArgsV1 {
                owner: initializer,
            })),
        )
        .unwrap();
    assert_eq!(settle_lp_execution.token_operations.len(), 2);

    let snapshot = program.snapshot();
    let market_account = unpack_market_account(&snapshot.market).unwrap();
    assert!(matches!(
        market_account.status,
        distribution_markets::MarketStatus::Resolved | distribution_markets::MarketStatus::Settled
    ));
    assert!(market_account.total_trades >= 1);

    let trade_position = unpack_position_account(&snapshot.positions[1]).unwrap();
    assert!(trade_position.settled);
}
