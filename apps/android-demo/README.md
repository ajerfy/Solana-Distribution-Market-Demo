# Android Demo Scaffold

This folder is the starting point for the Solana Mobile hackathon app.

## Intended Flow

- Screen 1: seeded market overview
- Screen 2: trader quote builder for `(mu, sigma)`
- Screen 3: wallet or transaction preview and submission state

## Current Integration Target

The app now consumes a generated asset backed by the trader-first SDK surface from:
- [`/Users/your-username/Solana Distribution Market Demo/crates/normal-v1-sdk/src/lib.rs`](/Users/your-username/Solana%20Distribution%20Market%20Demo/crates/normal-v1-sdk/src/lib.rs)
- [`/Users/your-username/Solana Distribution Market Demo/apps/android-demo/app/src/main/assets/demo_market.json`](/Users/your-username/Solana%20Distribution%20Market%20Demo/apps/android-demo/app/src/main/assets/demo_market.json)

The Android app does not need LP management for the first hackathon milestone.

## Use It Now

1. Regenerate the demo asset from the SDK:

```bash
cd "/Users/your-username/Solana Distribution Market Demo"
source $HOME/.cargo/env
cargo run -p normal-v1-sdk --bin export_demo_payload -- apps/android-demo/app/src/main/assets/demo_market.json
```

2. Open [`/Users/your-username/Solana Distribution Market Demo/apps/android-demo`](/Users/your-username/Solana%20Distribution%20Market%20Demo/apps/android-demo) in Android Studio.
3. Run the `app` target on an emulator or Android device.

## Current App Milestone

- Render one seeded market from a real SDK-generated asset
- Show preset Normal trade quotes with collateral previews
- Render serialized trade intents that can later be handed to wallet submission
- Leave live wallet signing and transaction submission as the next milestone
