pub mod distributions;
pub mod fixed_point;
pub mod market;
pub mod market_core;
pub mod math_core;
pub mod normal_market;
pub mod normal_math;
pub mod numerical;
pub mod product_v1;
pub mod regime_index;
pub mod scoring;
pub mod simulation;
pub mod solana_program_v1;
pub mod solana_v1;

pub use distributions::{
    CauchyDistribution, Distribution, NormalDistribution, ScaledDistribution, StudentTDistribution,
    SupportedDistribution, UniformDistribution,
};
pub use fixed_point::Fixed;
pub use market::{DistributionMarket, Resolution, TradeRecord};
pub use normal_market::{
    FixedNormalLiquiditySnapshot, FixedNormalMarket, FixedNormalMarketConfig,
    FixedNormalQuoteTrace, FixedNormalResolution, FixedNormalRiskGridPoint,
    FixedNormalSettlementWaterfallPreview, FixedNormalTradeQuote, FixedNormalTradeRecord,
};
pub use normal_math::{
    FixedCollateralQuote, FixedNormalDistribution, FixedSearchBounds, fixed_calculate_f,
    fixed_calculate_lambda, fixed_calculate_maximum_k, fixed_calculate_minimum_sigma,
    fixed_collateral_search_bounds, fixed_required_collateral, fixed_required_collateral_quote,
    fixed_required_collateral_quote_with_fee,
};
pub use numerical::{MinimumResult, SearchRange, find_global_minimum, verify_minimum_onchain};
pub use product_v1::{
    MilestoneStatus, ProductMilestoneV1, ProductTrack, RepoTaskMapV1, normal_v1_product_milestones,
    normal_v1_repo_task_map,
};
pub use regime_index::{
    RegimeConstituent, RegimeConstituentSide, RegimeConstituentSnapshot, RegimeConstituentStatus,
    RegimeIndex, RegimeIndexSnapshot, RegimeTokenSide, RegimeTradeQuote, quote_regime_trade,
};
pub use simulation::{
    SimulationReport, SimulationScenario, SimulationStep, builtin_scenarios, find_scenario,
    render_report, run_scenario,
};
pub use solana_program_v1::{
    InitializeAccountsV1, NormalV1ProgramState, ProgramInstructionEffectV1,
    initialize_program_state, process_instruction,
};
pub use solana_v1::{
    LiquidityAction, MarketStatus, NormalPositionSide, OracleConfigV1, QuoteEnvelopeV1,
    ResolveMarketArgsV1, SettleLpArgsV1, SettlePositionArgsV1, SolanaInstructionV1,
    SolanaMarketAccountV1, SolanaNormalPositionAccountV1, SolanaOperationMappingV1,
    SolanaPositionSettlementV1, TradeArgsV1, current_normal_market_to_account,
    normal_v1_operation_mapping,
};

#[cfg(test)]
mod tests;
