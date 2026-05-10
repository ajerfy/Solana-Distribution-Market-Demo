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

Edit `.env` if the API is not on `http://127.0.0.1:8787`.

## Run

Terminal 1 — from repo root:

```bash
cargo run -p live-perp-backend
```

Terminal 2:

```bash
cd apps/web
npm run dev
```

Open **http://localhost:5173**. The backend allows this origin via `PARABOLA_CORS_ORIGINS` (defaults include `http://localhost:5173`).

## Production API URL

Set `VITE_PARABOLA_API_BASE` to your deployed backend (`https://…`) before `npm run build`. Add your web origin to the backend’s `PARABOLA_CORS_ORIGINS`.
