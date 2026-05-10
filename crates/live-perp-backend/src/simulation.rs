use distribution_markets::{Fixed, FixedNormalDistribution, fixed_calculate_lambda};
use normal_v1_sdk::{TradeQuoteRequestV1, build_trade_quote, seeded_demo_market};
use serde_json::{Value, json};

use crate::{PolyMarketSnapshot, DEMO_MARKET_ID, DEMO_QUOTE_EXPIRY_DELTA, format9};

// ── Distribution family (fat-tail simulation) ─────────────────────────────────
//
// On-chain settlement is always Normal; these families control the noise term
// injected into agent proposals so the simulation can exhibit Black Swan jumps.
#[derive(Clone, Debug, Default, PartialEq)]
pub enum DistributionFamily {
    #[default]
    Normal,
    CauchyOverlay,
    StudentT, // ν = 4
}

impl DistributionFamily {
    pub fn label(&self) -> &'static str {
        match self {
            Self::Normal => "Normal",
            Self::CauchyOverlay => "CauchyOverlay",
            Self::StudentT => "StudentT(ν=4)",
        }
    }

    pub fn next(&self) -> Self {
        match self {
            Self::Normal => Self::CauchyOverlay,
            Self::CauchyOverlay => Self::StudentT,
            Self::StudentT => Self::Normal,
        }
    }

    /// Transform a [0,1] pseudo-random sample into a scaled perturbation.
    /// Cauchy and StudentT produce heavier tails, modelling Black Swan price jumps.
    pub fn perturbation(&self, u: f64, scale: f64) -> f64 {
        match self {
            Self::Normal => (u - 0.5) * scale,
            Self::CauchyOverlay => {
                // Cauchy quantile: tan(π(u − 0.5)). Clamped so infinities can't blow the state.
                let raw = std::f64::consts::PI * (u - 0.5);
                raw.tan().clamp(-8.0, 8.0) * (scale * 0.55)
            }
            Self::StudentT => {
                // Approximate t(ν=4) via the ratio z / √(χ²/ν) where χ² ≈ 1 + |z|·ν⁻¹.
                let u1 = u;
                let u2 = (u * 1.6180339 + 0.3141592).fract().max(1e-9);
                let z = ((-2.0 * u1.max(1e-9).ln()) * (2.0 * std::f64::consts::PI * u2).cos())
                    .clamp(-5.0, 5.0);
                let chi_approx = (1.0 + z * z * 0.25).sqrt(); // ν = 4
                (z / chi_approx).clamp(-6.0, 6.0) * (scale * 0.72)
            }
        }
    }
}

// ── Agent types ────────────────────────────────────────────────────────────────
#[derive(Clone, Debug, PartialEq)]
pub enum AgentType {
    Informed,
    Momentum,
    Noisy,
    Contrarian,
    RiskLimited,
    /// Specifically targets the spread between the live Polymarket μ and the
    /// internal market μ, closing the gap more aggressively than an Informed trader.
    Arbitrageur,
    /// Adjusts σ based on external book depth / spread rather than pushing on μ.
    VolatilityHedger,
}

impl AgentType {
    #[allow(dead_code)]
    pub fn label(&self) -> &'static str {
        match self {
            Self::Informed => "Informed",
            Self::Momentum => "Momentum",
            Self::Noisy => "Noisy",
            Self::Contrarian => "Contrarian",
            Self::RiskLimited => "Risk-limited",
            Self::Arbitrageur => "Arbitrageur",
            Self::VolatilityHedger => "VolatilityHedger",
        }
    }
}

// ── Simulation data types ──────────────────────────────────────────────────────
#[derive(Clone, Debug)]
pub struct SimulationSourceState {
    pub live_event: PolyMarketSnapshot,
    pub slot: u64,
}

