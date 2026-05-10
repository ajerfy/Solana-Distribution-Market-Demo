# Parabola Web

Read-only scaffold that loads the same JSON as the Android app from **`live-perp-backend`**.

## Prerequisites

- Node.js 20+ recommended
- Rust backend running (see repo root README)

## Setup

```bash
cd apps/web
npm install
cp .env.example .env
```

During **`npm run dev`**, leave **`VITE_PARABOLA_API_BASE` unset** so requests go to the same origin and **Vite proxies** `/api` and `/healthz` to `http://127.0.0.1:8787` (no CORS friction). Only set `VITE_PARABOLA_API_BASE` if you want the browser to call a different host directly.

## Run

Terminal 1 — from repo root (the backend must be running or the UI shows “Failed to fetch”):

```bash
cargo run -p live-perp-backend
```

Terminal 2:

```bash
cd apps/web
npm run dev
```

Open **http://localhost:5173**.

For **`npm run build`** / production hosting, set **`VITE_PARABOLA_API_BASE`** to your deployed API and add your web origin to the backend’s **`PARABOLA_CORS_ORIGINS`**.

## Production API URL

Set `VITE_PARABOLA_API_BASE` to your deployed backend (`https://…`) before `npm run build`. Add your web origin to the backend’s `PARABOLA_CORS_ORIGINS`.
