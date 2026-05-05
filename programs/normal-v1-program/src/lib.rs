use distribution_markets::{
    Fixed, FixedNormalDistribution, InitializeAccountsV1, MarketStatus, NormalPositionSide,
    NormalV1ProgramState, OracleConfigV1, ProgramInstructionEffectV1, QuoteEnvelopeV1,
    ResolveMarketArgsV1, SettleLpArgsV1, SettlePositionArgsV1, SolanaInstructionV1,
    SolanaMarketAccountV1, SolanaNormalPositionAccountV1, TradeArgsV1, initialize_program_state,
    process_instruction,
};

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ProgramInitializeArgsV1 {
    pub accounts: InitializeAccountsV1,
    pub initial_b: Fixed,
    pub initial_k: Fixed,
    pub initial_distribution: FixedNormalDistribution,
    pub oracle_config: OracleConfigV1,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ProgramTokenOperationV1 {
    TransferToVault { owner: [u8; 32], amount: Fixed },
    TransferFromVault { owner: [u8; 32], amount: Fixed },
    MintLpShares { owner: [u8; 32], shares: Fixed },
    BurnLpShares { owner: [u8; 32], shares: Fixed },
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ProgramExecutionV1 {
    pub effect: ProgramInstructionEffectV1,
    pub token_operations: Vec<ProgramTokenOperationV1>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AccountSnapshotV1 {
    pub market: Vec<u8>,
    pub positions: Vec<Vec<u8>>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NormalV1Program {
    pub state: NormalV1ProgramState,
}

impl NormalV1Program {
    pub fn initialize(
        args: ProgramInitializeArgsV1,
        created_slot: u64,
    ) -> Result<(Self, ProgramExecutionV1), String> {
        let state = initialize_program_state(
            args.accounts.clone(),
            args.initial_b,
            args.initial_k,
            args.initial_distribution,
            args.oracle_config,
            created_slot,
        )?;

        let execution = ProgramExecutionV1 {
            effect: ProgramInstructionEffectV1::MarketInitialized,
            token_operations: vec![
                ProgramTokenOperationV1::TransferToVault {
                    owner: args.accounts.initializer,
                    amount: args.initial_b,
                },
                ProgramTokenOperationV1::MintLpShares {
                    owner: args.accounts.initializer,
                    shares: state.core_market.total_lp_shares,
                },
            ],
        };

        Ok((Self { state }, execution))
    }

    pub fn execute_serialized(
        &mut self,
        signer: [u8; 32],
        current_slot: u64,
        instruction_data: &[u8],
    ) -> Result<ProgramExecutionV1, String> {
        let instruction = unpack_instruction(instruction_data)?;
        let token_operations = planned_token_operations(&self.state, signer, &instruction)?;
        let effect = process_instruction(&mut self.state, signer, current_slot, instruction)?;
        Ok(ProgramExecutionV1 {
            effect,
            token_operations,
        })
    }

    pub fn snapshot(&self) -> AccountSnapshotV1 {
        AccountSnapshotV1 {
            market: pack_market_account(&self.state.market_account),
            positions: self
                .state
                .position_accounts
                .iter()
                .map(pack_position_account)
                .collect(),
        }
    }
}

pub fn pack_instruction(instruction: &SolanaInstructionV1) -> Vec<u8> {
    let mut buffer = Vec::new();
    match instruction {
        SolanaInstructionV1::InitializeMarket {
            initial_b,
            initial_k,
            initial_distribution,
            oracle_config,
        } => {
            buffer.push(0);
            push_fixed(&mut buffer, *initial_b);
            push_fixed(&mut buffer, *initial_k);
            push_distribution(&mut buffer, *initial_distribution);
            push_oracle_config(&mut buffer, oracle_config);
        }
        SolanaInstructionV1::Trade(TradeArgsV1 { quote }) => {
            buffer.push(1);
            push_quote_envelope(&mut buffer, quote);
        }
        SolanaInstructionV1::ManageLiquidity {
            action,
            owner,
            amount_or_shares,
        } => {
            buffer.push(2);
            buffer.push(match action {
                distribution_markets::LiquidityAction::Add => 0,
                distribution_markets::LiquidityAction::Remove => 1,
            });
            buffer.extend_from_slice(owner);
            push_fixed(&mut buffer, *amount_or_shares);
        }
        SolanaInstructionV1::ResolveMarket(ResolveMarketArgsV1 {
            outcome,
            oracle_observation_slot,
        }) => {
            buffer.push(3);
            push_fixed(&mut buffer, *outcome);
            push_u64(&mut buffer, *oracle_observation_slot);
        }
        SolanaInstructionV1::SettlePosition(SettlePositionArgsV1 { position_id }) => {
            buffer.push(4);
            push_u64(&mut buffer, *position_id);
        }
        SolanaInstructionV1::SettleLp(SettleLpArgsV1 { owner }) => {
            buffer.push(5);
            buffer.extend_from_slice(owner);
        }
    }
    buffer
}

pub fn unpack_instruction(bytes: &[u8]) -> Result<SolanaInstructionV1, String> {
    let mut cursor = Cursor::new(bytes);
    let tag = cursor.read_u8()?;
    match tag {
        0 => Ok(SolanaInstructionV1::InitializeMarket {
            initial_b: cursor.read_fixed()?,
            initial_k: cursor.read_fixed()?,
            initial_distribution: cursor.read_distribution()?,
            oracle_config: cursor.read_oracle_config()?,
        }),
        1 => Ok(SolanaInstructionV1::Trade(TradeArgsV1 {
            quote: cursor.read_quote_envelope()?,
        })),
        2 => {
            let action = match cursor.read_u8()? {
                0 => distribution_markets::LiquidityAction::Add,
                1 => distribution_markets::LiquidityAction::Remove,
                _ => return Err("invalid liquidity action tag".to_string()),
            };
            let owner = cursor.read_array_32()?;
            let amount_or_shares = cursor.read_fixed()?;
            Ok(SolanaInstructionV1::ManageLiquidity {
                action,
                owner,
                amount_or_shares,
            })
        }
        3 => Ok(SolanaInstructionV1::ResolveMarket(ResolveMarketArgsV1 {
            outcome: cursor.read_fixed()?,
            oracle_observation_slot: cursor.read_u64()?,
        })),
        4 => Ok(SolanaInstructionV1::SettlePosition(SettlePositionArgsV1 {
            position_id: cursor.read_u64()?,
        })),
        5 => Ok(SolanaInstructionV1::SettleLp(SettleLpArgsV1 {
            owner: cursor.read_array_32()?,
        })),
        _ => Err("invalid instruction tag".to_string()),
    }
}

pub fn pack_market_account(account: &SolanaMarketAccountV1) -> Vec<u8> {
    let mut buffer = Vec::new();
    buffer.push(account.version);
    buffer.push(account.bump);
    buffer.push(pack_market_status(account.status));
    buffer.extend_from_slice(&account.collateral_mint);
    buffer.extend_from_slice(&account.collateral_vault);
    buffer.extend_from_slice(&account.lp_mint);
    buffer.extend_from_slice(&account.market_authority);
    push_oracle_config(&mut buffer, &account.oracle_config);
    push_fixed(&mut buffer, account.b);
    push_fixed(&mut buffer, account.k);
    push_distribution(&mut buffer, account.current_distribution);
    push_fixed(&mut buffer, account.current_lambda);
    push_fixed(&mut buffer, account.total_lp_shares);
    push_u64(&mut buffer, account.total_trades);
    push_option_fixed(&mut buffer, account.resolved_outcome);
    push_u64(&mut buffer, account.created_slot);
    push_option_u64(&mut buffer, account.resolved_slot);
    buffer
}

pub fn unpack_market_account(bytes: &[u8]) -> Result<SolanaMarketAccountV1, String> {
    let mut cursor = Cursor::new(bytes);
    let version = cursor.read_u8()?;
    let bump = cursor.read_u8()?;
    let status = unpack_market_status(cursor.read_u8()?)?;
    let collateral_mint = cursor.read_array_32()?;
    let collateral_vault = cursor.read_array_32()?;
    let lp_mint = cursor.read_array_32()?;
    let market_authority = cursor.read_array_32()?;
    let oracle_config = cursor.read_oracle_config()?;
    let b = cursor.read_fixed()?;
    let k = cursor.read_fixed()?;
    let current_distribution = cursor.read_distribution()?;
    let current_lambda = cursor.read_fixed()?;
    let total_lp_shares = cursor.read_fixed()?;
    let total_trades = cursor.read_u64()?;
    let resolved_outcome = cursor.read_option_fixed()?;
    let created_slot = cursor.read_u64()?;
    let resolved_slot = cursor.read_option_u64()?;
    cursor.ensure_consumed()?;
    Ok(SolanaMarketAccountV1 {
        version,
        bump,
        status,
        collateral_mint,
        collateral_vault,
        lp_mint,
        market_authority,
        oracle_config,
        b,
        k,
        current_distribution,
        current_lambda,
        total_lp_shares,
        total_trades,
        resolved_outcome,
        created_slot,
        resolved_slot,
    })
}

pub fn pack_position_account(account: &SolanaNormalPositionAccountV1) -> Vec<u8> {
    let mut buffer = Vec::new();
    buffer.push(account.version);
    buffer.push(account.bump);
    buffer.extend_from_slice(&account.market);
    buffer.extend_from_slice(&account.owner);
    buffer.push(pack_position_side(account.side));
    push_u64(&mut buffer, account.id);
    push_distribution(&mut buffer, account.old_distribution);
    push_distribution(&mut buffer, account.new_distribution);
    push_fixed(&mut buffer, account.collateral_posted);
    push_fixed(&mut buffer, account.lp_shares);
    buffer.push(if account.settled { 1 } else { 0 });
    push_fixed(&mut buffer, account.payout_claimed);
    push_u64(&mut buffer, account.created_slot);
    push_option_u64(&mut buffer, account.settled_slot);
    buffer
}

pub fn unpack_position_account(bytes: &[u8]) -> Result<SolanaNormalPositionAccountV1, String> {
    let mut cursor = Cursor::new(bytes);
    let version = cursor.read_u8()?;
    let bump = cursor.read_u8()?;
    let market = cursor.read_array_32()?;
    let owner = cursor.read_array_32()?;
    let side = unpack_position_side(cursor.read_u8()?)?;
    let id = cursor.read_u64()?;
    let old_distribution = cursor.read_distribution()?;
    let new_distribution = cursor.read_distribution()?;
    let collateral_posted = cursor.read_fixed()?;
    let lp_shares = cursor.read_fixed()?;
    let settled = cursor.read_u8()? != 0;
    let payout_claimed = cursor.read_fixed()?;
    let created_slot = cursor.read_u64()?;
    let settled_slot = cursor.read_option_u64()?;
    cursor.ensure_consumed()?;
    Ok(SolanaNormalPositionAccountV1 {
        version,
        bump,
        market,
        owner,
        side,
        id,
        old_distribution,
        new_distribution,
        collateral_posted,
        lp_shares,
        settled,
        payout_claimed,
        created_slot,
        settled_slot,
    })
}

fn planned_token_operations(
    state: &NormalV1ProgramState,
    signer: [u8; 32],
    instruction: &SolanaInstructionV1,
) -> Result<Vec<ProgramTokenOperationV1>, String> {
    match instruction {
        SolanaInstructionV1::InitializeMarket { .. } => {
            Err("initialize_market is not processed via execute_serialized".to_string())
        }
        SolanaInstructionV1::Trade(args) => Ok(vec![ProgramTokenOperationV1::TransferToVault {
            owner: signer,
            amount: args.quote.collateral_required,
        }]),
        SolanaInstructionV1::ManageLiquidity {
            action: distribution_markets::LiquidityAction::Add,
            owner,
            amount_or_shares,
        } => {
            let minted = state.core_market.total_lp_shares * (*amount_or_shares / state.core_market.b);
            Ok(vec![
                ProgramTokenOperationV1::TransferToVault {
                    owner: *owner,
                    amount: *amount_or_shares,
                },
                ProgramTokenOperationV1::MintLpShares {
                    owner: *owner,
                    shares: minted,
                },
            ])
        }
        SolanaInstructionV1::ManageLiquidity {
            action: distribution_markets::LiquidityAction::Remove,
            owner,
            amount_or_shares,
        } => {
            let backing_removed = state.core_market.b * (*amount_or_shares / state.core_market.total_lp_shares);
            Ok(vec![
                ProgramTokenOperationV1::BurnLpShares {
                    owner: *owner,
                    shares: *amount_or_shares,
                },
                ProgramTokenOperationV1::TransferFromVault {
                    owner: *owner,
                    amount: backing_removed,
                },
            ])
        }
        SolanaInstructionV1::ResolveMarket(_) => Ok(Vec::new()),
        SolanaInstructionV1::SettlePosition(args) => {
            let position = state
                .position_accounts
                .iter()
                .find(|position| position.id == args.position_id)
                .ok_or_else(|| "unknown position id".to_string())?;
            Ok(vec![ProgramTokenOperationV1::TransferFromVault {
                owner: position.owner,
                amount: Fixed::ZERO,
            }])
        }
        SolanaInstructionV1::SettleLp(args) => {
            let shares = state
                .position_accounts
                .iter()
                .find(|position| {
                    position.side == NormalPositionSide::Liquidity && position.owner == args.owner
                })
                .map(|position| position.lp_shares)
                .unwrap_or(Fixed::ZERO);
            Ok(vec![
                ProgramTokenOperationV1::BurnLpShares {
                    owner: args.owner,
                    shares,
                },
                ProgramTokenOperationV1::TransferFromVault {
                    owner: args.owner,
                    amount: Fixed::ZERO,
                },
            ])
        }
    }
}

fn push_quote_envelope(buffer: &mut Vec<u8>, quote: &QuoteEnvelopeV1) {
    buffer.extend_from_slice(&quote.market);
    push_u64(buffer, quote.expected_market_version);
    push_distribution(buffer, quote.new_distribution);
    push_fixed(buffer, quote.collateral_required);
    push_fixed(buffer, quote.max_slippage_collateral);
    push_fixed(buffer, quote.search_lower_bound);
    push_fixed(buffer, quote.search_upper_bound);
    push_u32(buffer, quote.coarse_samples);
    push_u32(buffer, quote.refine_samples);
    push_u64(buffer, quote.quote_slot);
    push_u64(buffer, quote.quote_expiry_slot);
}

fn push_oracle_config(buffer: &mut Vec<u8>, config: &OracleConfigV1) {
    buffer.extend_from_slice(&config.oracle_program);
    buffer.extend_from_slice(&config.oracle_feed);
    buffer.extend_from_slice(&config.authority);
}

fn push_distribution(buffer: &mut Vec<u8>, distribution: FixedNormalDistribution) {
    push_fixed(buffer, distribution.mu);
    push_fixed(buffer, distribution.sigma);
}

fn push_fixed(buffer: &mut Vec<u8>, value: Fixed) {
    buffer.extend_from_slice(&value.raw().to_le_bytes());
}

fn push_u64(buffer: &mut Vec<u8>, value: u64) {
    buffer.extend_from_slice(&value.to_le_bytes());
}

fn push_u32(buffer: &mut Vec<u8>, value: u32) {
    buffer.extend_from_slice(&value.to_le_bytes());
}

fn push_option_fixed(buffer: &mut Vec<u8>, value: Option<Fixed>) {
    match value {
        Some(value) => {
            buffer.push(1);
            push_fixed(buffer, value);
        }
        None => buffer.push(0),
    }
}

fn push_option_u64(buffer: &mut Vec<u8>, value: Option<u64>) {
    match value {
        Some(value) => {
            buffer.push(1);
            push_u64(buffer, value);
        }
        None => buffer.push(0),
    }
}

fn pack_market_status(status: MarketStatus) -> u8 {
    match status {
        MarketStatus::Uninitialized => 0,
        MarketStatus::Active => 1,
        MarketStatus::Resolved => 2,
        MarketStatus::Settled => 3,
    }
}

fn unpack_market_status(value: u8) -> Result<MarketStatus, String> {
    match value {
        0 => Ok(MarketStatus::Uninitialized),
        1 => Ok(MarketStatus::Active),
        2 => Ok(MarketStatus::Resolved),
        3 => Ok(MarketStatus::Settled),
        _ => Err("invalid market status".to_string()),
    }
}

fn pack_position_side(side: NormalPositionSide) -> u8 {
    match side {
        NormalPositionSide::Trade => 0,
        NormalPositionSide::Liquidity => 1,
    }
}

fn unpack_position_side(value: u8) -> Result<NormalPositionSide, String> {
    match value {
        0 => Ok(NormalPositionSide::Trade),
        1 => Ok(NormalPositionSide::Liquidity),
        _ => Err("invalid position side".to_string()),
    }
}

struct Cursor<'a> {
    bytes: &'a [u8],
    offset: usize,
}

impl<'a> Cursor<'a> {
    fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, offset: 0 }
    }

    fn read_u8(&mut self) -> Result<u8, String> {
        if self.offset >= self.bytes.len() {
            return Err("unexpected end of bytes".to_string());
        }
        let value = self.bytes[self.offset];
        self.offset += 1;
        Ok(value)
    }

    fn read_u32(&mut self) -> Result<u32, String> {
        let bytes = self.read_exact::<4>()?;
        Ok(u32::from_le_bytes(bytes))
    }

    fn read_u64(&mut self) -> Result<u64, String> {
        let bytes = self.read_exact::<8>()?;
        Ok(u64::from_le_bytes(bytes))
    }

    fn read_i128(&mut self) -> Result<i128, String> {
        let bytes = self.read_exact::<16>()?;
        Ok(i128::from_le_bytes(bytes))
    }

    fn read_fixed(&mut self) -> Result<Fixed, String> {
        Ok(Fixed::from_raw(self.read_i128()?))
    }

    fn read_distribution(&mut self) -> Result<FixedNormalDistribution, String> {
        FixedNormalDistribution::new(self.read_fixed()?, self.read_fixed()?)
    }

    fn read_oracle_config(&mut self) -> Result<OracleConfigV1, String> {
        Ok(OracleConfigV1 {
            oracle_program: self.read_array_32()?,
            oracle_feed: self.read_array_32()?,
            authority: self.read_array_32()?,
        })
    }

    fn read_quote_envelope(&mut self) -> Result<QuoteEnvelopeV1, String> {
        Ok(QuoteEnvelopeV1 {
            market: self.read_array_32()?,
            expected_market_version: self.read_u64()?,
            new_distribution: self.read_distribution()?,
            collateral_required: self.read_fixed()?,
            max_slippage_collateral: self.read_fixed()?,
            search_lower_bound: self.read_fixed()?,
            search_upper_bound: self.read_fixed()?,
            coarse_samples: self.read_u32()?,
            refine_samples: self.read_u32()?,
            quote_slot: self.read_u64()?,
            quote_expiry_slot: self.read_u64()?,
        })
    }

    fn read_option_fixed(&mut self) -> Result<Option<Fixed>, String> {
        match self.read_u8()? {
            0 => Ok(None),
            1 => Ok(Some(self.read_fixed()?)),
            _ => Err("invalid option tag for fixed".to_string()),
        }
    }

    fn read_option_u64(&mut self) -> Result<Option<u64>, String> {
        match self.read_u8()? {
            0 => Ok(None),
            1 => Ok(Some(self.read_u64()?)),
            _ => Err("invalid option tag for u64".to_string()),
        }
    }

    fn read_array_32(&mut self) -> Result<[u8; 32], String> {
        self.read_exact::<32>()
    }

    fn read_exact<const N: usize>(&mut self) -> Result<[u8; N], String> {
        if self.offset + N > self.bytes.len() {
            return Err("unexpected end of bytes".to_string());
        }
        let mut value = [0_u8; N];
        value.copy_from_slice(&self.bytes[self.offset..self.offset + N]);
        self.offset += N;
        Ok(value)
    }

    fn ensure_consumed(&self) -> Result<(), String> {
        if self.offset == self.bytes.len() {
            Ok(())
        } else {
            Err("trailing bytes after decode".to_string())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{
        NormalV1Program, ProgramInitializeArgsV1, pack_instruction, pack_market_account,
        pack_position_account, unpack_instruction, unpack_market_account, unpack_position_account,
    };
    use distribution_markets::{
        Fixed, FixedNormalDistribution, InitializeAccountsV1, OracleConfigV1, QuoteEnvelopeV1,
        TradeArgsV1,
    };
    use distribution_markets::{
        ResolveMarketArgsV1, SettleLpArgsV1, SettlePositionArgsV1, SolanaInstructionV1,
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
    fn instruction_round_trip_survives_trade_payload() {
        let quote = QuoteEnvelopeV1 {
            market: [4_u8; 32],
            expected_market_version: 3,
            new_distribution: distribution(100.0, 10.0),
            collateral_required: fixed(1.2),
            max_slippage_collateral: fixed(1.3),
            search_lower_bound: fixed(60.0),
            search_upper_bound: fixed(140.0),
            coarse_samples: 64,
            refine_samples: 25,
            quote_slot: 10,
            quote_expiry_slot: 20,
        };
        let instruction = SolanaInstructionV1::Trade(TradeArgsV1 { quote });
        let packed = pack_instruction(&instruction);
        let unpacked = unpack_instruction(&packed).unwrap();
        assert_eq!(instruction, unpacked);
    }

    #[test]
    fn account_round_trip_survives_market_and_position_payloads() {
        let (program, _) = NormalV1Program::initialize(
            ProgramInitializeArgsV1 {
                accounts: init_accounts(),
                initial_b: fixed(50.0),
                initial_k: fixed(21.05026039569057),
                initial_distribution: distribution(95.0, 10.0),
                oracle_config: oracle(),
            },
            10,
        )
        .unwrap();

        let market_bytes = pack_market_account(&program.state.market_account);
        let unpacked_market = unpack_market_account(&market_bytes).unwrap();
        assert_eq!(program.state.market_account, unpacked_market);

        let lp_position = &program.state.position_accounts[0];
        let lp_bytes = pack_position_account(lp_position);
        let unpacked_position = unpack_position_account(&lp_bytes).unwrap();
        assert_eq!(*lp_position, unpacked_position);
    }

    #[test]
    fn instruction_variants_pack_and_unpack() {
        let instructions = vec![
            SolanaInstructionV1::InitializeMarket {
                initial_b: fixed(50.0),
                initial_k: fixed(21.05026039569057),
                initial_distribution: distribution(95.0, 10.0),
                oracle_config: oracle(),
            },
            SolanaInstructionV1::ResolveMarket(ResolveMarketArgsV1 {
                outcome: fixed(104.0),
                oracle_observation_slot: 99,
            }),
            SolanaInstructionV1::SettlePosition(SettlePositionArgsV1 { position_id: 2 }),
            SolanaInstructionV1::SettleLp(SettleLpArgsV1 { owner: [8_u8; 32] }),
        ];

        for instruction in instructions {
            let packed = pack_instruction(&instruction);
            let unpacked = unpack_instruction(&packed).unwrap();
            assert_eq!(instruction, unpacked);
        }
    }
}