#[derive(Clone, Debug)]
pub struct MarketSimulationState {
    pub initialized: bool,
    pub running: bool,
    pub regime: String,
    pub scenario: String,
    pub speed: u32,
    pub tick: u64,
    pub revision: u64,
    pub current_mu: f64,
    pub current_sigma: f64,
    pub current_skew: f64,
    pub previous_mu: f64,
    pub previous_sigma: f64,
    pub previous_skew: f64,
    pub target_shift: f64,
    pub shock_remaining: u32,
    pub total_volume: f64,
    pub fees_earned: f64,
    pub trade_count: u64,
    pub accepted_count: u64,
    pub history: Vec<SimulationHistoryPoint>,
    pub tape: Vec<SimulationTradeEvent>,
    pub last_error: Option<String>,
    /// Ordered rotation of agent archetypes. Ticked through round-robin.
    pub agents: Vec<AgentType>,
    /// Noise distribution injected into agent μ proposals. Quote execution is always Normal.
    pub distribution_family: DistributionFamily,
}

#[derive(Clone, Debug)]
pub struct SimulationHistoryPoint {
    pub slot: u64,
    pub tick: u64,
    pub mu: f64,
    pub sigma: f64,
    pub volume: f64,
    pub fees: f64,
    pub reason: String,
}

#[derive(Clone, Debug)]
pub struct SimulationTradeEvent {
    pub id: String,
    pub slot: u64,
    pub tick: u64,
    pub agent_type: String,
    pub handle: String,
    pub action: String,
    pub target_mu: f64,
    pub target_sigma: f64,
    pub collateral: f64,
    pub fee: f64,
    pub total_debit: f64,
    pub accepted: bool,
    pub reason: String,
}

// ── impl MarketSimulationState ─────────────────────────────────────────────────
impl MarketSimulationState {
    pub fn new() -> Self {
        Self {
            initialized: false,
            running: false,
            regime: "drift".to_string(),
            scenario: "Consensus drift".to_string(),
            speed: 1,
            tick: 0,
            revision: 0,
            current_mu: 50.0,
            current_sigma: 10.0,
            current_skew: 0.0,
            previous_mu: 50.0,
            previous_sigma: 10.0,
            previous_skew: 0.0,
            target_shift: 0.0,
            shock_remaining: 0,
            total_volume: 0.0,
            fees_earned: 0.0,
            trade_count: 0,
            accepted_count: 0,
            history: Vec::new(),
            tape: Vec::new(),
            last_error: None,
            agents: vec![
                AgentType::Informed,
                AgentType::Momentum,
                AgentType::Noisy,
                AgentType::Contrarian,
                AgentType::RiskLimited,
                AgentType::Arbitrageur,
                AgentType::VolatilityHedger,
            ],
            distribution_family: DistributionFamily::Normal,
        }
    }

    pub fn status_json(&self) -> Value {
        json!({
            "running": self.running,
            "regime": self.regime,
            "scenario": self.scenario,
            "speed": self.speed,
            "tick": self.tick,
            "revision": self.revision,
            "trade_count": self.trade_count,
            "accepted_count": self.accepted_count,
            "current_mu_display": format9(self.current_mu),
            "current_sigma_display": format9(self.current_sigma),
            "current_skew_display": format9(self.current_skew),
            "previous_skew_display": format9(self.previous_skew),
            "distribution_family": self.distribution_family.label(),
            "last_error": &self.last_error,
        })
    }

    pub fn set_regime(&mut self, regime: &str) {
        self.running = true;
        self.regime = regime.to_string();
        self.shock_remaining = 0;
        self.target_shift = 0.0;
        self.scenario = match regime {
            "bullish" => "Bullish conviction".to_string(),
            "bearish" => "Bearish conviction".to_string(),
            "volatile" => "Two-sided volatility".to_string(),
            _ => "Consensus drift".to_string(),
        };
        let target_skew = self.regime_target_skew();
        self.current_skew = target_skew;
        self.previous_skew = target_skew;
        self.bump_revision();
    }

    pub fn bump_revision(&mut self) {
        self.revision = self.revision.saturating_add(1);
    }

