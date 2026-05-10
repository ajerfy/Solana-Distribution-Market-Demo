import { useMemo } from "react";
import type {
  MarketCategory,
  MarketListing,
  MarketType,
} from "../domain/types";
import { CATEGORY_META } from "../domain/types";
import type { MarketTypeFilterKey } from "../state/parabolaStore";
import { compactDecimal, formatVolume, shorten } from "../domain/format";
import { buildMarketListings } from "../domain/mockMarkets";
import { Sparkline } from "./Sparkline";
import { useParabolaStore } from "../state/parabolaStore";

const ALL_CATEGORIES: MarketCategory[] = [
  "All",
  "Events",
  "Weather",
  "Crypto",
  "Sports",
  "PopCulture",
  "Climate",
  "Macro",
  "Equities",
  "Politics",
];

const TYPE_FILTERS: {
  key: MarketTypeFilterKey;
  label: string;
  glyph: string;
  match: MarketType | null;
}[] = [
  { key: "All", label: "All", glyph: "✦", match: null },
  { key: "Estimates", label: "Estimates", glyph: "◯", match: "Estimation" },
  { key: "Perps", label: "Perps", glyph: "∞", match: "Perp" },
  {
    key: "RegimeIndexes",
    label: "Regime indexes",
    glyph: "▦",
    match: "RegimeIndex",
  },
];

function tagColor(mode: string): string {
  switch (mode) {
    case "live":
      return "var(--pb-long)";
    case "connecting":
      return "var(--pb-warn)";
    case "error":
      return "var(--pb-short)";
    default:
      return "var(--pb-chain)";
  }
}

function tagLabel(mode: string): string {
  switch (mode) {
    case "live":
      return "LIVE FEEDS";
    case "connecting":
      return "CONNECTING";
    case "error":
      return "LIVE ERROR";
    default:
      return "DEMO DATA";
  }
}

