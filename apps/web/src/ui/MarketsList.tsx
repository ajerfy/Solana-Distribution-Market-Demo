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
import { DistBar, MiniDistCurve } from "./shared";

const ALL_CATEGORIES: MarketCategory[] = [
  "All", "Events", "Weather", "Crypto", "Sports",
  "PopCulture", "Climate", "Macro", "Equities", "Politics",
];

const TYPE_FILTERS: { key: MarketTypeFilterKey; label: string; match: MarketType | null }[] = [
  { key: "All",          label: "All",       match: null          },
  { key: "Estimates",    label: "Estimates", match: "Estimation"  },
  { key: "Perps",        label: "Perps",     match: "Perp"        },
  { key: "RegimeIndexes",label: "Regime",    match: "RegimeIndex" },
];

function liveColor(mode: string) {
  if (mode === "live")       return "var(--pb-long)";
  if (mode === "connecting") return "var(--pb-warn)";
  if (mode === "error")      return "var(--pb-short)";
  return "var(--pb-chain)";
}
function liveLabel(mode: string) {
  if (mode === "live")       return "LIVE";
  if (mode === "connecting") return "CONNECTING";
  if (mode === "error")      return "ERROR";
  return "DEMO";
}

/* ── Market type badge ────────────────────────────────────── */
function TypeBadge({ t }: { t: MarketType }) {
  const cfg =
    t === "Estimation"  ? { label: "ESTIMATE", color: "var(--pb-crowd)" } :
    t === "RegimeIndex" ? { label: "REGIME",   color: "var(--pb-long)"  } :
                          { label: "PERP",     color: "var(--pb-warn)"  };
  return (
    <span style={{
      display: "inline-block", padding: "2px 7px", borderRadius: 6,
      fontSize: 9, fontWeight: 700, letterSpacing: "0.06em",
      fontFamily: "var(--pb-mono, monospace)",
      background: `${cfg.color}18`, border: `1px solid ${cfg.color}35`,
      color: cfg.color,
    }}>
      {cfg.label}
    </span>
  );
}

/* ═══════════════════════════════════════════════════════════
   MarketsList
═══════════════════════════════════════════════════════════ */