    pub fn toggle_regime(&mut self, regime: &str) {
        if self.regime == regime {
            self.set_regime("drift");
        } else {
            self.set_regime(regime);
        }
    }

    pub fn cycle_distribution_family(&mut self) {
        self.distribution_family = self.distribution_family.next();
        self.bump_revision();
    }

    pub fn advance_background(
        &mut self,
        live_event: &PolyMarketSnapshot,
        slot: u64,
    ) -> Result<(), String> {
        self.ensure_initialized(live_event, slot);
        if self.running {
            let steps = self.speed.clamp(1, 12);
            for _ in 0..steps {
                self.apply_trade_tick(live_event, slot + self.tick)?;
            }
        }
        Ok(())
    }

    pub fn ensure_initialized(&mut self, _live_event: &PolyMarketSnapshot, slot: u64) {
        if self.initialized {
            return;
        }
        self.initialized = true;
        self.current_mu = 50.0;
        self.current_sigma = 10.0;
        self.current_skew = 0.0;
        self.previous_mu = self.current_mu;
        self.previous_sigma = self.current_sigma;
        self.previous_skew = self.current_skew;
        self.history.clear();
        self.tape.clear();
        self.push_history(slot, "Neutral market baseline");
    }

    fn apply_trade_tick(
        &mut self,
        live_event: &PolyMarketSnapshot,
        slot: u64,
    ) -> Result<(), String> {
        self.tick += 1;
        let external_mu = (live_event.probability * 100.0).clamp(1.0, 99.0);
        let external_sigma = live_event.sigma.clamp(4.0, 18.0);
        let external_spread = live_event.spread.unwrap_or(0.02);

        let shock = if self.shock_remaining > 0 {
            self.shock_remaining -= 1;
            if self.shock_remaining == 0 {
                self.regime = "drift".to_string();
                self.scenario = "Consensus drift".to_string();
                self.target_shift = 0.0;
            }
            self.target_shift
        } else {
            0.0
        };

        let target_consensus = self.regime_target_consensus(external_mu + shock);
        let (agent_type, handle, action, reason, target_mu, target_sigma) =
            self.agent_proposal(target_consensus, external_sigma, external_spread);
        let target_skew = self.regime_target_skew();

        match self.quote_transition(target_mu, target_sigma, slot) {
            Ok((collateral, fee, total_debit)) => {
                self.previous_mu = self.current_mu;
                self.previous_sigma = self.current_sigma;
                self.previous_skew = self.current_skew;
                self.current_mu = target_mu;
                self.current_sigma = target_sigma;
                self.current_skew = target_skew;
                self.total_volume += total_debit;
                self.fees_earned += fee;
                self.trade_count += 1;
                self.accepted_count += 1;
                self.last_error = None;
                self.push_tape(SimulationTradeEvent {
                    id: format!("sim-{}", self.tick),
                    slot,
                    tick: self.tick,
                    agent_type,
                    handle,
                    action,
                    target_mu,
                    target_sigma,
                    collateral,
                    fee,
                    total_debit,
                    accepted: true,
                    reason: reason.clone(),
                });
                self.push_history(slot, &reason);
            }
            Err(error) => {
                self.last_error = Some(error.clone());
                self.push_tape(SimulationTradeEvent {
                    id: format!("sim-{}", self.tick),
                    slot,
                    tick: self.tick,
                    agent_type,
                    handle,
                    action,
                    target_mu,
                    target_sigma,
                    collateral: 0.0,
                    fee: 0.0,
                    total_debit: 0.0,
                    accepted: false,
                    reason: error,
                });
            }
        }
        self.bump_revision();
        Ok(())
    }