export function MarketsList() {
  const payload = useParabolaStore((s) => s.payload);
  const markets = useMemo(() => (payload ? buildMarketListings(payload) : []), [payload]);
  const selectedCategory = useParabolaStore((s) => s.selectedCategory);
  const setSelectedCategory = useParabolaStore((s) => s.setSelectedCategory);
  const selectedType = useParabolaStore((s) => s.selectedMarketTypeFilter);
  const setSelectedType = useParabolaStore((s) => s.setSelectedMarketTypeFilter);
  const openMarket = useParabolaStore((s) => s.openMarket);
  const liveSync = useParabolaStore((s) => s.liveSync);
  const themeMode = useParabolaStore((s) => s.themeMode);
  const setThemeMode = useParabolaStore((s) => s.setThemeMode);

  const filtered = markets.filter((m) => {
    const catOk =
      selectedCategory === "All" || m.category === selectedCategory;
    const typeOk =
      TYPE_FILTERS.find((x) => x.key === selectedType)?.match == null ||
      m.marketType ===
        TYPE_FILTERS.find((x) => x.key === selectedType)?.match;
    return catOk && typeOk;
  });

  const featured = filtered.filter((m) => m.isFeaturedLive);

  const categoryScoped =
    selectedCategory === "All"
      ? markets
      : markets.filter((m) => m.category === selectedCategory);

  return (
    <div className="pb-scroll">
      <header style={{ padding: "28px 20px 16px" }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            flexWrap: "wrap",
            marginBottom: 14,
          }}
        >
          <span
            className="pb-mono"
            style={{
              background: "linear-gradient(135deg, var(--pb-you) 0%, var(--pb-crowd) 100%)",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent",
              backgroundClip: "text",
              fontWeight: 800,
              fontSize: 15,
              letterSpacing: "0.12em",
            }}
          >
            PARABOLA
          </span>
          <span className="pb-tag" style={{ background: `${tagColor(liveSync.mode)}20`, color: tagColor(liveSync.mode), border: `1px solid ${tagColor(liveSync.mode)}40` }}>
            {tagLabel(liveSync.mode)}
          </span>
          <span className="pb-tag" style={{ background: "var(--pb-chain)18", color: "var(--pb-chain)", border: "1px solid var(--pb-chain)33" }}>
            DEVNET
          </span>
          <button
            type="button"
            className="pb-chip"
            data-selected="false"
            style={{ marginLeft: "auto", fontSize: 12, padding: "6px 12px" }}
            onClick={() =>
              setThemeMode(themeMode === "dark" ? "light" : "dark")
            }
          >
            {themeMode === "light" ? "☀ Light" : "☾ Dark"}
          </button>
        </div>
        <h2 style={{ fontSize: 26, fontWeight: 700, margin: "0 0 8px", lineHeight: 1.25, letterSpacing: "-0.02em" }}>
          Bet on the shape,{" "}
          <span style={{ color: "var(--pb-text-sec)", fontWeight: 500 }}>not the side.</span>
        </h2>
        <p style={{ color: "var(--pb-text-sec)", fontSize: 14, margin: 0, lineHeight: 1.6 }}>
          Trade conviction and uncertainty across live events, perps, and estimation markets.
        </p>
        {liveSync.message ? (
          <p style={{ color: "var(--pb-text-dim)", fontSize: 11, marginTop: 8, fontFamily: "var(--pb-mono, monospace)" }}>
            {liveSync.message}
          </p>
        ) : null}
      </header>

      <div
        style={{
          display: "flex",
          gap: 8,
          overflowX: "auto",
          padding: "0 20px 12px",
        }}
      >
        {ALL_CATEGORIES.map((cat) => (
          <button
            key={cat}
            type="button"
            className="pb-chip"
            data-selected={selectedCategory === cat}
            onClick={() => setSelectedCategory(cat)}
          >
            {cat === "All"
              ? "✦ All"
              : `${CATEGORY_META[cat as Exclude<MarketCategory, "All">]?.emoji ?? ""} ${CATEGORY_META[cat as Exclude<MarketCategory, "All">]?.label ?? cat}`}
          </button>
        ))}
      </div>

      <div
        style={{
          display: "flex",
          gap: 8,
          overflowX: "auto",
          padding: "0 20px 16px",
        }}
      >
        {TYPE_FILTERS.map((tf) => {
          const count = categoryScoped.filter(
            (m) => tf.match == null || m.marketType === tf.match,
          ).length;
          return (
            <button
              key={tf.key}
              type="button"
              className="pb-chip"
              data-selected={selectedType === tf.key}
              onClick={() => setSelectedType(tf.key)}
            >
              {tf.glyph} {tf.label} {count}
            </button>
          );
        })}
      </div>

      {featured.length > 0 && (
        <section style={{ padding: "0 20px 16px" }}>
          <div className="pb-section-label" style={{ marginBottom: 10 }}>
            LIVE NOW
          </div>
          {featured.map((m) => (
            <LiveFacetCard key={m.id} market={m} onOpen={() => openMarket(m.id)} />
          ))}
        </section>
      )}

      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          padding: "0 20px 8px",
          fontSize: 11,
          color: "var(--pb-text-dim)",
        }}
      >
        <span className="pb-section-label">{filtered.length} markets</span>
        <span className="pb-section-label">
          {selectedType === "All"
            ? "sorted · trending"
            : `filter · ${selectedType.toLowerCase()}`}
        </span>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: "0 20px" }}>
        {filtered.length === 0 ? (
          <div className="pb-card">
            <p style={{ fontWeight: 600, margin: 0 }}>
              No {TYPE_FILTERS.find((x) => x.key === selectedType)?.label.toLowerCase() ?? "markets"} in {selectedCategory === "All" ? "all" : selectedCategory.toLowerCase()} yet.
            </p>
            <p style={{ color: "var(--pb-text-sec)", fontSize: 13, marginTop: 6 }}>
              Switch category or clear filters to see every active Parabola market.
            </p>
            <button
              type="button"
              className="pb-ghost-btn"
              style={{ marginTop: 12 }}
              onClick={() => {
                setSelectedCategory("All");
                setSelectedType("All");
              }}
            >
              Clear filters
            </button>
          </div>
        ) : (
          filtered.map((m) => (
            <MarketRow key={m.id} market={m} onClick={() => openMarket(m.id)} />
          ))
        )}
      </div>
    </div>
  );
}

