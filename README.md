# DistributionMarket

Rust research and porting library for distribution prediction markets, intended to evolve into a Solana-oriented implementation.

This repo now includes:

- A research-oriented multi-distribution market engine for Normal, Uniform, Cauchy, and Student's t markets.
- A fixed-point Normal math bridge and Solidity-parity tests to support eventual Solana porting work.
- A reusable simulation layer and interactive CLI seeded by the example scenarios.
- A Normal-only product roadmap and devnet launch checklist for the Solana v1 path.

## Dependencies

- Rust toolchain with `cargo` and `rustc` available in your shell
- The crates declared in [`Cargo.toml`](/Users/your-username/distribution-markets/Cargo.toml), which Cargo will download automatically on first build
- A Unix-like shell environment that can run `source $HOME/.cargo/env` before invoking Cargo commands
- Network access the first time you build, so Cargo can fetch Rust dependencies

## Run

```bash
cd /Users/your-username/distribution-markets
source $HOME/.cargo/env
cargo test
```

```bash
cd /Users/your-username/distribution-markets
source $HOME/.cargo/env
cargo run --bin simulate
```

```bash
cd /Users/your-username/distribution-markets
source $HOME/.cargo/env
cargo run --bin simulate -- list
cargo run --bin simulate -- run normal
cargo run --bin simulate -- run uniform
cargo run --bin simulate -- run cauchy
cargo run --bin simulate -- run student_t
```

## Product Path

- The current product roadmap lives in [`/Users/your-username/distribution-markets/specs/normal-v1-product-roadmap.md`](/Users/your-username/distribution-markets/specs/normal-v1-product-roadmap.md).
- The current devnet launch checklist lives in [`/Users/your-username/distribution-markets/specs/devnet-launch-checklist.md`](/Users/your-username/distribution-markets/specs/devnet-launch-checklist.md).
- The canonical Solana-facing Normal market spec lives in [`/Users/your-username/distribution-markets/specs/solana-normal-v1.md`](/Users/your-username/distribution-markets/specs/solana-normal-v1.md).