    fn regime_target_consensus(&self, base_consensus: f64) -> f64 {
        match self.regime.as_str() {
            "bullish" => (base_consensus + 24.0).clamp(70.0, 96.0),
            "bearish" => (base_consensus - 45.0).clamp(4.0, 35.0),
            "volatile" => {
                let wave = if (self.tick / 4) % 2 == 0 { 30.0 } else { -30.0 };
                let pulse = (pseudo_unit(self.tick + 91) - 0.5) * 10.0;
                (base_consensus + wave + pulse).clamp(1.0, 99.0)
            }
            _ => base_consensus.clamp(1.0, 99.0),
        }
    }

    fn regime_target_skew(&self) -> f64 {
        match self.regime.as_str() {
            // Visual pressure layer only: settlement pricing remains Normal-only.
            "bullish" => -7.0,
            "bearish" => 7.0,
            "volatile" => {
                if (self.tick / 3) % 2 == 0 { -6.0 } else { 6.0 }
            }
            "shock" => {
                if self.target_shift >= 0.0 { -5.0 } else { 5.0 }
            }
            _ => 0.0,
        }
    }

    fn agent_proposal(
        &self,
        target_consensus: f64,
        external_sigma: f64,
        external_spread: f64,
    ) -> (String, String, String, String, f64, f64) {
        let u = pseudo_unit(self.tick + 17);
        let noise = self.distribution_family.perturbation(u, 1.0);
        let momentum = self.current_mu - self.previous_mu;
        let n_agents = self.agents.len().max(1);
        let agent = &self.agents[(self.tick as usize) % n_agents];

        // Regime overrides bypass the round-robin agent rotation.
        if self.regime == "bullish" {
            let breath = (pseudo_unit(self.tick + 31) - 0.5) * 4.5;
            return (
                "Bullish".to_string(),
                format!("bull_{}", 100 + (self.tick % 5)),
                "bids higher with confidence".to_string(),
                "Bullish traders are clustered above the live consensus, tightening confidence and leaving a left-tail risk shadow.".to_string(),
                (self.current_mu * 0.25 + target_consensus * 0.75 + breath).clamp(70.0, 96.0),
                (4.4 + pseudo_unit(self.tick + 37) * 2.4).clamp(4.0, 8.0),
            );
        }
        if self.regime == "bearish" {
            let breath = (pseudo_unit(self.tick + 43) - 0.5) * 4.5;
            return (
                "Bearish".to_string(),
                format!("bear_{}", 100 + (self.tick % 5)),
                "offers lower with confidence".to_string(),
                "Bearish traders are clustered below the live consensus, tightening confidence and leaving a right-tail risk shadow.".to_string(),
                (self.current_mu * 0.25 + target_consensus * 0.75 + breath).clamp(4.0, 35.0),
                (4.4 + pseudo_unit(self.tick + 47) * 2.4).clamp(4.0, 8.0),
            );
        }
        if self.regime == "volatile" {
            let direction = if (self.tick / 4) % 2 == 0 { 1.0 } else { -1.0 };
            return (
                "Volatile".to_string(),
                format!("vol_{}", 100 + (self.tick % 5)),
                "hits both sides aggressively".to_string(),
                "Two-sided flow keeps dragging the estimate back and forth while confidence widens.".to_string(),
                (self.current_mu
                    + (target_consensus - self.current_mu) * 1.05
                    + direction * (5.0 + pseudo_unit(self.tick + 53) * 6.0))
                    .clamp(1.0, 99.0),
                (self.current_sigma * 1.20 + 4.0 + pseudo_unit(self.tick + 59) * 4.0)
                    .clamp(10.0, 18.0),
            );
        }

        // Normal round-robin agent proposals with distribution-family noise.
        let (raw_mu, raw_sigma, label, handle, action, reason) = match agent {
            AgentType::Informed => (
                self.current_mu + (target_consensus - self.current_mu) * 0.88 + noise * 3.5,
                self.current_sigma * 0.78 + external_sigma * 0.22,
                "Informed", "model_alpha", "leans toward fresh information",
                "An informed trader pulls the curve toward the latest consensus.",
            ),
            AgentType::Momentum => (
                self.current_mu + momentum * 2.15 + noise * 5.0,
                self.current_sigma * (0.90 + pseudo_unit(self.tick + 3) * 0.22),
                "Momentum", "trend_721", "follows the last move",
                "A momentum trader extends the recent direction.",
            ),
            AgentType::Noisy => (
                self.current_mu + noise * 16.0,
                self.current_sigma * (0.72 + pseudo_unit(self.tick + 5) * 0.70),
                "Noisy", "anon_noise", "adds disagreement",
                "A noisy trader adds short-term disagreement to the market.",
            ),
            AgentType::Contrarian => (
                self.current_mu + (target_consensus - self.current_mu) * 0.42 - momentum * 1.35
                    + noise * 4.0,
                self.current_sigma * 1.18,
                "Contrarian", "fade_shop", "fades the crowd",
                "A contrarian trader fades the last move.",
            ),
            AgentType::RiskLimited => (
                self.current_mu + (target_consensus - self.current_mu) * 0.55 + noise * 2.5,
                self.current_sigma * 0.88 + external_sigma * 0.12,
                "Risk-limited", "small_size", "nudges within budget",
                "A smaller trader still moves the curve visibly.",
            ),
            AgentType::Arbitrageur => {
                // Aggressively closes the spread between Polymarket μ and internal μ.
                // Uses tighter σ because arb positions are hedged.
                let arb_u = pseudo_unit(self.tick + 71);
                let arb_noise = self.distribution_family.perturbation(arb_u, 0.8);
                return (
                    "Arbitrageur".to_string(),
                    format!("arb_{:03}", self.tick % 1000),
                    "arbs Polymarket vs internal spread".to_string(),
                    format!(
                        "Arbitrageur closes {:.1}pt gap between external ({:.1}%) and internal ({:.1}%) consensus.",
                        (target_consensus - self.current_mu).abs(),
                        target_consensus,
                        self.current_mu,
                    ),
                    (self.current_mu + (target_consensus - self.current_mu) * 0.97 + arb_noise)
                        .clamp(1.0, 99.0),
                    (external_sigma * 0.70).clamp(4.0, 10.0),
                );
            }
            AgentType::VolatilityHedger => {
                // Keeps μ near consensus but widens/tightens σ based on book depth.
                // Thin book (high spread) → widen σ. Deep book (low spread) → tighten σ.
                let spread_pressure = (external_spread * 40.0).clamp(0.5, 3.0);
                let hedge_sigma = (self.current_sigma * spread_pressure).clamp(4.0, 18.0);
                let hedge_u = pseudo_unit(self.tick + 83);
                let hedge_noise = self.distribution_family.perturbation(hedge_u, 1.2);
                return (
                    "VolatilityHedger".to_string(),
                    format!("vhg_{:03}", self.tick % 1000),
                    "adjusts sigma from book depth".to_string(),
                    format!(
                        "Vol hedger reads {:.3} spread → sets σ={:.2} ({}book).",
                        external_spread,
                        hedge_sigma,
                        if external_spread > 0.05 { "thin " } else { "deep " },
                    ),
                    (target_consensus + hedge_noise).clamp(1.0, 99.0),
                    hedge_sigma,
                );
            }
        };

        let mut target_mu = raw_mu.clamp(1.0, 99.0);
        let mut target_sigma = raw_sigma.clamp(4.0, 18.0);

        // Enforce minimum visible move so the simulation doesn't stall.
        if (target_mu - self.current_mu).abs() < 0.12
            && (target_sigma - self.current_sigma).abs() < 0.08
        {
            let sign = if target_consensus >= self.current_mu { 1.0 } else { -1.0 };
            target_mu = (target_mu + sign * 2.5).clamp(1.0, 99.0);
            target_sigma = (target_sigma * 1.08).clamp(4.0, 18.0);
        }

        (
            label.to_string(),
            handle.to_string(),
            action.to_string(),
            reason.to_string(),
            target_mu,
            target_sigma,
        )
    }

