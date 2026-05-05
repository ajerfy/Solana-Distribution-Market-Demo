# Normal V1 Devnet Launch Checklist

## Core Readiness

- The fixed-point Normal engine is the only live economic path used by product code.
- Trade quote verification is deterministic, bounded, and conservatively collateralized.
- LP add/remove and settlement semantics are frozen.
- Stress and invariant tests pass on every release candidate.

## Program Readiness

- A Solana program crate exists and compiles locally.
- Account layouts match the v1 spec exactly.
- Initialize, trade, liquidity, resolve, and settlement instructions pass integration tests.
- Replay, stale quote, and unauthorized resolution paths fail safely.

## SDK Readiness

- The SDK can read a market account and build a valid Normal trade quote.
- The SDK can build liquidity and settlement transactions.
- The SDK catches expired or stale quotes before submission.
- Local quote verification matches program acceptance behavior.

## Frontend Readiness

- Users can inspect a market’s current Normal distribution and active status.
- Users can preview trade collateral and quote expiry before signing.
- LP users can add/remove liquidity and inspect share balances.
- Resolved markets expose settlement actions and remaining vault state clearly.

## Operations Readiness

- Oracle source, authority, and fallback rules are documented.
- There is a runbook for failed resolutions, stuck settlements, and quote mismatches.
- Market size and LP exposure caps are defined for the first devnet cohort.
- Logs and dashboards are in place for rejected instructions and vault movements.

## Devnet Success Conditions

- At least one market is initialized, traded, LP-managed, resolved, and fully settled on devnet.
- Vault balances reconcile with expected payouts and residual dust policy.
- No undercollateralized trade is accepted.
- Operator and user flows are understandable without internal debugging knowledge.
