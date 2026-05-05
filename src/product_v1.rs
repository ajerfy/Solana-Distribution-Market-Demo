#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ProductTrack {
    Core,
    Program,
    Sdk,
    Frontend,
    Launch,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MilestoneStatus {
    Ready,
    InProgress,
    Planned,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ProductMilestoneV1 {
    pub id: &'static str,
    pub track: ProductTrack,
    pub status: MilestoneStatus,
    pub title: &'static str,
    pub goal: &'static str,
    pub exit_criteria: &'static [&'static str],
    pub primary_artifacts: &'static [&'static str],
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RepoTaskMapV1 {
    pub track: ProductTrack,
    pub next_repo_targets: &'static [&'static str],
    pub why_now: &'static str,
}

pub fn normal_v1_product_milestones() -> Vec<ProductMilestoneV1> {
    vec![
        ProductMilestoneV1 {
            id: "core-v1-freeze",
            track: ProductTrack::Core,
            status: MilestoneStatus::InProgress,
            title: "Freeze the Normal-only core",
            goal: "Promote the fixed-point Normal engine into the single canonical v1 economic surface.",
            exit_criteria: &[
                "Trade, liquidity, collateral, and resolution semantics are locked.",
                "Quote verification is conservative and deterministic.",
                "Stress tests cover replay, rounding dust, sigma floor, and repeated state transitions.",
            ],
            primary_artifacts: &[
                "/Users/aaditjerfy/distribution-markets/src/normal_market.rs",
                "/Users/aaditjerfy/distribution-markets/src/normal_math.rs",
                "/Users/aaditjerfy/distribution-markets/src/tests.rs",
            ],
        },
        ProductMilestoneV1 {
            id: "program-scaffold",
            track: ProductTrack::Program,
            status: MilestoneStatus::InProgress,
            title: "Scaffold the Solana v1 program",
            goal: "Turn the Normal-only account and instruction spec into a compilable Solana program crate.",
            exit_criteria: &[
                "Instruction handlers exist for initialize, trade, liquidity, resolve, and settlement.",
                "Account serialization matches the v1 spec.",
                "The program verifies bounded trade quotes against the canonical core.",
            ],
            primary_artifacts: &[
                "/Users/aaditjerfy/distribution-markets/specs/solana-normal-v1.md",
                "/Users/aaditjerfy/distribution-markets/src/solana_v1.rs",
                "/Users/aaditjerfy/distribution-markets/programs/normal-v1-program",
            ],
        },
        ProductMilestoneV1 {
            id: "sdk-quote-client",
            track: ProductTrack::Sdk,
            status: MilestoneStatus::Planned,
            title: "Build the client quote and transaction SDK",
            goal: "Give clients one supported path for reading markets, creating quotes, and assembling signed transactions.",
            exit_criteria: &[
                "SDK can read market state and generate bounded trade quotes.",
                "SDK can build add/remove liquidity and settlement transactions.",
                "SDK reproduces onchain verification results locally.",
            ],
            primary_artifacts: &[
                "/Users/aaditjerfy/distribution-markets/src/solana_v1.rs",
                "/Users/aaditjerfy/distribution-markets/src/normal_market.rs",
            ],
        },
        ProductMilestoneV1 {
            id: "frontend-alpha",
            track: ProductTrack::Frontend,
            status: MilestoneStatus::Planned,
            title: "Ship a minimal operator and trader UI",
            goal: "Expose Normal market creation, trading, LP flows, and settlement in a controlled interface.",
            exit_criteria: &[
                "UI renders the implied Normal distribution and trade quote summary.",
                "Users can trade, add/remove liquidity, and settle positions with a wallet.",
                "Resolution and LP settlement are visible and auditable.",
            ],
            primary_artifacts: &[
                "/Users/aaditjerfy/distribution-markets/specs/normal-v1-product-roadmap.md",
                "/Users/aaditjerfy/distribution-markets/specs/devnet-launch-checklist.md",
            ],
        },
        ProductMilestoneV1 {
            id: "devnet-beta",
            track: ProductTrack::Launch,
            status: MilestoneStatus::Planned,
            title: "Run a staged devnet beta",
            goal: "Operate a small number of capped Normal markets with monitored real transaction flow.",
            exit_criteria: &[
                "At least one end-to-end market runs from initialization through settlement on devnet.",
                "Operational runbooks exist for quoting, oracle resolution, and incident response.",
                "Observed behavior matches model expectations within defined tolerances.",
            ],
            primary_artifacts: &[
                "/Users/aaditjerfy/distribution-markets/specs/devnet-launch-checklist.md",
            ],
        },
    ]
}

pub fn normal_v1_repo_task_map() -> Vec<RepoTaskMapV1> {
    vec![
        RepoTaskMapV1 {
            track: ProductTrack::Core,
            next_repo_targets: &[
                "/Users/aaditjerfy/distribution-markets/src/normal_market.rs",
                "/Users/aaditjerfy/distribution-markets/src/normal_math.rs",
                "/Users/aaditjerfy/distribution-markets/src/tests.rs",
            ],
            why_now: "This is the only market path close enough to deployability to justify turning into the v1 economic source of truth.",
        },
        RepoTaskMapV1 {
            track: ProductTrack::Program,
            next_repo_targets: &[
                "/Users/aaditjerfy/distribution-markets/programs/normal-v1-program",
                "/Users/aaditjerfy/distribution-markets/src/solana_v1.rs",
                "/Users/aaditjerfy/distribution-markets/specs/solana-normal-v1.md",
            ],
            why_now: "The Solana layer should be generated from locked core semantics, not invented in parallel.",
        },
        RepoTaskMapV1 {
            track: ProductTrack::Sdk,
            next_repo_targets: &[
                "/Users/aaditjerfy/distribution-markets/src/solana_v1.rs",
                "/Users/aaditjerfy/distribution-markets/src/simulation.rs",
            ],
            why_now: "The SDK is the bridge between offchain quote generation and onchain bounded verification.",
        },
        RepoTaskMapV1 {
            track: ProductTrack::Frontend,
            next_repo_targets: &[
                "/Users/aaditjerfy/distribution-markets/specs/normal-v1-product-roadmap.md",
            ],
            why_now: "UI work should follow a stable transaction and quote surface so it does not churn with protocol changes.",
        },
        RepoTaskMapV1 {
            track: ProductTrack::Launch,
            next_repo_targets: &[
                "/Users/aaditjerfy/distribution-markets/specs/devnet-launch-checklist.md",
            ],
            why_now: "Launch discipline is what turns a prototype into a usable product rather than a demo.",
        },
    ]
}

#[cfg(test)]
mod tests {
    use super::{MilestoneStatus, ProductTrack, normal_v1_product_milestones, normal_v1_repo_task_map};

    #[test]
    fn milestones_cover_all_product_tracks() {
        let milestones = normal_v1_product_milestones();
        assert!(milestones.iter().any(|item| item.track == ProductTrack::Core));
        assert!(milestones.iter().any(|item| item.track == ProductTrack::Program));
        assert!(milestones.iter().any(|item| item.track == ProductTrack::Sdk));
        assert!(milestones.iter().any(|item| item.track == ProductTrack::Frontend));
        assert!(milestones.iter().any(|item| item.track == ProductTrack::Launch));
        assert!(milestones
            .iter()
            .any(|item| item.status == MilestoneStatus::InProgress));
    }

    #[test]
    fn repo_task_map_targets_every_execution_track() {
        let task_map = normal_v1_repo_task_map();
        assert_eq!(task_map.len(), 5);
        assert!(task_map.iter().all(|item| !item.next_repo_targets.is_empty()));
    }
}