    fn quote_transition(
        &self,
        target_mu: f64,
        target_sigma: f64,
        slot: u64,
    ) -> Result<(f64, f64, f64), String> {
        let mut program = seeded_demo_market()?;
        program.state.core_market.config.max_collateral_per_trade = Fixed::from_f64(100.0)?;
        program.state.market_account.max_collateral_per_trade =
            program.state.core_market.config.max_collateral_per_trade;
        set_program_distribution(&mut program, self.current_mu, self.current_sigma)?;
        let target_distribution = FixedNormalDistribution::new(
            Fixed::from_f64(target_mu)?,
            Fixed::from_f64(target_sigma)?,
        )?;
        let envelope = build_trade_quote(
            &program,
            TradeQuoteRequestV1 {
                trader: [8_u8; 32],
                market: DEMO_MARKET_ID,
                target_distribution,
                quote_slot: slot,
                quote_expiry_slot: slot + DEMO_QUOTE_EXPIRY_DELTA,
            },
        )?;
        Ok((
            envelope.collateral_required.to_f64(),
            envelope.fee_paid.to_f64(),
            envelope.total_debit.to_f64(),
        ))
    }

    fn push_history(&mut self, slot: u64, reason: &str) {
        self.history.push(SimulationHistoryPoint {
            slot,
            tick: self.tick,
            mu: self.current_mu,
            sigma: self.current_sigma,
            volume: self.total_volume,
            fees: self.fees_earned,
            reason: reason.to_string(),
        });
        if self.history.len() > 64 {
            let drop_count = self.history.len() - 64;
            self.history.drain(0..drop_count);
        }
    }

