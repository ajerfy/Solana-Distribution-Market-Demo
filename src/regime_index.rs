use crate::Fixed;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RegimeConstituentSide {
    Long,
    Short,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RegimeConstituentStatus {
    Active,
    Resolved,
    PendingSpawn,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RegimeTokenSide {
    Long,
    Short,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RegimeConstituent {
    pub id: String,
    pub label: String,
    pub market_id: [u8; 32],
    pub side: RegimeConstituentSide,
    pub weight_bps: u32,
    pub probability: Fixed,
    pub previous_probability: Fixed,
    pub status: RegimeConstituentStatus,
    pub expiry_slot: u64,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RegimeIndex {
    pub id: String,
    pub symbol: String,
    pub title: String,
    pub thesis: String,
    pub status: String,
    pub rebalance_slot: u64,
    pub next_rebalance_slot: u64,
    pub constituents: Vec<RegimeConstituent>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RegimeConstituentSnapshot {
    pub constituent: RegimeConstituent,
    pub aligned_probability: Fixed,
    pub previous_aligned_probability: Fixed,
    pub level_contribution: Fixed,
    pub previous_level_contribution: Fixed,
    pub signed_pressure: Fixed,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RegimeIndexSnapshot {
    pub index: RegimeIndex,
    pub level: Fixed,
    pub previous_level: Fixed,
    pub change: Fixed,
    pub constituents: Vec<RegimeConstituentSnapshot>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RegimeTradeQuote {
    pub side: RegimeTokenSide,
    pub size: Fixed,
    pub entry_level: Fixed,
    pub token_price: Fixed,
    pub collateral_required: Fixed,
    pub fee_paid: Fixed,
    pub total_debit: Fixed,
}

impl RegimeIndex {
    pub fn snapshot(&self) -> Result<RegimeIndexSnapshot, String> {
        validate_regime_index(self)?;

        let mut level = Fixed::ZERO;
        let mut previous_level = Fixed::ZERO;
        let mut constituents = Vec::with_capacity(self.constituents.len());

        for constituent in &self.constituents {
            let weight = bps_to_fixed(constituent.weight_bps);
            let current_aligned_probability =
                aligned_probability(constituent.side, constituent.probability);
            let previous_aligned_probability =
                aligned_probability(constituent.side, constituent.previous_probability);
            let level_contribution = fixed_percent(current_aligned_probability * weight);
            let previous_level_contribution = fixed_percent(previous_aligned_probability * weight);
            let signed_pressure = signed_pressure(
                constituent.side,
                constituent.probability,
                constituent.weight_bps,
            );

            level = level + level_contribution;
            previous_level = previous_level + previous_level_contribution;
            constituents.push(RegimeConstituentSnapshot {
                constituent: constituent.clone(),
                aligned_probability: current_aligned_probability,
                previous_aligned_probability,
                level_contribution,
                previous_level_contribution,
                signed_pressure,
            });
        }

        Ok(RegimeIndexSnapshot {
            index: self.clone(),
            level,
            previous_level,
            change: level - previous_level,
            constituents,
        })
    }
}

pub fn quote_regime_trade(
    snapshot: &RegimeIndexSnapshot,
    side: RegimeTokenSide,
    size: Fixed,
    taker_fee_bps: u32,
    min_taker_fee: Fixed,
) -> Result<RegimeTradeQuote, String> {
    if size.raw() <= 0 {
        return Err("regime token quote size must be positive".to_string());
    }
    if taker_fee_bps > 10_000 {
        return Err("regime token taker fee bps cannot exceed 10000".to_string());
    }
    if min_taker_fee.raw() < 0 {
        return Err("regime token minimum fee cannot be negative".to_string());
    }

    let level_fraction = snapshot.level / Fixed::from_f64(100.0)?;
    let token_price = match side {
        RegimeTokenSide::Long => level_fraction,
        RegimeTokenSide::Short => Fixed::ONE - level_fraction,
    };
    let collateral_required = size * token_price;
    let fee_rate = bps_to_fixed(taker_fee_bps);
    let fee_paid = (collateral_required * fee_rate).max(min_taker_fee);

    Ok(RegimeTradeQuote {
        side,
        size,
        entry_level: snapshot.level,
        token_price,
        collateral_required,
        fee_paid,
        total_debit: collateral_required + fee_paid,
    })
}

fn validate_regime_index(index: &RegimeIndex) -> Result<(), String> {
    if index.constituents.is_empty() {
        return Err("regime index must have at least one constituent".to_string());
    }

    let mut weight_sum = 0_u32;
    for constituent in &index.constituents {
        if constituent.weight_bps == 0 {
            return Err("regime index constituent weight cannot be zero".to_string());
        }
        validate_probability(constituent.probability)?;
        validate_probability(constituent.previous_probability)?;
        weight_sum = weight_sum
            .checked_add(constituent.weight_bps)
            .ok_or_else(|| "regime index constituent weights overflow".to_string())?;
    }

    if weight_sum != 10_000 {
        return Err("regime index constituent weights must sum to 10000 bps".to_string());
    }

    Ok(())
}

fn validate_probability(probability: Fixed) -> Result<(), String> {
    if probability.raw() < 0 || probability.raw() > Fixed::ONE.raw() {
        return Err("regime index constituent probability must be between 0 and 1".to_string());
    }
    Ok(())
}

fn aligned_probability(side: RegimeConstituentSide, probability: Fixed) -> Fixed {
    match side {
        RegimeConstituentSide::Long => probability,
        RegimeConstituentSide::Short => Fixed::ONE - probability,
    }
}

fn signed_pressure(side: RegimeConstituentSide, probability: Fixed, weight_bps: u32) -> Fixed {
    let pressure = fixed_percent(probability * bps_to_fixed(weight_bps));
    match side {
        RegimeConstituentSide::Long => pressure,
        RegimeConstituentSide::Short => -pressure,
    }
}

fn fixed_percent(value: Fixed) -> Fixed {
    value * Fixed::from_raw(100 * Fixed::SCALE)
}

fn bps_to_fixed(weight_bps: u32) -> Fixed {
    Fixed::from_raw((weight_bps as i128 * Fixed::SCALE) / 10_000)
}

#[cfg(test)]
mod tests {
    use super::{
        RegimeConstituent, RegimeConstituentSide, RegimeConstituentStatus, RegimeIndex,
        RegimeTokenSide, quote_regime_trade,
    };
    use crate::Fixed;

    fn fixed(value: f64) -> Fixed {
        Fixed::from_f64(value).unwrap()
    }

    fn constituent(
        id: &str,
        side: RegimeConstituentSide,
        weight_bps: u32,
        probability: f64,
        previous_probability: f64,
    ) -> RegimeConstituent {
        RegimeConstituent {
            id: id.to_string(),
            label: id.to_string(),
            market_id: [weight_bps as u8; 32],
            side,
            weight_bps,
            probability: fixed(probability),
            previous_probability: fixed(previous_probability),
            status: RegimeConstituentStatus::Active,
            expiry_slot: 100,
        }
    }

    fn hawkish_index(recession_probability: f64) -> RegimeIndex {
        RegimeIndex {
            id: "hawkish-fed".to_string(),
            symbol: "HAWKFED".to_string(),
            title: "Hawkish Fed".to_string(),
            thesis: "Rate pressure basket".to_string(),
            status: "Active".to_string(),
            rebalance_slot: 50,
            next_rebalance_slot: 75,
            constituents: vec![
                constituent("rate-hike", RegimeConstituentSide::Long, 4_000, 0.70, 0.64),
                constituent("hot-cpi", RegimeConstituentSide::Long, 3_500, 0.60, 0.55),
                constituent(
                    "recession",
                    RegimeConstituentSide::Short,
                    2_500,
                    recession_probability,
                    0.35,
                ),
            ],
        }
    }

    #[test]
    fn regime_index_scores_long_and_short_constituents() {
        let snapshot = hawkish_index(0.40).snapshot().unwrap();

        assert_eq!(snapshot.level.to_string(), "64.000000000");
        assert_eq!(snapshot.constituents.len(), 3);
        assert_eq!(
            snapshot.constituents[2].level_contribution.to_string(),
            "15.000000000"
        );
        assert_eq!(
            snapshot.constituents[2].signed_pressure.to_string(),
            "-10.000000000"
        );
    }

    #[test]
    fn higher_short_constituent_probability_lowers_regime_level() {
        let lower_recession = hawkish_index(0.30).snapshot().unwrap();
        let higher_recession = hawkish_index(0.70).snapshot().unwrap();

        assert!(higher_recession.level < lower_recession.level);
    }

    #[test]
    fn regime_trade_quote_prices_long_and_short_tokens() {
        let snapshot = hawkish_index(0.40).snapshot().unwrap();
        let min_fee = fixed(0.001);
        let long_quote =
            quote_regime_trade(&snapshot, RegimeTokenSide::Long, Fixed::ONE, 100, min_fee).unwrap();
        let short_quote =
            quote_regime_trade(&snapshot, RegimeTokenSide::Short, Fixed::ONE, 100, min_fee)
                .unwrap();

        assert_eq!(long_quote.token_price.to_string(), "0.640000000");
        assert_eq!(short_quote.token_price.to_string(), "0.360000000");
        assert_eq!(long_quote.total_debit.to_string(), "0.646400000");
        assert_eq!(short_quote.total_debit.to_string(), "0.363600000");
    }

    #[test]
    fn invalid_regime_weights_are_rejected() {
        let mut index = hawkish_index(0.40);
        index.constituents[0].weight_bps = 4_100;

        assert!(index.snapshot().unwrap_err().contains("weights"));
    }

    #[test]
    fn invalid_regime_probability_is_rejected() {
        let mut index = hawkish_index(0.40);
        index.constituents[0].probability = fixed(1.20);

        assert!(index.snapshot().unwrap_err().contains("probability"));
    }
}