export function MarketsList() {
  const payload      = useParabolaStore((s) => s.payload);
  const markets      = useMemo(() => (payload ? buildMarketListings(payload) : []), [payload]);
  const selCat       = useParabolaStore((s) => s.selectedCategory);
  const setSelCat    = useParabolaStore((s) => s.setSelectedCategory);
  const selType      = useParabolaStore((s) => s.selectedMarketTypeFilter);
  const setSelType   = useParabolaStore((s) => s.setSelectedMarketTypeFilter);
  const openMarket   = useParabolaStore((s) => s.openMarket);
  const liveSync     = useParabolaStore((s) => s.liveSync);
  const themeMode    = useParabolaStore((s) => s.themeMode);
  const setThemeMode = useParabolaStore((s) => s.setThemeMode);

  const filtered = markets.filter((m) => {
    const catOk  = selCat === "All" || m.category === selCat;
    const match  = TYPE_FILTERS.find((x) => x.key === selType)?.match ?? null;
    const typeOk = match == null || m.marketType === match;
    return catOk && typeOk;
  });

  const featured = filtered.filter((m) => m.isFeaturedLive);
  const rest     = filtered.filter((m) => !m.isFeaturedLive);

  const dotColor = liveColor(liveSync.mode);

  return (
    <div className="pb-scroll">

      {/* ── Header ── */}
      <header style={{ padding: "24px 20px 0" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 18 }}>
          {/* Wordmark */}
          <span className="pb-mono" style={{
            background: "linear-gradient(135deg, var(--pb-you) 0%, var(--pb-crowd) 100%)",
            WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
            backgroundClip: "text", fontWeight: 800, fontSize: 15, letterSpacing: "0.1em",
          }}>
            PARABOLA
          </span>

          {/* Live status dot */}
          <span style={{
            display: "inline-flex", alignItems: "center", gap: 5,
            padding: "3px 9px", borderRadius: 99,
            background: `${dotColor}14`, border: `1px solid ${dotColor}38`,
            fontSize: 10, fontWeight: 700, color: dotColor,
            fontFamily: "var(--pb-mono, monospace)", letterSpacing: "0.06em",
          }}>
            <span style={{
              width: 6, height: 6, borderRadius: "50%",
              background: dotColor, boxShadow: `0 0 6px ${dotColor}`,
            }} />
            {liveLabel(liveSync.mode)}
          </span>

          <span style={{
            padding: "3px 9px", borderRadius: 99,
            background: "var(--pb-chain)12", border: "1px solid var(--pb-chain)2e",
            fontSize: 10, fontWeight: 700, color: "var(--pb-chain)",
            fontFamily: "var(--pb-mono, monospace)", letterSpacing: "0.06em",
          }}>
            DEVNET
          </span>

          <button
            type="button"
            style={{
              marginLeft: "auto", border: "1px solid var(--pb-border-strong)",
              background: "var(--pb-surface-el)", borderRadius: 99,
              padding: "5px 12px", color: "var(--pb-text-sec)", fontSize: 12, fontWeight: 500,
            }}
            onClick={() => setThemeMode(themeMode === "dark" ? "light" : "dark")}
          >
            {themeMode === "light" ? "☀" : "☾"}
          </button>
        </div>

        <h2 style={{ fontSize: 22, fontWeight: 700, margin: "0 0 5px", letterSpacing: "-0.02em" }}>
          Markets
        </h2>
        <p style={{ color: "var(--pb-text-dim)", fontSize: 13, margin: "0 0 20px" }}>
          {filtered.length} active · bet the distribution, not just the direction
        </p>
      </header>

      {/* ── Category pills ── */}
      <div style={{ display: "flex", gap: 6, overflowX: "auto", padding: "0 20px 14px", scrollbarWidth: "none" }}>
        {ALL_CATEGORIES.map((cat) => {
          const active = selCat === cat;
          const meta = cat !== "All" ? CATEGORY_META[cat as Exclude<MarketCategory, "All">] : null;
          return (
            <button
              key={cat}
              type="button"
              className="pb-filter-pill"
              onClick={() => setSelCat(cat)}
              style={{
                padding: "7px 14px", borderRadius: 99, fontSize: 13, fontWeight: active ? 700 : 500,
                background: active ? "var(--pb-text)" : "transparent",
                color: active ? "var(--pb-bg)" : "var(--pb-text-sec)",
                border: active ? "none" : "1px solid var(--pb-border-strong)",
              }}
            >
              {meta ? `${meta.emoji} ${meta.label}` : "✦ All"}
            </button>
          );
        })}
      </div>

      {/* ── Type tabs ── */}
      <div style={{
        display: "flex", gap: 0, padding: "0 20px 20px",
        borderBottom: "1px solid var(--pb-border)",
      }}>
        {TYPE_FILTERS.map((tf) => {
          const active = selType === tf.key;
          const count  = markets.filter((m) => {
            const catOk = selCat === "All" || m.category === selCat;
            return catOk && (tf.match == null || m.marketType === tf.match);
          }).length;
          return (
            <button
              key={tf.key}
              type="button"
              className="pb-tab-btn"
              onClick={() => setSelType(tf.key)}
              style={{
                flex: 1, padding: "8px 4px", position: "relative",
                color: active ? "var(--pb-text)" : "var(--pb-text-dim)",
                fontWeight: active ? 700 : 400, fontSize: 13,
              }}
            >
              {tf.label}
              <span style={{ marginLeft: 5, fontSize: 11, color: active ? "var(--pb-text-sec)" : "var(--pb-text-dim)" }}>
                {count}
              </span>
              {active && (
                <div style={{
                  position: "absolute", bottom: 0, left: "10%", right: "10%",
                  height: 2, borderRadius: 2, background: "var(--pb-you)",
                }} />
              )}
            </button>
          );
        })}
      </div>

      {/* ── Featured / live ── */}
      {featured.length > 0 && (
        <section style={{ padding: "20px 20px 0" }}>
          <div style={{
            fontSize: 10, fontWeight: 700, letterSpacing: "0.08em",
            color: "var(--pb-text-dim)", textTransform: "uppercase", marginBottom: 12,
          }}>
            Live now
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {featured.map((m) => (
              <LiveCard key={m.id} market={m} onOpen={() => openMarket(m.id)} />
            ))}
          </div>
        </section>
      )}

      {/* ── Grid ── */}
      {rest.length === 0 && featured.length === 0 ? (
        <div style={{ padding: "48px 20px", textAlign: "center" }}>
          <p style={{ color: "var(--pb-text-sec)", marginBottom: 14 }}>No markets match these filters.</p>
          <button
            type="button"
            style={{
              border: "1px solid var(--pb-border-strong)", background: "var(--pb-surface-el)",
              borderRadius: 12, padding: "10px 20px", color: "var(--pb-text)",
              fontSize: 14, fontFamily: "inherit",
            }}
            onClick={() => { setSelCat("All"); setSelType("All"); }}
          >
            Clear filters
          </button>
        </div>
      ) : (
        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
          gap: 12, padding: "20px 20px 0",
        }}>
          {rest.map((m) => (
            <MarketCard key={m.id} market={m} onClick={() => openMarket(m.id)} />
          ))}
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   LiveCard — full-width featured card
   Shows the distribution as the PRIMARY concept,
   with any binary reference (Polymarket) clearly secondary.
═══════════════════════════════════════════════════════════ */
function LiveCard({ market, onOpen }: { market: MarketListing; onOpen: () => void }) {
  const stats = market.liveEventStats;

  /* For event markets tied to a binary source, crowdMu is in [0,100] (probability %)
     σ represents crowd uncertainty on that probability estimate.             */
  const isPerp   = market.marketType === "Perp";
  const barColor = isPerp ? "var(--pb-warn)" : "var(--pb-crowd)";

  return (
    <button
      type="button"
      className="pb-live-card"
      onClick={onOpen}
      style={{
        width: "100%", textAlign: "left",
        background: "var(--pb-surface)", border: "1px solid var(--pb-border-strong)",
        borderRadius: 18, padding: "18px 18px 16px",
        boxShadow: "var(--pb-card-shadow)", position: "relative", overflow: "hidden",
      }}
    >
      {/* Accent stripe */}
      <div style={{
        position: "absolute", top: 0, left: 0, right: 0, height: 3,
        background: "linear-gradient(90deg, var(--pb-long), var(--pb-crowd))",
        borderRadius: "18px 18px 0 0",
      }} />

      <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          {/* Badges */}
          <div style={{ display: "flex", gap: 6, alignItems: "center", marginBottom: 10, flexWrap: "wrap" }}>
            <span style={{
              display: "inline-flex", alignItems: "center", gap: 5,
              background: "var(--pb-long)18", border: "1px solid var(--pb-long)40",
              borderRadius: 99, padding: "3px 9px",
              fontSize: 10, fontWeight: 700, color: "var(--pb-long)",
              fontFamily: "var(--pb-mono, monospace)",
            }}>
              <span style={{ width: 5, height: 5, borderRadius: "50%", background: "var(--pb-long)", boxShadow: "0 0 5px var(--pb-long)" }} />
              LIVE
            </span>
            {market.sourceBadge && (
              <span style={{
                background: "var(--pb-chain)14", border: "1px solid var(--pb-chain)30",
                borderRadius: 99, padding: "3px 9px",
                fontSize: 10, fontWeight: 700, color: "var(--pb-chain)",
                fontFamily: "var(--pb-mono, monospace)",
              }}>
                {market.sourceBadge}
              </span>
            )}
            <TypeBadge t={market.marketType} />
          </div>

          <div style={{ fontWeight: 700, fontSize: 16, lineHeight: 1.35, marginBottom: 4 }}>
            {market.title}
          </div>
          <div style={{ color: "var(--pb-text-sec)", fontSize: 13 }}>{market.subtitle}</div>
        </div>

        <Sparkline values={market.crowdHistory} color={barColor} width={80} height={44} />
      </div>

      {/* ── Distribution display ── */}
      <div style={{ marginTop: 16 }}>
        {/* μ and σ as the primary numbers */}
        <div style={{ display: "flex", gap: 16, marginBottom: 12 }}>
          <div>
            <div style={{ fontSize: 10, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.07em", marginBottom: 3 }}>
              CROWD MEAN μ
            </div>
            <div className="pb-mono" style={{ fontSize: 22, fontWeight: 700, color: "var(--pb-crowd)" }}>
              {compactDecimal(market.crowdMu, 2)}
              <span style={{ fontSize: 13, fontWeight: 500, color: "var(--pb-text-sec)", marginLeft: 4 }}>
                {market.unit}
              </span>
            </div>
          </div>
          <div>
            <div style={{ fontSize: 10, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.07em", marginBottom: 3 }}>
              UNCERTAINTY σ
            </div>
            <div className="pb-mono" style={{ fontSize: 22, fontWeight: 700, color: "var(--pb-text-sec)" }}>
              ±{compactDecimal(market.crowdSigma, 2)}
              <span style={{ fontSize: 13, fontWeight: 500, color: "var(--pb-text-dim)", marginLeft: 4 }}>
                {market.unit}
              </span>
            </div>
          </div>
          <div style={{ flex: 1 }} />
          {/* Mini curve */}
          <MiniDistCurve
            mu={market.crowdMu} sigma={market.crowdSigma}
            muMin={market.muMin} muMax={market.muMax}
            color={barColor} width={80} height={40}
          />
        </div>

        {/* Distribution bar */}
        <DistBar
          mu={market.crowdMu} sigma={market.crowdSigma}
          muMin={market.muMin} muMax={market.muMax}
          color={barColor}
        />

        {/* Range labels */}
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 5 }}>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>
            {compactDecimal(market.muMin, 2)} {market.unit}
          </span>
          <span className="pb-mono" style={{ fontSize: 10, color: "var(--pb-text-dim)" }}>
            {compactDecimal(market.muMax, 2)} {market.unit}
          </span>
        </div>

        {/* Binary ref if available — secondary, clearly labelled */}
        {stats?.yesPrice != null && (
          <div style={{
            marginTop: 12, padding: "8px 12px", borderRadius: 10,
            background: "var(--pb-surface-el)", border: "1px solid var(--pb-border)",
            display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap",
          }}>
            <span style={{ fontSize: 10, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.06em" }}>
              {market.sourceBadge ?? "SOURCE"} REF
            </span>
            <span className="pb-mono" style={{ fontSize: 12, color: "var(--pb-long)" }}>
              YES {(stats.yesPrice * 100).toFixed(0)}¢
            </span>
            {stats.noPrice != null && (
              <span className="pb-mono" style={{ fontSize: 12, color: "var(--pb-short)" }}>
                NO {(stats.noPrice * 100).toFixed(0)}¢
              </span>
            )}
            {stats.bestBid != null && stats.bestAsk != null && (
              <span className="pb-mono" style={{ fontSize: 11, color: "var(--pb-text-dim)" }}>
                bid {(stats.bestBid * 100).toFixed(0)}¢ · ask {(stats.bestAsk * 100).toFixed(0)}¢
              </span>
            )}
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 11, color: "var(--pb-text-dim)" }}>anchor only · you bet the distribution</span>
          </div>
        )}

        {/* Footer */}
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 10 }}>
          <span style={{ fontSize: 11, color: "var(--pb-text-dim)" }}>
            ${formatVolume(market.volumeUsd)} vol · {market.bettorCount} bettors
          </span>
          <span style={{ fontSize: 11, color: "var(--pb-text-dim)" }}>
            {shorten(market.resolvesAt)}
          </span>
        </div>
      </div>
    </button>
  );
}

/* ═══════════════════════════════════════════════════════════
   MarketCard — grid card
   Distribution market card: μ is the value, σ is confidence.
   You bet BOTH, not just a direction.
═══════════════════════════════════════════════════════════ */
function MarketCard({ market, onClick }: { market: MarketListing; onClick: () => void }) {
  const catMeta = CATEGORY_META[market.category];
  const isPerp   = market.marketType === "Perp";
  const isRegime = market.marketType === "RegimeIndex";
  const barColor = isPerp ? "var(--pb-warn)" : isRegime ? "var(--pb-long)" : "var(--pb-crowd)";

  /* Label + unit for the hero number */
  const muLabel    = isPerp ? "MARK PRICE" : isRegime ? "INDEX LEVEL" : "CROWD MEAN μ";
  const sigmaLabel = isPerp ? "PRICE SPREAD σ" : isRegime ? "INDEX SPREAD σ" : "UNCERTAINTY σ";
  const muVal      = compactDecimal(market.crowdMu, 2);
  const sigmaVal   = compactDecimal(market.crowdSigma, 2);

  return (
    <button
      type="button"
      className="pb-market-card"
      onClick={onClick}
      style={{
        textAlign: "left",
        background: "var(--pb-surface)", border: "1px solid var(--pb-border)",
        borderRadius: 16, padding: "16px",
        display: "flex", flexDirection: "column", gap: 12,
        boxShadow: "var(--pb-card-shadow)",
      }}
    >
      {/* Card header */}
      <div style={{ display: "flex", alignItems: "flex-start", gap: 10 }}>
        <div style={{
          width: 40, height: 40, borderRadius: 12, flexShrink: 0,
          background: "var(--pb-surface-muted)", border: "1px solid var(--pb-border-strong)",
          display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18,
        }}>
          {catMeta?.emoji ?? "✦"}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", gap: 5, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontSize: 11, color: "var(--pb-text-dim)", fontWeight: 500 }}>
              {catMeta?.label ?? market.category}
            </span>
            <TypeBadge t={market.marketType} />
          </div>
          <div style={{
            fontWeight: 600, fontSize: 14, lineHeight: 1.35, marginTop: 3,
            overflow: "hidden", display: "-webkit-box",
            WebkitLineClamp: 2, WebkitBoxOrient: "vertical",
          }}>
            {market.title}
          </div>
        </div>
      </div>

      {/* ── Distribution metrics ── */}
      <div>
        <div style={{ display: "flex", gap: 10, marginBottom: 10 }}>
          {/* μ */}
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.07em", marginBottom: 3 }}>
              {muLabel}
            </div>
            <div className="pb-mono" style={{ fontSize: 20, fontWeight: 700, color: "var(--pb-text)", letterSpacing: "-0.01em" }}>
              {muVal}
              <span style={{ fontSize: 11, fontWeight: 500, color: "var(--pb-text-sec)", marginLeft: 3 }}>
                {market.unit}
              </span>
            </div>
          </div>
          {/* σ */}
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.07em", marginBottom: 3 }}>
              {sigmaLabel}
            </div>
            <div className="pb-mono" style={{ fontSize: 20, fontWeight: 700, color: barColor, letterSpacing: "-0.01em", opacity: 0.8 }}>
              ±{sigmaVal}
              <span style={{ fontSize: 11, fontWeight: 500, color: "var(--pb-text-dim)", marginLeft: 3 }}>
                {market.unit}
              </span>
            </div>
          </div>
          {/* Mini curve */}
          <MiniDistCurve
            mu={market.crowdMu} sigma={market.crowdSigma}
            muMin={market.muMin} muMax={market.muMax}
            color={barColor} width={52} height={36}
          />
        </div>

        {/* Extra context for perps */}
        {isPerp && market.perp && (
          <div style={{ fontSize: 11, color: "var(--pb-text-dim)", marginBottom: 8 }}>
            funding {market.perp.spot_funding_rate_display.slice(0, 9)} · {market.perp.open_positions} open pos
          </div>
        )}

        {/* Distribution bar: ±1σ band + μ tick */}
        <DistBar
          mu={market.crowdMu} sigma={market.crowdSigma}
          muMin={market.muMin} muMax={market.muMax}
          color={barColor}
        />
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 4 }}>
          <span className="pb-mono" style={{ fontSize: 9, color: "var(--pb-text-dim)" }}>
            {compactDecimal(market.muMin, 1)}
          </span>
          <span className="pb-mono" style={{ fontSize: 9, color: barColor, opacity: 0.7 }}>
            ±1σ range
          </span>
          <span className="pb-mono" style={{ fontSize: 9, color: "var(--pb-text-dim)" }}>
            {compactDecimal(market.muMax, 1)}
          </span>
        </div>
      </div>

      {/* Card footer */}
      <div style={{
        display: "flex", alignItems: "center", gap: 8,
        paddingTop: 8, borderTop: "1px solid var(--pb-border)",
      }}>
        <Sparkline values={market.crowdHistory} color="var(--pb-text-dim)" width={44} height={20} />
        <span style={{ flex: 1 }} />
        <span style={{ fontSize: 11, color: "var(--pb-text-dim)" }}>
          ${formatVolume(market.volumeUsd)}
        </span>
        <span style={{ fontSize: 11, color: "var(--pb-border-strong)" }}>·</span>
        <span style={{ fontSize: 11, color: "var(--pb-text-dim)" }}>
          {shorten(market.resolvesAt)}
        </span>
      </div>
    </button>
  );
}
