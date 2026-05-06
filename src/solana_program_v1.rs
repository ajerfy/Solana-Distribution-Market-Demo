use crate::fixed_point::Fixed;
use crate::normal_market::{FixedNormalMarket, FixedNormalTradeQuote};
use crate::normal_math::fixed_calculate_f;
use crate::solana_v1::{
    LiquidityAction, MarketStatus, NormalPositionSide, OracleConfigV1, QuoteEnvelopeV1,
    SolanaInstructionV1, SolanaMarketAccountV1, SolanaNormalPositionAccountV1,
    SolanaPositionSettlementV1, TradeArgsV1, current_normal_market_to_account,
};
use std::collections::HashSet;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct InitializeAccountsV1 {
    pub initializer: [u8; 32],
    pub collateral_mint: [u8; 32],
    pub collateral_vault: [u8; 32],
    pub lp_mint: [u8; 32],
    pub market_authority: [u8; 32],
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NormalV1ProgramState {
    pub market_account: SolanaMarketAccountV1,
    pub core_market: FixedNormalMarket,
    pub position_accounts: Vec<SolanaNormalPositionAccountV1>,
    pub settled_lp_owners: HashSet<[u8; 32]>,
    pub vault_balance: Fixed,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ProgramInstructionEffectV1 {
    MarketInitialized,
    TradeOpened {
        position_id: u64,
        collateral_posted: Fixed,
    },
    LiquidityAdded {
        owner: [u8; 32],
        amount_added: Fixed,
        shares_minted: Fixed,
    },
    LiquidityRemoved {
        owner: [u8; 32],
        shares_burned: Fixed,
        backing_removed: Fixed,
    },
    MarketResolved {
        outcome: Fixed,
    },
    PositionSettled(SolanaPositionSettlementV1),
    LpSettled {
        owner: [u8; 32],
        payout: Fixed,
    },
}

pub fn initialize_program_state(
    accounts: InitializeAccountsV1,
    initial_b: Fixed,
    initial_k: Fixed,
    initial_distribution: crate::normal_math::FixedNormalDistribution,
    oracle_config: OracleConfigV1,
    created_slot: u64,
) -> Result<NormalV1ProgramState, String> {
    let mut core_market = FixedNormalMarket::new(initial_b, initial_k, initial_distribution)?;
    let initializer_key = owner_key_string(accounts.initializer);
    core_market.lp_shares.remove("genesis_lp");
    core_market
        .lp_shares
        .insert(initializer_key, core_market.total_lp_shares);

    let mut market_account = current_normal_market_to_account(
        &core_market,
        accounts.collateral_mint,
        accounts.collateral_vault,
        accounts.lp_mint,
        accounts.market_authority,
        oracle_config,
    );
    market_account.created_slot = created_slot;

    let mut state = NormalV1ProgramState {
        market_account,
        core_market,
        position_accounts: Vec::new(),
        settled_lp_owners: HashSet::new(),
        vault_balance: initial_b,
    };
    sync_market_account(&mut state);
    upsert_liquidity_position(&mut state, accounts.initializer, created_slot)?;
    Ok(state)
}

pub fn process_instruction(
    state: &mut NormalV1ProgramState,
    signer: [u8; 32],
    current_slot: u64,
    instruction: SolanaInstructionV1,
) -> Result<ProgramInstructionEffectV1, String> {
    match instruction {
        SolanaInstructionV1::InitializeMarket { .. } => {
            Err("initialize_market must be handled by initialize_program_state".to_string())
        }
        SolanaInstructionV1::Trade(args) => process_trade(state, signer, current_slot, args),
        SolanaInstructionV1::ManageLiquidity {
            action,
            owner,
            amount_or_shares,
        } => {
            if owner != signer {
                return Err("liquidity instruction signer does not match owner".to_string());
            }
            match action {
                LiquidityAction::Add => {
                    process_add_liquidity(state, owner, amount_or_shares, current_slot)
                }
                LiquidityAction::Remove => {
                    process_remove_liquidity(state, owner, amount_or_shares, current_slot)
                }
            }
        }
        SolanaInstructionV1::ResolveMarket(args) => {
            process_resolve_market(state, signer, current_slot, args)
        }
        SolanaInstructionV1::SettlePosition(args) => {
            process_settle_position(state, signer, current_slot, args.position_id)
        }
        SolanaInstructionV1::SettleLp(args) => {
            if args.owner != signer {
                return Err("lp settlement signer does not match owner".to_string());
            }
            process_settle_lp(state, signer, current_slot)
        }
    }
}

fn process_trade(
    state: &mut NormalV1ProgramState,
    signer: [u8; 32],
    current_slot: u64,
    args: TradeArgsV1,
) -> Result<ProgramInstructionEffectV1, String> {
    ensure_active_market(state)?;
    if current_slot > args.quote.quote_expiry_slot {
        return Err("trade quote has expired".to_string());
    }

    let old_distribution = state.core_market.current_distribution;
    let quote = fixed_quote_from_envelope(&args.quote);
    let collateral = state.core_market.trade_with_quote(quote)?;
    state.vault_balance = state.core_market.cash;

    let position_id = state.position_accounts.len() as u64;
    state.position_accounts.push(SolanaNormalPositionAccountV1 {
        version: 1,
        bump: 0,
        market: state.market_account.market_authority,
        owner: signer,
        side: NormalPositionSide::Trade,
        id: position_id,
        old_distribution,
        new_distribution: args.quote.new_distribution,
        collateral_posted: collateral,
        k_at_trade: state.core_market.k,
        lp_shares: Fixed::ZERO,
        settled: false,
        payout_claimed: Fixed::ZERO,
        created_slot: current_slot,
        settled_slot: None,
    });

    sync_market_account(state);
    Ok(ProgramInstructionEffectV1::TradeOpened {
        position_id,
        collateral_posted: collateral,
    })
}

fn process_add_liquidity(
    state: &mut NormalV1ProgramState,
    owner: [u8; 32],
    amount_added: Fixed,
    current_slot: u64,
) -> Result<ProgramInstructionEffectV1, String> {
    ensure_active_market(state)?;
    if amount_added.raw() <= 0 {
        return Err("liquidity amount must be positive".to_string());
    }

    let proportion = amount_added / state.core_market.b;
    let minted = state.core_market.add_liquidity(owner_key_string(owner), proportion)?;
    state.vault_balance = state.core_market.cash;
    upsert_liquidity_position(state, owner, current_slot)?;
    sync_market_account(state);

    Ok(ProgramInstructionEffectV1::LiquidityAdded {
        owner,
        amount_added,
        shares_minted: minted,
    })
}

fn process_remove_liquidity(
    state: &mut NormalV1ProgramState,
    owner: [u8; 32],
    shares: Fixed,
    current_slot: u64,
) -> Result<ProgramInstructionEffectV1, String> {
    ensure_active_market(state)?;
    let backing_removed = state
        .core_market
        .remove_liquidity(&owner_key_string(owner), shares)?;
    state.vault_balance = state.core_market.cash;
    upsert_liquidity_position(state, owner, current_slot)?;
    sync_market_account(state);

    Ok(ProgramInstructionEffectV1::LiquidityRemoved {
        owner,
        shares_burned: shares,
        backing_removed,
    })
}

fn process_resolve_market(
    state: &mut NormalV1ProgramState,
    signer: [u8; 32],
    current_slot: u64,
    args: crate::solana_v1::ResolveMarketArgsV1,
) -> Result<ProgramInstructionEffectV1, String> {
    ensure_active_market(state)?;
    if signer != state.market_account.oracle_config.authority {
        return Err("resolve_market signer does not match oracle authority".to_string());
    }

    state.market_account.status = MarketStatus::Resolved;
    state.market_account.resolved_outcome = Some(args.outcome);
    state.market_account.resolved_slot = Some(current_slot);

    Ok(ProgramInstructionEffectV1::MarketResolved {
        outcome: args.outcome,
    })
}

fn process_settle_position(
    state: &mut NormalV1ProgramState,
    signer: [u8; 32],
    current_slot: u64,
    position_id: u64,
) -> Result<ProgramInstructionEffectV1, String> {
    ensure_resolved_market(state)?;
    let outcome = state
        .market_account
        .resolved_outcome
        .ok_or_else(|| "market outcome is missing".to_string())?;

    // Validate and compute payout without mutating any state.
    let (payout, collateral_returned) = {
        let position = state
            .position_accounts
            .iter()
            .find(|position| position.id == position_id)
            .ok_or_else(|| "unknown position id".to_string())?;

        if position.side != NormalPositionSide::Trade {
            return Err("position id does not refer to a trader position".to_string());
        }
        if position.owner != signer {
            return Err("settle_position signer does not match owner".to_string());
        }
        if position.settled {
            return Err("position already settled".to_string());
        }

        let final_payout = fixed_calculate_f(outcome, position.new_distribution, position.k_at_trade)?;
        let initial_payout = fixed_calculate_f(outcome, position.old_distribution, position.k_at_trade)?;
        let payout = position.collateral_posted + (final_payout - initial_payout);
        if payout.raw() < 0 {
            return Err("negative payout encountered during settlement".to_string());
        }

        (payout, position.collateral_posted)
    };

    if payout.raw() > state.vault_balance.raw() {
        return Err("vault cannot satisfy position settlement".to_string());
    }

    // All validation passed — mutate now.
    let position = state
        .position_accounts
        .iter_mut()
        .find(|position| position.id == position_id)
        .unwrap();
    position.settled = true;
    position.payout_claimed = payout;
    position.settled_slot = Some(current_slot);

    state.vault_balance = state.vault_balance - payout;
    update_market_status_if_fully_settled(state);

    Ok(ProgramInstructionEffectV1::PositionSettled(
        SolanaPositionSettlementV1 {
            position_id,
            payout,
            collateral_returned,
        },
    ))
}

fn process_settle_lp(
    state: &mut NormalV1ProgramState,
    owner: [u8; 32],
    current_slot: u64,
) -> Result<ProgramInstructionEffectV1, String> {
    ensure_resolved_market(state)?;
    if state.settled_lp_owners.contains(&owner) {
        return Err("lp owner already settled".to_string());
    }

    let owner_key = owner_key_string(owner);
    let owner_shares = state
        .core_market
        .lp_shares
        .get(&owner_key)
        .copied()
        .unwrap_or(Fixed::ZERO);
    if owner_shares.raw() <= 0 {
        return Err("owner has no LP shares to settle".to_string());
    }

    let mut unsettled_total = Fixed::ZERO;
    for (lp_id, shares) in &state.core_market.lp_shares {
        if !state.settled_lp_owners.contains(&decode_owner_key(lp_id)?) {
            unsettled_total = unsettled_total + *shares;
        }
    }
    if unsettled_total.raw() <= 0 {
        return Err("no unsettled LP shares remain".to_string());
    }

    let payout = state.vault_balance * (owner_shares / unsettled_total);
    state.vault_balance = state.vault_balance - payout;
    state.settled_lp_owners.insert(owner);

    if let Some(position) = state
        .position_accounts
        .iter_mut()
        .find(|position| position.side == NormalPositionSide::Liquidity && position.owner == owner)
    {
        position.settled = true;
        position.payout_claimed = payout;
        position.settled_slot = Some(current_slot);
    }

    update_market_status_if_fully_settled(state);
    Ok(ProgramInstructionEffectV1::LpSettled { owner, payout })
}

fn ensure_active_market(state: &NormalV1ProgramState) -> Result<(), String> {
    if state.market_account.status != MarketStatus::Active {
        return Err("market is not active".to_string());
    }
    Ok(())
}

fn ensure_resolved_market(state: &NormalV1ProgramState) -> Result<(), String> {
    if state.market_account.status != MarketStatus::Resolved
        && state.market_account.status != MarketStatus::Settled
    {
        return Err("market is not resolved".to_string());
    }
    Ok(())
}

fn fixed_quote_from_envelope(quote: &QuoteEnvelopeV1) -> FixedNormalTradeQuote {
    FixedNormalTradeQuote {
        market_version: quote.expected_market_version,
        new_distribution: quote.new_distribution,
        collateral_quote: crate::normal_math::FixedCollateralQuote {
            collateral_required: quote.collateral_required,
            lower_bound: quote.search_lower_bound,
            upper_bound: quote.search_upper_bound,
            coarse_samples: quote.coarse_samples,
            refine_samples: quote.refine_samples,
        },
    }
}

fn sync_market_account(state: &mut NormalV1ProgramState) {
    state.market_account.b = state.core_market.b;
    state.market_account.k = state.core_market.k;
    state.market_account.current_distribution = state.core_market.current_distribution;
    state.market_account.current_lambda = state.core_market.current_lambda;
    state.market_account.total_lp_shares = state.core_market.total_lp_shares;
    state.market_account.state_version = state.core_market.state_version;
    state.market_account.total_trades = state
        .position_accounts
        .iter()
        .filter(|position| position.side == NormalPositionSide::Trade)
        .count() as u64;
}

fn upsert_liquidity_position(
    state: &mut NormalV1ProgramState,
    owner: [u8; 32],
    current_slot: u64,
) -> Result<(), String> {
    let lp_shares = state
        .core_market
        .lp_shares
        .get(&owner_key_string(owner))
        .copied()
        .unwrap_or(Fixed::ZERO);

    if let Some(position) = state
        .position_accounts
        .iter_mut()
        .find(|position| position.side == NormalPositionSide::Liquidity && position.owner == owner)
    {
        position.lp_shares = lp_shares;
        position.new_distribution = state.core_market.current_distribution;
        return Ok(());
    }

    state.position_accounts.push(SolanaNormalPositionAccountV1 {
        version: 1,
        bump: 0,
        market: state.market_account.market_authority,
        owner,
        side: NormalPositionSide::Liquidity,
        id: state.position_accounts.len() as u64,
        old_distribution: state.core_market.current_distribution,
        new_distribution: state.core_market.current_distribution,
        collateral_posted: Fixed::ZERO,
        k_at_trade: Fixed::ZERO,
        lp_shares,
        settled: false,
        payout_claimed: Fixed::ZERO,
        created_slot: current_slot,
        settled_slot: None,
    });
    Ok(())
}

fn update_market_status_if_fully_settled(state: &mut NormalV1ProgramState) {
    let all_trades_settled = state
        .position_accounts
        .iter()
        .filter(|position| position.side == NormalPositionSide::Trade)
        .all(|position| position.settled);

    let total_lp_owner_count = state
        .core_market
        .lp_shares
        .values()
        .filter(|shares| shares.raw() > 0)
        .count();

    if all_trades_settled && state.settled_lp_owners.len() == total_lp_owner_count {
        state.market_account.status = MarketStatus::Settled;
    }
}

fn owner_key_string(owner: [u8; 32]) -> String {
    let mut value = String::with_capacity(64);
    for byte in owner {
        value.push(nibble_to_hex(byte >> 4));
        value.push(nibble_to_hex(byte & 0x0f));
    }
    value
}

fn decode_owner_key(value: &str) -> Result<[u8; 32], String> {
    if value.len() != 64 {
        return Err("owner key encoding must be 64 hex characters".to_string());
    }

    let mut owner = [0_u8; 32];
    for index in 0..32 {
        let high = hex_to_nibble(value.as_bytes()[index * 2] as char)?;
        let low = hex_to_nibble(value.as_bytes()[index * 2 + 1] as char)?;
        owner[index] = (high << 4) | low;
    }
    Ok(owner)
}

fn nibble_to_hex(value: u8) -> char {
    match value {
        0..=9 => (b'0' + value) as char,
        10..=15 => (b'a' + (value - 10)) as char,
        _ => '0',
    }
}

fn hex_to_nibble(value: char) -> Result<u8, String> {
    match value {
        '0'..='9' => Ok((value as u8) - b'0'),
        'a'..='f' => Ok((value as u8) - b'a' + 10),
        'A'..='F' => Ok((value as u8) - b'A' + 10),
        _ => Err("invalid hex owner key".to_string()),
    }
}

#[cfg(test)]
mod tests {
    use super::{
        InitializeAccountsV1, ProgramInstructionEffectV1, initialize_program_state,
        process_instruction,
    };
    use crate::fixed_point::Fixed;
    use crate::normal_math::FixedNormalDistribution;
    use crate::solana_v1::{
        LiquidityAction, OracleConfigV1, QuoteEnvelopeV1, ResolveMarketArgsV1,
        SettleLpArgsV1, SettlePositionArgsV1, SolanaInstructionV1, TradeArgsV1,
    };

    fn normal_distribution(mu: f64, sigma: f64) -> FixedNormalDistribution {
        FixedNormalDistribution::new(
            Fixed::from_f64(mu).unwrap(),
            Fixed::from_f64(sigma).unwrap(),
        )
        .unwrap()
    }

    fn sample_accounts() -> InitializeAccountsV1 {
        InitializeAccountsV1 {
            initializer: [9_u8; 32],
            collateral_mint: [1_u8; 32],
            collateral_vault: [2_u8; 32],
            lp_mint: [3_u8; 32],
            market_authority: [4_u8; 32],
        }
    }

    fn sample_oracle() -> OracleConfigV1 {
        OracleConfigV1 {
            oracle_program: [5_u8; 32],
            oracle_feed: [6_u8; 32],
            authority: [7_u8; 32],
        }
    }

    #[test]
    fn initialize_creates_active_program_state() {
        let state = initialize_program_state(
            sample_accounts(),
            Fixed::from_f64(50.0).unwrap(),
            Fixed::from_f64(21.05026039569057).unwrap(),
            normal_distribution(95.0, 10.0),
            sample_oracle(),
            42,
        )
        .unwrap();

        assert_eq!(state.market_account.status, crate::solana_v1::MarketStatus::Active);
        assert_eq!(state.market_account.created_slot, 42);
        assert_eq!(state.vault_balance, Fixed::from_f64(50.0).unwrap());
        assert_eq!(state.position_accounts.len(), 1);
    }

    #[test]
    fn trade_handler_opens_position_from_quote() {
        let mut state = initialize_program_state(
            sample_accounts(),
            Fixed::from_f64(50.0).unwrap(),
            Fixed::from_f64(21.05026039569057).unwrap(),
            normal_distribution(95.0, 10.0),
            sample_oracle(),
            1,
        )
        .unwrap();
        let quoted = state.core_market.quote_trade(normal_distribution(100.0, 10.0)).unwrap();

        let result = process_instruction(
            &mut state,
            [8_u8; 32],
            5,
            SolanaInstructionV1::Trade(TradeArgsV1 {
                quote: QuoteEnvelopeV1 {
                    market: [4_u8; 32],
                    expected_market_version: quoted.market_version,
                    new_distribution: quoted.new_distribution,
                    collateral_required: quoted.collateral_quote.collateral_required,
                    max_slippage_collateral: quoted.collateral_quote.collateral_required,
                    search_lower_bound: quoted.collateral_quote.lower_bound,
                    search_upper_bound: quoted.collateral_quote.upper_bound,
                    coarse_samples: quoted.collateral_quote.coarse_samples,
                    refine_samples: quoted.collateral_quote.refine_samples,
                    quote_slot: 4,
                    quote_expiry_slot: 10,
                },
            }),
        )
        .unwrap();

        match result {
            ProgramInstructionEffectV1::TradeOpened { position_id, .. } => {
                assert_eq!(position_id, 1);
            }
            _ => panic!("expected trade opened effect"),
        }
        assert_eq!(state.market_account.total_trades, 1);
    }

    #[test]
    fn liquidity_and_resolution_handlers_round_trip() {
        let initializer = [9_u8; 32];
        let trader = [8_u8; 32];
        let oracle = [7_u8; 32];
        let mut state = initialize_program_state(
            sample_accounts(),
            Fixed::from_f64(50.0).unwrap(),
            Fixed::from_f64(21.05026039569057).unwrap(),
            normal_distribution(95.0, 10.0),
            sample_oracle(),
            1,
        )
        .unwrap();

        let quoted = state.core_market.quote_trade(normal_distribution(100.0, 10.0)).unwrap();
        process_instruction(
            &mut state,
            trader,
            5,
            SolanaInstructionV1::Trade(TradeArgsV1 {
                quote: QuoteEnvelopeV1 {
                    market: [4_u8; 32],
                    expected_market_version: quoted.market_version,
                    new_distribution: quoted.new_distribution,
                    collateral_required: quoted.collateral_quote.collateral_required,
                    max_slippage_collateral: quoted.collateral_quote.collateral_required,
                    search_lower_bound: quoted.collateral_quote.lower_bound,
                    search_upper_bound: quoted.collateral_quote.upper_bound,
                    coarse_samples: quoted.collateral_quote.coarse_samples,
                    refine_samples: quoted.collateral_quote.refine_samples,
                    quote_slot: 4,
                    quote_expiry_slot: 10,
                },
            }),
        )
        .unwrap();

        process_instruction(
            &mut state,
            initializer,
            6,
            SolanaInstructionV1::ManageLiquidity {
                action: LiquidityAction::Add,
                owner: initializer,
                amount_or_shares: Fixed::from_f64(5.0).unwrap(),
            },
        )
        .unwrap();

        process_instruction(
            &mut state,
            oracle,
            10,
            SolanaInstructionV1::ResolveMarket(ResolveMarketArgsV1 {
                outcome: Fixed::from_f64(107.6).unwrap(),
                oracle_observation_slot: 10,
            }),
        )
        .unwrap();

        let trade_result = process_instruction(
            &mut state,
            trader,
            11,
            SolanaInstructionV1::SettlePosition(SettlePositionArgsV1 { position_id: 1 }),
        )
        .unwrap();
        assert!(matches!(
            trade_result,
            ProgramInstructionEffectV1::PositionSettled(_)
        ));

        let lp_result = process_instruction(
            &mut state,
            initializer,
            12,
            SolanaInstructionV1::SettleLp(SettleLpArgsV1 { owner: initializer }),
        )
        .unwrap();
        assert!(matches!(lp_result, ProgramInstructionEffectV1::LpSettled { .. }));
        assert!(state.vault_balance.raw() >= 0);
    }

    #[test]
    fn expired_quote_is_rejected() {
        let mut state = initialize_program_state(
            sample_accounts(),
            Fixed::from_f64(50.0).unwrap(),
            Fixed::from_f64(21.05026039569057).unwrap(),
            normal_distribution(95.0, 10.0),
            sample_oracle(),
            1,
        )
        .unwrap();
        let quoted = state.core_market.quote_trade(normal_distribution(100.0, 10.0)).unwrap();

        let error = process_instruction(
            &mut state,
            [8_u8; 32],
            20,
            SolanaInstructionV1::Trade(TradeArgsV1 {
                quote: QuoteEnvelopeV1 {
                    market: [4_u8; 32],
                    expected_market_version: quoted.market_version,
                    new_distribution: quoted.new_distribution,
                    collateral_required: quoted.collateral_quote.collateral_required,
                    max_slippage_collateral: quoted.collateral_quote.collateral_required,
                    search_lower_bound: quoted.collateral_quote.lower_bound,
                    search_upper_bound: quoted.collateral_quote.upper_bound,
                    coarse_samples: quoted.collateral_quote.coarse_samples,
                    refine_samples: quoted.collateral_quote.refine_samples,
                    quote_slot: 4,
                    quote_expiry_slot: 10,
                },
            }),
        )
        .unwrap_err();

        assert!(error.contains("expired"));
    }
}
