# PARABOLA — Pitch Deck Spec
> Solana Distribution Markets · Mobile Hackathon Build
> A complete, slide-by-slide blueprint to build the deck inside Canva.

---

## 0. How to use this document

This file is a **build script** for the Canva deck — every slide has copy, layout, color/font calls, and a chart spec. Workflow:

1. Open Canva → Create a design → **Presentation (16:9)**.
2. Apply the brand kit below (Section A) as the document's color palette and font set.
3. Walk through Section B slide-by-slide. Each slide gives you (a) headline/copy, (b) layout in plain English, (c) any chart/graphic to add, (d) which on-device screenshot or motif to drop in.
4. Section C lists every chart to draw and exact data to plug in (drop into Canva's "Charts" element).
5. Section D is the screenshot capture list — open the Android app, capture these screens at 2x density, drop them into the device-mockup elements on slides 4, 8, 9, 12.

**Vibe target:** Linear / Vercel / Arc Browser meets fintech. Generous white-space, oversized type, monospace numerics, one bold accent at a time, never more than 2 colors per chart.

---

## A. Brand Kit (set this in Canva first)

### A.1 Color palette
Set these as the deck's *Brand Kit colors* (`Brand → Brand Kit → +`):

| Token | Hex | Use |
|---|---|---|
| Ink | `#0F1419` | Primary text on light, primary surface on dark |
| Cream | `#FAFAF7` | Default page background (light slides) |
| Carbon | `#0A0D12` | Background (dark / "engine" slides) |
| Lime (signal) | `#B8FF66` | The hero accent. One use per slide max. |
| Long-green | `#16A34A` | Long / upside / win |
| Short-red | `#DC2626` | Short / risk / loss |
| Crowd-blue | `#2563EB` | The crowd / consensus distribution |
| Chain-violet | `#7C3AED` | "On-chain" badge, Solana motifs |
| Warn-amber | `#D97706` | Caution, slippage |
| Hairline | `#0F141914` | Borders on light bg (8% ink) |

> Pulled directly from the app's [Theme.kt](apps/android-demo/app/src/main/java/com/solanadistributionmarketdemo/ui/Theme.kt) so the deck looks like the product.

### A.2 Type pairing
- **Headlines / display:** Inter Tight (Canva has it). Weights: Bold 700 for slide titles (60–96 pt), SemiBold 600 for section labels.
- **Body:** Inter, Regular 400 (18–22 pt body, 14 pt captions).
- **Numerics / code / "engine" copy:** JetBrains Mono, Medium 500. *Always* monospace any number, ticker, hex string, or formula in the deck — this is the visual signature of the product.

### A.3 Layout grid
- 16:9, 1920×1080.
- 64 px outer margin. 12-column grid, 24 px gutter.
- Default vertical rhythm: title → 32 px gap → body.
- Card radius: **20 px** everywhere. No drop shadows; use a 1 px hairline border at 8 % ink instead. (Matches `RoundedCornerShape(20.dp)` in the app.)

### A.4 Iconography
Three motifs reappear across the deck — make them as Canva elements once and reuse:

- **The Parabola glyph** — a Normal-curve over a circle on a faint grid, with one green and one red dot pulsing on its flanks. Lifted from the app's [Entrance.kt](apps/android-demo/app/src/main/java/com/solanadistributionmarketdemo/ui/Entrance.kt) splash. Use as the deck's repeating mark.
- **Two-curve diagram** — a faint blue "crowd" Normal and a bold lime "you" Normal overlaid. This is the product in one image.
- **Pill / tag** — uppercase mono text in a 1 px-bordered rounded pill. Use for `ON-CHAIN`, `DEVNET`, `LIVE`, `NORMAL v1`, `SOLANA MOBILE`.

---

## B. Slide-by-slide

> **18 slides.** Three are optional appendix. Each slide has a single job.

### Slide 1 — Cover

- **Headline:** `Parabola.`
- **Sub:** Distribution markets for Solana Mobile.
- **Tag pill (top-right):** `SOLANA MOBILE HACKATHON · NORMAL v1`
- **Layout:** Full-bleed Cream background. Title left-aligned, 220 pt Inter Tight Bold, Ink. Sub directly under, 36 pt, 60 % ink. Center-right of slide: the **Parabola glyph** (Section A.4) at ~700 px wide, with the green dot pulsing top-right of the curve, red dot mid-left.
- **Bottom-left footer:** monospace `parabola.markets · v0.1 · @your-handle`

### Slide 2 — The hook (one sentence)

- **Headline (single line, 110 pt):** Binary "yes/no" markets throw away the answer.
- **Sub (32 pt, 70 % ink, max 2 lines):** The world is continuous. Inflation isn't yes/no. Fight outcomes aren't yes/no. SOL price isn't yes/no. Today's prediction markets force every question into a coin flip — and lose every other dimension of what traders actually believe.
- **Layout:** Cream. Centered. Use Crowd-blue underline behind "yes/no" as a marker-strike. Nothing else on the slide.

### Slide 3 — Show the loss (the side-by-side)

- **Headline:** What you can say today vs. what you actually believe.
- **Layout:** Two cards, 50/50.
  - **Left card** (titled `BINARY MARKET — POLYMARKET-STYLE`): a horizontal bar showing "YES 62 ¢ / NO 38 ¢". Below: "You can only pick a side and a price."
  - **Right card** (titled `DISTRIBUTION MARKET — PARABOLA`): the **two-curve diagram** (Crowd-blue Normal + Lime "you" Normal). Below: "You post a full belief: μ (your estimate) and σ (your confidence)."
- **Footer caption (16 pt mono):** Same question. Two universes of expressiveness.
- **Chart:** see C.1.

### Slide 4 — The product (live screen)

- **Headline:** It's a real app. It's already live on devnet.
- **Layout:** Left half — copy stack:
  - Pill `LIVE NOW · SOLANA DEVNET`
  - 56 pt: "Parabola is the first mobile-native distribution market on Solana."
  - 3 bullets, 22 pt:
    - **Markets that take a curve, not a side.** Every trade posts (μ, σ).
    - **Three primitives, one engine.** Estimation · Regime Index · Perpetual.
    - **Live oracle data.** Polymarket Gamma + Pyth Hermes feeding markets in real time.
- **Right half:** Android device frame (Canva has free frames — search "Pixel mockup"). Drop in **Screenshot S1** (markets list, see Section D).
- **Bottom strip:** four monospace stat tiles — `10,638 LOC · 4 distributions · 64 max open trades · ~6 ms quote verify`.

### Slide 5 — The thesis (one slide, no decoration)

- **Headline:** Thesis.
- **Body (40 pt, three short paragraphs, generous leading):**

> **Markets are belief-aggregation machines.** When you compress beliefs to a binary, you get a probability — and you throw away every other moment of the distribution.
>
> **A price is a point. A distribution is a posture.** Distribution markets let traders express *what they think*, *how confident they are*, and *how the tails look* — in one trade.
>
> **Solana is the only chain where this fits.** Sub-cent fees and sub-second slots make it economical to post real distributions instead of binary tickets. Mobile Wallet Adapter makes it pocket-native.

- **Layout:** Cream. Three blocks of body with thin Lime hairline (4 px tall × 64 px wide) marker before each paragraph.

### Slide 6 — How a trade works (the money diagram)

- **Headline:** What a trade actually does.
- **Visual:** A horizontal four-step ribbon, each step a 20 px-radius Cream card with 1 px hairline:
  1. **You read the market.** State: current `(μ₀, σ₀)`, backing `b`, invariant `k`.
  2. **You propose a new shape.** Move to `(μ₁, σ₁)`. The SDK builds a quote.
  3. **The program verifies.** Bounded-grid search proves your collateral covers the worst-case directional loss.
  4. **At resolution.** Realized outcome `x*` settles your position against your stored old/new pair.
- **Below the ribbon:** mono one-liner `collateral = max over x of [ f_old(x) − f_new(x) ]   →   verified onchain via 4096+4096 sample search`
- **Footer caption:** "Offchain quote, onchain verify. Cheap and deterministic." (Source: [solana-normal-v1.md](specs/solana-normal-v1.md).)

### Slide 7 — The math, plainly (this is the wow slide)

- **Headline:** A position is a curve.
- **Layout:** Big chart center-stage (Section C.2). Four numeric callouts in monospace mini-cards orbiting the chart corners:
  - Top-left `μ — your estimate`
  - Top-right `σ — your confidence`
  - Bottom-left `λ = k · √(2σ√π) — invariant scale`
  - Bottom-right `b ≥ max f(x) — solvency floor`
- **Caption (under chart, 18 pt, mono):** *Implementation: deterministic i128 fixed-point Gaussian math — Solidity-parity, ready to port to a Solana program.*

### Slide 8 — The three primitives

- **Headline:** Three asset shapes. One distribution engine.
- **Layout:** Three cards horizontal.

| Card | Pill | Headline | Body | Chart |
|---|---|---|---|---|
| 1 | `ESTIMATION` (Crowd-blue) | A continuous market on any number. | "What will US CPI YoY print for May? Post a full curve, not a side." | mini bell-curve preview |
| 2 | `REGIME INDEX` (Chain-violet) | A basket of yes/no constituents. | "Compose a thesis — 'AI eats search 2026' — from many small markets, traded as one token." | mini stacked-bar |
| 3 | `PERP` (Lime) | A perpetual where funding *is* the distribution edge. | "AMM-μ vs anchor-μ drives funding. Live SOL/USD via Pyth right now." | mini funding-rate sparkline |

- **Footer caption:** Sources: [src/regime_index.rs](src/regime_index.rs), [crates/live-perp-backend/src/main.rs](crates/live-perp-backend/src/main.rs).

### Slide 9 — Inside the app (3-up screens)

- **Headline:** What you ship.
- **Layout:** Three Pixel device frames in a row, 32 px gap.
  - Frame 1: **Markets list** — Screenshot S1.
  - Frame 2: **Bet sheet** with the dual-curve chart and (μ, σ) sliders — Screenshot S3.
  - Frame 3: **Engine** screen showing live feeds — Screenshot S5.
- **Below each frame:** a 16 pt mono caption naming the screen.
- **Side note (right edge, vertical mono):** `Compose · Material 3 · MWA`

### Slide 10 — Live data is already wired up

- **Headline:** Not a mock. It's calling real oracles.
- **Visual (full-width):** A flow diagram, left-to-right:
  - `POLYMARKET (Gamma + CLOB)` → `parabola live-perp-backend (Axum, Rust)` → `Pyth Hermes (SOL/USD)` → `Android client (3 s poll)` → `live_feed badge in UI`.
  - Each node is a Cream card; arrows are 2 px Lime; latency labels in mono between nodes (`~600 ms`, `~120 ms`).
- **Below diagram:** four mono stat pills:
  - `Endpoint /api/demo-payload`
  - `Refresh 3 s`
  - `Mode live | demo | degraded | error`
  - `Devnet slot tracking via JSON-RPC`
- Source: [crates/live-perp-backend/src/main.rs](crates/live-perp-backend/src/main.rs).

### Slide 11 — Architecture

- **Headline:** One engine. Five layers.
- **Visual:** A stacked-block diagram, top to bottom (each block a Cream card with mono code path on the right):

```
ANDROID APP        Jetpack Compose · Material 3 · MWA wallet adapter   apps/android-demo/
       ▲
SDK                Quote builder · trade-intent serializer              crates/normal-v1-sdk/
       ▲
SOLANA PROGRAM     PDAs · token vault · bounded quote-verify            programs/normal-v1-program/
       ▲
FIXED-POINT CORE   i128 Gaussian math · solvency search · invariants    src/normal_market.rs
       ▲
RESEARCH ENGINE    Normal · Uniform · Cauchy · Student's t simulations  src/distributions.rs
```

- **Right column callout (mono, lime):** `~10.6k LOC. ~30 tests pass. Rust + Kotlin.`

### Slide 12 — Why Solana, why mobile

- **Headline:** Distribution markets only work where this is true.
- **Three numbered cards (Cream):**
  1. **Cents per trade.** Posting a curve is not a binary ticket; it has a tail. Solana fees + slot times keep that economical.
  2. **Mobile Wallet Adapter.** Real signing UX — no extension hacks, no QR detours — flows straight through `ActivityResultSender`. (Source: [WalletSubmitter.kt](apps/android-demo/app/src/main/java/com/solanadistributionmarketdemo/WalletSubmitter.kt).)
  3. **dApp Store distribution.** The Solana Mobile dApp Store is the venue. We're built natively for it (Compose, dark/light themes, onboarding overlay).

### Slide 13 — Live demo (anchor for the talk)

- **Headline:** Demo.
- **Layout:** Single Pixel frame, centered, large. Screenshot S2 (a featured live market with the live-feed pill and the dual-curve chart visible).
- **Three callouts pointing into the screen:**
  - top-left arrow → "Crowd Normal — what the market thinks."
  - mid-right arrow → "Your Normal — drag (μ, σ) sliders, see collateral update in real time."
  - bottom arrow → "Quote envelope hex — what gets serialized + signed."
- **Sub:** "60 seconds: open app → pick UFC 328 → drag your curve → sign with Phantom → trade lands on devnet."

### Slide 14 — The roadmap (where we're going)

- **Headline:** Roadmap.
- **Visual:** A horizontal timeline with five nodes, the first three marked done (Lime fill), last two outline.

| Phase | Label | Status |
|---|---|---|
| 1 | Freeze v1 economic core (Normal, fixed-point) | ✓ Done |
| 2 | Solana program scaffold + lifecycle tests | ✓ Done |
| 3 | Quote/transaction SDK | ✓ Done |
| 4 | Thin product UI (this app) | ◐ In progress |
| 5 | Capped devnet launch — runbooks, caps, dashboards | ○ Next |

- **Right-side callout (mono, 18 pt):** `Source: specs/normal-v1-product-roadmap.md`
- **Below timeline:** "Beyond v1 — Cauchy & Student's t markets, regime-index baskets, mainnet."

### Slide 15 — What's hard (the moat)

- **Headline:** Hard things, done.
- **Four cards (2×2):**
  - **Deterministic Gaussian math in i128.** Bounded-grid collateral search is provably solvent and replayable.
  - **Offchain-quote / onchain-verify.** No expensive Newton search on-chain; bounded sample policy keeps compute budget predictable.
  - **Three asset primitives, one core.** Estimation, Regime Index, and Perp all settle through the same Normal engine.
  - **Mobile-native UX.** Live data, onboarding, dark/light, Compose — not a Web2 wrapper.

### Slide 16 — Asks / closing

- **Headline:** What we want.
- **Body, three short lines (40 pt):**
  - **Hackathon judges:** play with it on devnet today.
  - **Solana Mobile team:** dApp Store launch slot.
  - **Liquidity providers:** join the capped devnet cohort.
- **Below:** big mono call sign — `parabola.markets · github/your-handle/Solana-Distribution-Market-Demo`
- **Bottom-right:** Parabola glyph at 320 px, green dot lit.

### Slide 17 (optional appendix) — Numbers that matter

- **Headline:** Spec sheet.
- **Layout:** Two-column mono table (everything in JetBrains Mono):

```
Distributions supported (research)        Normal · Uniform · Cauchy · Student's t
Distributions live on Solana (v1)         Normal
Collateral search (coarse + refine)       4096 + 4096 samples
Default taker fee                         100 bps  (1.00 %)
Min taker fee                             0.001 collateral units
Max collateral per trade (devnet)         10.0
Max open trades                           64
Sigma floor (fixed-point)                 1e-6
Quote envelope expiry                     +10 slots from quote slot
Live oracle poll interval                 3 s
LP shares model                           Fungible SPL pro rata
Repo                                      ~10.6 k LOC · Rust + Kotlin
```

### Slide 18 (optional appendix) — Risk / honest caveats

- **Headline:** What's not done yet.
- **Bullet list (24 pt, neutral tone):**
  - Trade signing on devnet is currently a memo-tx demo — full instruction submission is the next milestone.
  - Multi-distribution live trading is research-only; v1 ships Normal-only by design.
  - LP add/remove UX in the app is read-only for the hackathon scope.
  - Resolution + settlement flows are scaffolded in the program crate; mainnet hardening still ahead.
- **Sub-line:** *Sources: [IMPLEMENTATION_NOTES.txt](IMPLEMENTATION_NOTES.txt), [specs/devnet-launch-checklist.md](specs/devnet-launch-checklist.md).*

---

## C. Charts to draw (drop into Canva via *Element → Chart*, then style)

> Style every chart the same way: no chart title, no legend box, hairline gridlines at 8 % ink, axis labels in JetBrains Mono 12 pt 60 % ink, numbers as integers when possible.

### C.1 — Binary vs. Distribution (Slide 3)

- **Type:** Two adjacent visuals, one bar + one curve overlay, drawn as separate Canva charts.
- **Left (bar):** horizontal stacked bar, single row.
  - YES segment: 62, fill `#2563EB` (Crowd-blue).
  - NO segment: 38, fill `#0F141914` (hairline ink).
- **Right (line):** two Normal PDFs on a shared x-axis from 0.0 to 6.0.
  - Crowd: μ=3.0, σ=0.6, stroke `#2563EB` 2 px.
  - You: μ=3.4, σ=0.4, stroke `#B8FF66` 4 px (the hero).
  - Sample 80 points each. (You can plug these into any spreadsheet using `=NORM.DIST(x, μ, σ, FALSE)` then paste the table into the chart.)

### C.2 — The hero curve (Slide 7)

- **Type:** Line chart, 2 series, x ∈ [70, 100] (use the demo CPI range), 100 points.
- **Series A — `crowd`:** μ=82.5, σ=10.65 (real numbers from [demo_market.json](apps/android-demo/app/src/main/assets/demo_market.json)). Stroke `#2563EB` 2 px, fill below curve at 6 % opacity.
- **Series B — `you`:** μ=86.0, σ=8.5. Stroke `#B8FF66` 5 px, fill below curve at 14 % opacity.
- Add a single dashed vertical line at x=88 labeled mono `realized x*` in 60 % ink.

### C.3 — Live data flow (Slide 10)

- Not a chart — a hand-built diagram. Use Canva's *Lines & Shapes*. 5 nodes, 4 arrows. Arrows in Lime (`#B8FF66`), 3 px stroke, 8 px arrowhead.

### C.4 — Roadmap timeline (Slide 14)

- Use Canva's *Timeline* element, 5 nodes. Node 1–3: filled Lime. Node 4: half-filled. Node 5: outline only. Connector line: 2 px ink, 30 % opacity.

### C.5 — Funding-rate sparkline (Slide 8, perp card)

- 24-point line chart, no axes, no gridlines.
- Synthetic data (sine + noise around 0): `0.03, 0.05, 0.04, 0.07, 0.09, 0.06, 0.04, 0.02, -0.01, -0.03, -0.04, -0.02, 0.00, 0.02, 0.04, 0.05, 0.03, 0.01, -0.02, -0.05, -0.03, 0.00, 0.02, 0.04`.
- Single stroke, Lime, 3 px.

---

## D. Screenshots to capture from the Android app

Open the app on a Pixel emulator (or a real device) and capture these screens at 2x density. Save as PNG with transparent device frame removed (Canva will re-frame them).

| ID | Screen | How to reach it |
|---|---|---|
| S1 | **Markets list (default)** | Launch app → tap "Trade on estimation markets" on the entrance → first screen |
| S2 | **Featured live market detail** | Markets list → tap the market with the `LIVE` pill (UFC 328) |
| S3 | **Bet sheet open** | Inside S2, tap the bottom CTA → modal sheet appears with the dual-curve chart and (μ, σ) sliders |
| S4 | **Perp detail** | Markets list → filter to `Perps` → tap the SOL perp |
| S5 | **Engine screen** | Bottom nav → `Engine` |
| S6 | **Portfolio / Positions** | Bottom nav → `Portfolio` |
| S7 | **Onboarding overlay** | Long-press the app's "replay onboarding" affordance, or fresh install |
| S8 | **Entrance / splash** | Cold-start the app — the parabola animation |

> Capture both **light mode** and **dark mode** for S1, S2, S3 — the dark theme is gorgeous and you'll want at least one dark slide (Slide 13 demo) to break up the deck.

---

## E. Two-minute script (for delivery)

A loose script that maps to slides:

> **(Slide 2)** Today's prediction markets force you into yes/no. **(Slide 3)** But the real question — "what's CPI?" — has a shape, not a side. **(Slide 4)** Parabola is the first mobile-native app on Solana that lets you trade that shape. **(Slide 6)** You post a Normal `(μ, σ)`, the program does a deterministic bounded search to verify your collateral, and you settle against the realized outcome. **(Slide 8)** Three primitives — estimation, regime index, perpetual — all use the same engine. **(Slide 10)** It's already wired to live Polymarket and Pyth data. **(Slide 13)** Demo. **(Slide 14)** v1 is Normal-only on devnet, and we're heading for a capped launch next.

---

## F. Five copy alternatives for the cover headline

If "Parabola." feels too short, try one of these in 200 pt Inter Tight Bold:

1. *Trade the curve, not the coin.*
2. *Distribution markets, in your pocket.*
3. *A price is a point. A market should be a shape.*
4. *Yes/no is over. Welcome to (μ, σ).*
5. *Solana's first distribution-market mobile app.*

---

## G. Final checklist before you present

- [ ] Brand kit colors loaded into Canva.
- [ ] Inter Tight, Inter, JetBrains Mono added to the doc.
- [ ] All 8 screenshots captured at 2x in light + dark.
- [ ] Charts C.1, C.2, C.5 drawn with exact data.
- [ ] Every number on every slide is in JetBrains Mono.
- [ ] One — and only one — Lime accent per slide.
- [ ] Dark slide (13) included for visual contrast.
- [ ] Footer page numbers in mono, bottom-right, 30 % ink.
- [ ] Export → PDF (print quality) for backup, plus Canva share link for live demo.