    fn push_tape(&mut self, event: SimulationTradeEvent) {
        self.tape.insert(0, event);
        if self.tape.len() > 18 {
            self.tape.truncate(18);
        }
    }

    pub fn to_payload(&self) -> Value {
        json!({
            "running": self.running,
            "regime": self.regime,
            "scenario": self.scenario,
            "speed": self.speed,
            "tick": self.tick,
            "revision": self.revision,
            "trade_count": self.trade_count,
            "accepted_count": self.accepted_count,
            "current_mu_display": format9(self.current_mu),
            "current_sigma_display": format9(self.current_sigma),
            "current_skew_display": format9(self.current_skew),
            "previous_mu_display": format9(self.previous_mu),
            "previous_sigma_display": format9(self.previous_sigma),
            "previous_skew_display": format9(self.previous_skew),
            "total_volume_display": format9(self.total_volume),
            "fees_earned_display": format9(self.fees_earned),
            "distribution_family": self.distribution_family.label(),
            "last_error": &self.last_error,
            "market_path": self.history.iter().map(|point| json!({
                "slot": point.slot,
                "tick": point.tick,
                "mu_display": format9(point.mu),
                "sigma_display": format9(point.sigma),
                "volume_display": format9(point.volume),
                "fees_display": format9(point.fees),
                "reason": &point.reason,
            })).collect::<Vec<_>>(),
            "trade_tape": self.tape.iter().map(|trade| json!({
                "id": &trade.id,
                "slot": trade.slot,
                "tick": trade.tick,
                "agent_type": &trade.agent_type,
                "handle": &trade.handle,
                "action": &trade.action,
                "target_mu_display": format9(trade.target_mu),
                "target_sigma_display": format9(trade.target_sigma),
                "collateral_display": format9(trade.collateral),
                "fee_display": format9(trade.fee),
                "total_debit_display": format9(trade.total_debit),
                "accepted": trade.accepted,
                "reason": &trade.reason,
            })).collect::<Vec<_>>(),
        })
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

pub(crate) fn pseudo_unit(seed: u64) -> f64 {
    let raw = ((seed as f64 + 1.0) * 12.9898).sin() * 43_758.5453;
    raw - raw.floor()
}

pub(crate) fn set_program_distribution(
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
