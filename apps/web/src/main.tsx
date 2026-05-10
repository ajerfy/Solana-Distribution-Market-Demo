import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import "./ui/theme.css";
import "./index.css";

try {
  const t = localStorage.getItem("parabola.web.theme.v1");
  document.documentElement.dataset.theme = t === "light" ? "light" : "dark";
} catch {
  document.documentElement.dataset.theme = "dark";
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