function LiveFacetCard({
  market,
  onOpen,
}: {
  market: MarketListing;
  onOpen: () => void;
}) {
  const spark =
    market.marketType === "Estimation"
      ? "var(--pb-crowd)"
      : market.marketType === "RegimeIndex"
        ? "var(--pb-long)"
        : "var(--pb-warn)";
  return (
    <button
      type="button"
      className="pb-card pb-card-click"
      style={{ width: "100%", textAlign: "left", marginBottom: 10 }}
      onClick={onOpen}
    >
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <div>
          <div style={{ display: "flex", gap: 6, alignItems: "center", flexWrap: "wrap" }}>
            <span className="pb-tag" style={{ background: "var(--pb-long)", color: "#fff" }}>
              LIVE
            </span>
            {market.sourceBadge && (
              <span className="pb-tag" style={{ background: "var(--pb-chain)22", color: "var(--pb-chain)" }}>
                {market.sourceBadge}
              </span>
            )}
            <MarketTypeBadge t={market.marketType} />
          </div>
          <div style={{ fontWeight: 600, marginTop: 8, fontSize: 17 }}>{market.title}</div>
          <div style={{ color: "var(--pb-text-sec)", fontSize: 14 }}>{market.subtitle}</div>
        </div>
        <Sparkline values={market.crowdHistory} color={spark} width={88} />
      </div>
      <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
        {market.marketType === "Perp" && market.perp ? (
          <>
            <CrowdMini label="MARK" value={`$${compactDecimal(market.crowdMu, 2)}`} />
            <CrowdMini
              label="ANCHOR"
              value={`$${compactDecimal(Number.parseFloat(market.perp.anchor_mu_display) || 0, 2)}`}
            />
            <CrowdMini label="FUNDING" value={market.perp.spot_funding_rate_display.slice(0, 9)} />
            <CrowdMini label="STATUS" value={market.resolvesAt} />
          </>
        ) : (
          <>
            <CrowdMini
              label="LIVE PROB"
              value={`${compactDecimal(market.crowdMu, 1)} ${market.unit}`}
            />
            <CrowdMini
              label="BID"
              value={
                market.liveEventStats?.bestBid != null
                  ? `${compactDecimal(market.liveEventStats.bestBid * 100, 1)}¢`
                  : "—"
              }
            />
            <CrowdMini
              label="ASK"
              value={
                market.liveEventStats?.bestAsk != null
                  ? `${compactDecimal(market.liveEventStats.bestAsk * 100, 1)}¢`
                  : "—"
              }
            />
            <CrowdMini label="VOLUME" value={formatVolume(market.volumeUsd)} />
          </>
        )}
      </div>
    </button>
  );
}

function MarketTypeBadge({ t }: { t: MarketType }) {
  if (t === "Estimation") {
    return (
      <span className="pb-tag" style={{ background: "var(--pb-crowd)22", color: "var(--pb-crowd)" }}>
        ESTIMATE
      </span>
    );
  }
  if (t === "RegimeIndex") {
    return (
      <span className="pb-tag" style={{ background: "var(--pb-long)22", color: "var(--pb-long)" }}>
        REGIME
      </span>
    );
  }
  return (
    <span className="pb-tag" style={{ background: "var(--pb-warn)22", color: "var(--pb-warn)" }}>
      PERP
    </span>
  );
}

function CrowdMini({ label, value }: { label: string; value: string }) {
  return (
    <div className="pb-metric" style={{ flex: "1 1 70px", minWidth: 0 }}>
      <div className="pb-section-label">{label}</div>
      <div className="pb-mono" style={{ fontSize: 13, fontWeight: 600 }}>
        {value}
      </div>
    </div>
  );
}

function MarketRow({
  market,
  onClick,
}: {
  market: MarketListing;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className="pb-card pb-card-click"
      style={{ width: "100%", textAlign: "left" }}
      onClick={onClick}
    >
      <div style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: 14,
            background: "linear-gradient(135deg, var(--pb-surface-muted) 0%, var(--pb-surface-el) 100%)",
            border: "1px solid var(--pb-border-strong)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 20,
            flexShrink: 0,
          }}
        >
          {CATEGORY_META[market.category]?.emoji ?? "✦"}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
            {market.isOnChain && (
              <span className="pb-tag" style={{ background: "var(--pb-chain)", color: "#fff" }}>
                ON-CHAIN
              </span>
            )}
            {market.sourceBadge && (
              <span className="pb-tag" style={{ background: "var(--pb-chain)22", color: "var(--pb-chain)" }}>
                {market.sourceBadge}
              </span>
            )}
            <MarketTypeBadge t={market.marketType} />
            <span style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>
              {market.category.toUpperCase()}
            </span>
          </div>
          <div style={{ fontWeight: 600, marginTop: 4 }}>{market.title}</div>
          <div style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>
            {market.subtitle}
          </div>
        </div>
        <Sparkline values={market.crowdHistory} color="var(--pb-crowd)" width={72} />
      </div>
      <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
        <CrowdMini
          label="CROWD GUESS"
          value={`${compactDecimal(market.crowdMu, 2)} ${market.unit}`}
        />
        <CrowdMini label="± RANGE" value={compactDecimal(market.crowdSigma, 2)} />
        <CrowdMini label="VOLUME" value={formatVolume(market.volumeUsd)} />
        <CrowdMini label="RESOLVES" value={shorten(market.resolvesAt)} />
      </div>
    </button>
  );
}
