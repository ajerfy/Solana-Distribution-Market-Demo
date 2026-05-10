# Parabola

Parabola is a Solana Mobile estimation market app. Instead of forcing users into binary yes/no positions, Parabola lets them express a full belief distribution using an expected value (“average”) and uncertainty (“confidence” or explicit standard deviation in advanced mode).

Our demo focuses on a live combat-sports market, portfolio management, the pricing/liquidity engine, and a wallet-connected mobile flow on Solana Mobile. The core Normal-distribution engine math and quote logic are already implemented, so the app is not just visualizing fake market states, but generating distribution-aware quote previews from the actual estimation-market pricing model. The current demo is deployed against Solana devnet through a real mobile wallet-connected flow.

## Demo Video (Youtube Link)

<p align="center">
  <a href="https://www.youtube.com/watch?v=FdCR-rBYMu8">
    <img src="https://img.youtube.com/vi/FdCR-rBYMu8/maxresdefault.jpg" width="700">
  </a>
</p>

## Screenshots

### 1. Opening / Brand Screen
<img width="294" height="619" alt="Screenshot 2026-05-07 at 7 19 25 AM" src="https://github.com/user-attachments/assets/3c8e5aea-65bb-4aa2-a474-fb96446d5073" />

### 2. Markets Home + Filters
<img width="296" height="624" alt="Screenshot 2026-05-07 at 7 20 09 AM" src="https://github.com/user-attachments/assets/6f172bc6-e2af-4046-a00a-10547b6e26a2" />

### 3. Live Market Detail
<img width="298" height="620" alt="Screenshot 2026-05-07 at 7 21 08 AM" src="https://github.com/user-attachments/assets/18866110-a60d-4f3e-9d1b-cde1d9c205f4" />

### 4. Bet Sheet with Average / Confidence
<img width="295" height="617" alt="Screenshot 2026-05-07 at 7 20 42 AM" src="https://github.com/user-attachments/assets/5d1bdd33-01ba-4646-a618-7340b2c0a199" />

### 5. Advanced Standard Deviation Controls
<img width="251" height="532" alt="Screenshot 2026-05-07 at 7 22 19 AM" src="https://github.com/user-attachments/assets/d23f3876-a8ec-454e-8e55-23cdc1b70fd4" />

### 6. Portfolio / Positions
<img width="249" height="531" alt="Screenshot 2026-05-07 at 7 23 05 AM" src="https://github.com/user-attachments/assets/46785f54-4e2b-4fc9-97db-2b43f7eeced8" />

### 7. Engine / Market Internals
<img width="251" height="527" alt="Screenshot 2026-05-07 at 7 23 34 AM" src="https://github.com/user-attachments/assets/79666736-3c09-437d-b6f6-a37bc66a6a5b" />
<img width="252" height="526" alt="Screenshot 2026-05-07 at 7 23 53 AM" src="https://github.com/user-attachments/assets/eb207311-3aea-4e68-ad8e-77224a51440c" />

### 8. Wallet
<img width="251" height="528" alt="Screenshot 2026-05-07 at 7 24 31 AM" src="https://github.com/user-attachments/assets/3351ced6-e5d3-48a3-9bc1-944d8f58b922" />

## What Parabola Does

Traditional prediction markets are strongest at binary questions. But many important forecasts are not binary: price levels, uncertainty bands, regime changes, and continuously updating expectations. Parabola is built around “estimation markets,” where users trade on a distribution rather than a single yes/no outcome.

Under the hood, Parabola already includes a working quote engine for Normal-distribution estimation markets. When a user changes average, confidence, or advanced standard deviation settings, the app recomputes the proposed distribution, collateral preview, and quote state from the underlying pricing model rather than from a static frontend mock.

In the current demo, users can:

- browse and filter markets from a mobile-first home screen
- open a live market and shape a forecast with average and confidence controls
- switch into advanced mode to explicitly choose standard deviation
- preview and place a wallet-connected mobile action
- review positions and resolved P/L in the portfolio view
- inspect the market engine and liquidity mechanics
- explore perpetual estimation markets and regime indices as higher-level market primitives

## How The Blockchain Interaction Works

Parabola is built as a Solana Mobile app with a real wallet connection flow and is currently demonstrated on Solana devnet.

### Current demo transaction flow

1. The Android app connects to a Solana wallet using **Solana Mobile Wallet Adapter**.
2. When the user taps the submit action, the app constructs a short **devnet memo transaction** that represents the selected market action for demo purposes.
3. The wallet signs the transaction on-device.
4. The app then broadcasts the signed transaction to **Solana devnet** and performs confirmation checks.
5. The result is surfaced back in the app as a wallet-connected mobile transaction flow.

### Why this matters

For the hackathon demo, this proves the core mobile blockchain interaction:
- wallet discovery and authorization on Solana Mobile
- transaction signing from the mobile wallet
- transaction submission and confirmation on Solana devnet

The current demo transaction is a memo-based execution path rather than the full production market program call. That keeps the mobile UX real while allowing us to demonstrate the complete on-device wallet flow today.

## Live Data / Backend Flow

Parabola is not just a static mockup. It can consume live payloads from a Rust backend.

- The mobile app fetches market payloads from a live backend endpoint
- The live backend is implemented in Rust
- It serves the structured market payload the Android client renders
- It is designed to support live perp-style market views and dynamic market data
- The backend also supports live quote and market-state generation for the estimation-market demo, so the client can render pricing-aware market views rather than static screenshots

To run the live backend locally:

```bash
cd "/Users/your-username/Solana Distribution Market Demo"
source $HOME/.cargo/env
cargo run -p live-perp-backend
```

## Web app (scaffold)

A Vite + React client lives in [`apps/web`](apps/web). It calls the same `GET /api/demo-payload` and `GET /healthz` as the mobile app. The backend enables **CORS** for local dev (`http://localhost:5173` by default); override with **`PARABOLA_CORS_ORIGINS`** (comma-separated) when you deploy.

```bash
cd apps/web
npm install
npm run dev
```

During **`npm run dev`**, omit **`VITE_PARABOLA_API_BASE`** so Vite proxies to `http://127.0.0.1:8787`. Set it only for a remote API or production builds.
