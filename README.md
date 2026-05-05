# Solana Distribution Market Demo

Hackathon-focused Solana Mobile demo repo for a Normal-only distribution prediction market.

This repo now includes:

- A research-oriented multi-distribution market engine for Normal, Uniform, Cauchy, and Student's t markets.
- A fixed-point Normal math bridge and Solidity-parity tests to support eventual Solana porting work.
- A reusable simulation layer and interactive CLI seeded by the example scenarios.
- A Normal-only product roadmap and devnet launch checklist for the Solana v1 path.
- A dedicated workspace crate at [`/Users/your-username/Solana Distribution Market Demo/programs/normal-v1-program`](/Users/your-username/Solana%20Distribution%20Market%20Demo/programs/normal-v1-program) for the Normal-only program-facing scaffold.
- A tiny trader-first SDK at [`/Users/your-username/Solana Distribution Market Demo/crates/normal-v1-sdk`](/Users/your-username/Solana%20Distribution%20Market%20Demo/crates/normal-v1-sdk).
- An Android demo scaffold at [`/Users/your-username/Solana Distribution Market Demo/apps/android-demo`](/Users/your-username/Solana%20Distribution%20Market%20Demo/apps/android-demo).

## Dependencies

- Rust toolchain with `cargo` and `rustc` available in your shell
- The crates declared in [`Cargo.toml`](/Users/your-username/Solana%20Distribution%20Market%20Demo/Cargo.toml), which Cargo will download automatically on first build
- A Unix-like shell environment that can run `source $HOME/.cargo/env` before invoking Cargo commands
- Network access the first time you build, so Cargo can fetch Rust dependencies

## Run

```bash
cd "/Users/your-username/Solana Distribution Market Demo"
source $HOME/.cargo/env
cargo test
```

```bash
cd "/Users/your-username/Solana Distribution Market Demo"
source $HOME/.cargo/env
cargo run --bin simulate
```

```bash
cd "/Users/your-username/Solana Distribution Market Demo"
source $HOME/.cargo/env
cargo run --bin simulate -- list
cargo run --bin simulate -- run normal
cargo run --bin simulate -- run uniform
cargo run --bin simulate -- run cauchy
cargo run --bin simulate -- run student_t
```

```bash
cd "/Users/your-username/Solana Distribution Market Demo"
source $HOME/.cargo/env
cargo test --workspace
```

## Hackathon Path

- The current mobile-track scope freeze lives in [`/Users/your-username/Solana Distribution Market Demo/specs/solana-mobile-hackathon-scope.md`](/Users/your-username/Solana%20Distribution%20Market%20Demo/specs/solana-mobile-hackathon-scope.md).
- The Normal-only product roadmap lives in [`/Users/your-username/Solana Distribution Market Demo/specs/normal-v1-product-roadmap.md`](/Users/your-username/Solana%20Distribution%20Market%20Demo/specs/normal-v1-product-roadmap.md).
- The canonical Solana-facing Normal market spec lives in [`/Users/your-username/Solana Distribution Market Demo/specs/solana-normal-v1.md`](/Users/your-username/Solana%20Distribution%20Market%20Demo/specs/solana-normal-v1.md).

## Android Demo

- The Android app scaffold lives in [`/Users/your-username/Solana Distribution Market Demo/apps/android-demo`](/Users/your-username/Solana%20Distribution%20Market%20Demo/apps/android-demo).
- It currently loads seeded market data and preset trade intents from [`/Users/your-username/Solana Distribution Market Demo/apps/android-demo/app/src/main/assets/demo_market.json`](/Users/your-username/Solana%20Distribution%20Market%20Demo/apps/android-demo/app/src/main/assets/demo_market.json).
- Regenerate that asset from the real SDK with:

```bash
cd "/Users/your-username/Solana Distribution Market Demo"
source $HOME/.cargo/env
cargo run -p normal-v1-sdk --bin export_demo_payload -- apps/android-demo/app/src/main/assets/demo_market.json
```

- Then open [`/Users/your-username/Solana Distribution Market Demo/apps/android-demo`](/Users/your-username/Solana%20Distribution%20Market%20Demo/apps/android-demo) in Android Studio and run the `app` target on an emulator or device.
