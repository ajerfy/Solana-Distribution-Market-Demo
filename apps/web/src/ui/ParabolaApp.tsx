import { Component, useEffect } from "react";
import type { ReactNode } from "react";
import { useBackendSync } from "../hooks/useBackendSync";
import { useParabolaStore } from "../state/parabolaStore";
import { BottomNav } from "./BottomNav";
import { BetSheetHost } from "./BetSheetHost";
import { Engine } from "./Engine";
import { Entrance } from "./Entrance";
import { MarketDetail } from "./MarketDetail";
import { MarketsList } from "./MarketsList";
import { Portfolio } from "./Portfolio";
import { Wallet } from "./Wallet";

class BetSheetErrorBoundary extends Component<
  { children: ReactNode },
  { crashed: boolean }
> {
  state = { crashed: false };
  static getDerivedStateFromError() {
    return { crashed: true };
  }
  render() {
    if (this.state.crashed) {
      return (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.7)",
            zIndex: 50,
            display: "flex",
            alignItems: "flex-end",
            justifyContent: "center",
            padding: 20,
          }}
          onClick={() => this.setState({ crashed: false })}
          role="presentation"
        >
          <div
            className="pb-card"
            style={{ width: "100%", maxWidth: 520 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ fontWeight: 700, color: "var(--pb-short)" }}>
              Could not open bet sheet
            </div>
            <p style={{ color: "var(--pb-text-sec)", fontSize: 14, marginTop: 8 }}>
              Market quote data is unavailable or incomplete. Try again once the
              live backend has synced.
            </p>
            <button
              type="button"
              className="pb-ghost-btn"
              style={{ marginTop: 12 }}
              onClick={() => this.setState({ crashed: false })}
            >
              Dismiss
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function ParabolaApp() {
  const entered = useParabolaStore((s) => s.entered);
  const setEntered = useParabolaStore((s) => s.setEntered);
  const themeMode = useParabolaStore((s) => s.themeMode);
  const activeTab = useParabolaStore((s) => s.activeTab);
  const setActiveTab = useParabolaStore((s) => s.setActiveTab);
  const selectedMarketId = useParabolaStore((s) => s.selectedMarketId);

  useEffect(() => {
    document.documentElement.dataset.theme = themeMode;
  }, [themeMode]);

  useBackendSync(entered);

  if (!entered) {
    return (
      <div className="pb-root">
        <Entrance onEnter={() => setEntered(true)} />
      </div>
    );
  }

  return (
    <div className="pb-root">
      {selectedMarketId ? (
        <>
          <MarketDetail />
          <BetSheetErrorBoundary>
            <BetSheetHost />
          </BetSheetErrorBoundary>
        </>
      ) : (
        <>
          {activeTab === "markets" ? <MarketsList /> : null}
          {activeTab === "portfolio" ? <Portfolio /> : null}
          {activeTab === "engine" ? <Engine /> : null}
          {activeTab === "wallet" ? <Wallet /> : null}
          <BottomNav active={activeTab} onSelect={setActiveTab} />
        </>
      )}
    </div>
  );
}
