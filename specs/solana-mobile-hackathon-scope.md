# Solana Mobile Hackathon Scope

## Demo Goal

Ship a Solana Mobile demo that lets a user open the app, inspect one seeded Normal-distribution market, submit a trader quote, and sign a real Solana-style trade path with the smallest possible protocol surface.

## Scope Freeze

- Live distribution family: Normal only
- Primary user flow: trader opens a market and submits a trade
- Market lifecycle for demo: seeded market, trade, optional resolution, optional settlement
- Quoting model: offchain quote generation with bounded onchain-style verification
- Mobile target: Android app for the Solana dApp Store

## Must-Haves

- One seeded demo market backed by the fixed-point Normal engine
- One executable serialized trade-instruction path from SDK to program crate
- One small SDK surface for quote generation and instruction packaging
- One Android app scaffold with market, trade, and wallet-status views
- One demo script that clearly shows why distribution markets are different from binary prediction markets

## Nice-To-Haves

- Resolution and settlement flow in the app
- One additional seeded market scenario
- Read-only LP visibility in the UI
- Better charting of the implied Normal distribution

## Stretch Goals

- LP add/remove flows in the app
- On-device historical position view
- Real oracle integration
- Multiple live markets

## Explicit Cuts

- No live multi-distribution trading
- No generalized market creation flow
- No full dispute system
- No polished LP management UX for the initial submission
- No attempt to productionize the entire protocol before demo readiness

## Immediate Build Order

1. Keep the current program crate and root Normal engine stable.
2. Use the new SDK crate to finalize trader-first quote and instruction generation.
3. Connect the Android app scaffold to seeded market data and Android-ready trade intents.
4. Only after the trade path feels stable, add resolution and settlement as secondary demo features.
