import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

/** Dev-only: browser talks to Vite (:5173); these paths forward to live-perp-backend (avoids CORS). */
const LIVE_BACKEND = "http://127.0.0.1:8787";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: LIVE_BACKEND, changeOrigin: true },
      "/healthz": { target: LIVE_BACKEND, changeOrigin: true },
    },
  },
});
