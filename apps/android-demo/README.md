# Android Demo Scaffold

This folder is the starting point for the Solana Mobile hackathon app.

## Intended Flow

- Screen 1: seeded market overview
- Screen 2: trader quote builder for `(mu, sigma)`
- Screen 3: wallet or transaction preview and submission state

## Current Integration Target

The app should consume the trader-first SDK surface from:
- [`/Users/your-username/Solana Distribution Market Demo/crates/normal-v1-sdk/src/lib.rs`](/Users/your-username/Solana%20Distribution%20Market%20Demo/crates/normal-v1-sdk/src/lib.rs)

The Android app does not need LP management for the first hackathon milestone.

## First App Milestone

- Render one seeded market
- Show current `mu`, `sigma`, and collateral preview
- Accept target `mu` and `sigma`
- Render the serialized trade intent returned by the SDK or companion service
- Leave actual signing and submission as the next incremental step if time is tight
