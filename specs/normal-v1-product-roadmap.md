# Normal V1 Product Roadmap

## Product Objective

Ship a usable Normal-only distribution market product on Solana with one deterministic economic engine, one bounded quote-verification path, one LP model, and one end-to-end settlement workflow.

The guiding product decision is to keep research breadth in the repo while narrowing deployable scope to the fixed-point Normal market in [`/Users/aaditjerfy/distribution-markets/src/normal_market.rs`](/Users/aaditjerfy/distribution-markets/src/normal_market.rs).

## Phase 1: Freeze the v1 Economic Core

Goal:
- Treat the fixed-point Normal path as the only canonical market engine for product work.

Must-haves:
- Lock trade, collateral, solvency, LP, and settlement semantics.
- Preserve deterministic bounded quote verification.
- Expand invariants around replay, rounding dust, sigma floor, and repeated state transitions.

Exit criteria:
- Core semantics are documented and no longer changing casually.
- Every client and program-facing flow can be expressed through the same Normal-only quote model.
- The stress suite is strong enough to act as a regression gate for product changes.

Primary files:
- [`/Users/aaditjerfy/distribution-markets/src/normal_market.rs`](/Users/aaditjerfy/distribution-markets/src/normal_market.rs)
- [`/Users/aaditjerfy/distribution-markets/src/normal_math.rs`](/Users/aaditjerfy/distribution-markets/src/normal_math.rs)
- [`/Users/aaditjerfy/distribution-markets/src/tests.rs`](/Users/aaditjerfy/distribution-markets/src/tests.rs)

## Phase 2: Solana Program Scaffold

Goal:
- Turn the Normal-only protocol spec into a compilable Solana program crate.

Must-haves:
- Instruction handlers for initialize, trade, add/remove liquidity, resolve, settle position, and settle LP.
- Account serialization and validation aligned with the v1 spec.
- Program-side bounded quote verification derived from the canonical fixed-point core.

Exit criteria:
- The program can process happy-path integration tests locally.
- Trade verification logic matches the offchain quote model for allowed Normal trades.
- Account transitions preserve the same invariants as the Rust core.

Primary files:
- [`/Users/aaditjerfy/distribution-markets/specs/solana-normal-v1.md`](/Users/aaditjerfy/distribution-markets/specs/solana-normal-v1.md)
- [`/Users/aaditjerfy/distribution-markets/src/solana_v1.rs`](/Users/aaditjerfy/distribution-markets/src/solana_v1.rs)

## Phase 3: Quote and Transaction SDK

Goal:
- Give clients a single supported way to build quotes and transactions.

Must-haves:
- Read market state and derive a Normal trade quote offchain.
- Build trade, LP, and settlement transactions against the Solana program.
- Reproduce bounded verification results locally for user-facing previews.

Exit criteria:
- The SDK can generate quotes that the program accepts when market state matches.
- The SDK can reject stale or invalid quotes before submission.
- The SDK becomes the only supported quoting surface for frontend and operator tooling.

## Phase 4: Thin Product UI

Goal:
- Provide a minimal interface for operators, traders, and LPs.

Must-haves:
- Market creation and resolution views.
- Trade form showing proposed `(mu, sigma)`, collateral, and implied distribution.
- LP add/remove controls and settlement screens.

Exit criteria:
- A user can run one full market lifecycle without touching raw instruction payloads.
- The UI surfaces quote expiry, settlement status, and residual cash behavior clearly.

## Phase 5: Controlled Devnet Launch

Goal:
- Run a small number of capped Normal markets on devnet and learn from real transaction flow.

Must-haves:
- Position and LP caps.
- Runbooks for oracle resolution, incident handling, and quote failures.
- Monitoring for rejected quotes, settlement mismatches, and vault drift.

Exit criteria:
- At least one market completes from initialization through final settlement on devnet.
- Observed results are consistent with the model within acceptable tolerances.
- The team has enough operational confidence to decide whether to advance to a beta release.

## Product Rule For v1

Do not expand supported live distributions until the Normal-only path is operationally excellent. New distribution families belong to the research engine until the deployable Normal product is stable, testable, and cheap enough to operate.
