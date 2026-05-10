import type { CSSProperties, ReactNode } from "react";

/* ═══════════════════════════════════════════════════════════
   Distribution primitives  (used on list + detail pages)
═══════════════════════════════════════════════════════════ */

function clamp(v: number) { return Math.min(100, Math.max(0, v)); }

/**
 * Shows the crowd's ±1σ confidence band as a filled segment within the full
 * [muMin, muMax] range, with a bright tick at μ.
 * The visual immediately conveys WHERE the crowd thinks it lands and HOW WIDE
 * their uncertainty is — the core of a distribution market card.
 */
export function DistBar({
  mu, sigma, muMin, muMax,
  color = "var(--pb-crowd)", height = 6,
}: {
  mu: number; sigma: number; muMin: number; muMax: number;
  color?: string; height?: number;
}) {
  const span    = muMax - muMin || 1;
  const muPct   = clamp(((mu - muMin)          / span) * 100);
  const leftPct = clamp(((mu - sigma - muMin)  / span) * 100);
  const wPct    = clamp(((2 * sigma)            / span) * 100);

  return (
    <div style={{ position: "relative", height, borderRadius: 99, background: "var(--pb-surface-muted)" }}>
      <div style={{
        position: "absolute", top: 0, bottom: 0,
        left: `${leftPct}%`, width: `${wPct}%`,
        background: color, opacity: 0.35, borderRadius: 99,
      }} />
      <div style={{
        position: "absolute", top: -2, bottom: -2,
        left: `${muPct}%`, width: 3,
        transform: "translateX(-50%)",
        background: color, borderRadius: 2,
      }} />
    </div>
  );
}

/** Small bell-curve SVG for embedding on cards. */
export function MiniDistCurve({
  mu, sigma, muMin, muMax,
  color = "var(--pb-crowd)", width = 80, height = 32,
}: {
  mu: number; sigma: number; muMin: number; muMax: number;
  color?: string; width?: number; height?: number;
}) {
  const span = muMax - muMin || 1;
  const samples = 60;
  const pad = 2;
  const plotW = width - pad * 2;
  const plotH = height - pad * 2;
  const pdf = (x: number) => Math.exp(-0.5 * ((x - mu) / sigma) ** 2);
  const pts = Array.from({ length: samples }, (_, i) => {
    const x = muMin + (i / (samples - 1)) * span;
    return pdf(x);
  });
  const maxY = Math.max(...pts, 1e-9);
  const path = pts.map((v, i) => {
    const px = pad + (i / (samples - 1)) * plotW;
    const py = pad + plotH - (v / maxY) * plotH * 0.9;
    return `${i === 0 ? "M" : "L"}${px.toFixed(1)},${py.toFixed(1)}`;
  }).join(" ");

  return (
    <svg width={width} height={height} aria-hidden style={{ overflow: "visible", flexShrink: 0 }}>
      <path d={path} fill="none" stroke={color} strokeWidth={1.8} strokeLinecap="round" opacity={0.7} />
    </svg>
  );
}

/* ═══════════════════════════════════════════════════════════
   Layout / navigation
═══════════════════════════════════════════════════════════ */

export function BackBar({ onBack, unitUpper }: { onBack: () => void; unitUpper: string }) {
  return (
    <div style={{
      display: "flex", alignItems: "center", padding: "14px 16px", gap: 12,
      borderBottom: "1px solid var(--pb-border)",
    }}>
      <button
        type="button"
        onClick={onBack}
        aria-label="Back"
        style={{
          width: 34, height: 34, borderRadius: 10, flexShrink: 0,
          border: "1px solid var(--pb-border-strong)",
          background: "var(--pb-surface-el)", color: "var(--pb-text)",
          cursor: "pointer", fontSize: 16, display: "flex",
          alignItems: "center", justifyContent: "center",
        }}
      >
        ←
      </button>
      <span style={{ color: "var(--pb-text-sec)", fontSize: 14, fontWeight: 500 }}>Markets</span>
      <span style={{ flex: 1 }} />
      <span className="pb-mono" style={{
        fontSize: 10, fontWeight: 700, color: "var(--pb-text-dim)",
        letterSpacing: "0.08em", textTransform: "uppercase",
      }}>
        {unitUpper}
      </span>
    </div>
  );
}

export function DetailBackBar({ onBack, trailing }: { onBack: () => void; trailing: string }) {
  return (
    <div style={{
      display: "flex", alignItems: "center", padding: "14px 16px", gap: 12,
      borderBottom: "1px solid var(--pb-border)",
    }}>
      <button
        type="button"
        onClick={onBack}
        aria-label="Back"
        style={{
          width: 34, height: 34, borderRadius: 10, flexShrink: 0,
          border: "1px solid var(--pb-border-strong)",
          background: "var(--pb-surface-el)", color: "var(--pb-text)",
          cursor: "pointer", fontSize: 16, display: "flex",
          alignItems: "center", justifyContent: "center",
        }}
      >
        ←
      </button>
      <span style={{ color: "var(--pb-text-sec)", fontSize: 14, fontWeight: 500 }}>Markets</span>
      <span style={{ flex: 1 }} />
      <span className="pb-mono" style={{
        fontSize: 10, fontWeight: 700, color: "var(--pb-text-dim)",
        letterSpacing: "0.08em",
      }}>
        {trailing}
      </span>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   Cards & containers
═══════════════════════════════════════════════════════════ */

export function Card({
  children, className, onClick, style,
}: {
  children: ReactNode; className?: string; onClick?: () => void; style?: CSSProperties;
}) {
  return (
    <div
      className={`pb-card${onClick ? " pb-card-click" : ""}${className ? ` ${className}` : ""}`}
      style={{ boxShadow: "var(--pb-card-shadow)", ...style }}
      onClick={onClick}
      onKeyDown={onClick ? (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onClick(); } } : undefined}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      {children}
    </div>
  );
}

/** Two-value hero block: big number on left, label above. */
export function HeroMetric({
  label, value, unit, color,
}: { label: string; value: string; unit?: string; color?: string }) {
  return (
    <div>
      <div style={{ fontSize: 9, fontWeight: 700, color: "var(--pb-text-dim)", letterSpacing: "0.08em", marginBottom: 4 }}>
        {label}
      </div>
      <div className="pb-mono" style={{ fontSize: 24, fontWeight: 700, color: color ?? "var(--pb-text)", letterSpacing: "-0.01em", lineHeight: 1 }}>
        {value}
        {unit && (
          <span style={{ fontSize: 13, fontWeight: 500, color: "var(--pb-text-sec)", marginLeft: 4 }}>
            {unit}
          </span>
        )}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   Tags & badges
═══════════════════════════════════════════════════════════ */

export function TagPill({
  children, colorVar, filled,
}: { children: ReactNode; colorVar: string; filled?: boolean }) {
  return (
    <span className="pb-tag" style={{
      background: filled ? `${colorVar}28` : "transparent",
      border: `1px solid ${colorVar}${filled ? "45" : "55"}`,
      color: colorVar,
    }}>
      {children}
    </span>
  );
}

/* ═══════════════════════════════════════════════════════════
   Metrics & stats
═══════════════════════════════════════════════════════════ */

export function MetricPill({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <div className="pb-metric" style={{ flex: 1, minWidth: 0, border: "1px solid var(--pb-border)" }}>
      <div className="pb-section-label" style={{ marginBottom: 4 }}>{label}</div>
      <div className="pb-mono" style={{
        fontSize: 14, fontWeight: 600,
        color: accent ?? "var(--pb-text)",
        whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis",
      }}>
        {value}
      </div>
    </div>
  );
}

export function SectionLabel({ children }: { children: ReactNode }) {
  return <div className="pb-section-label">{children}</div>;
}

export function StatRow({
  label, value, accent, strong,
}: { label: string; value: string; accent?: string; strong?: boolean }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "baseline", marginBottom: 4 }}>
      <span style={{ color: "var(--pb-text-dim)", fontSize: 13 }}>{label}</span>
      <span className="pb-mono" style={{
        color: accent ?? "var(--pb-text)", fontWeight: strong ? 700 : 500, fontSize: 14, textAlign: "right",
      }}>
        {value}
      </span>
    </div>
  );
}

export function CompactDivider({ style }: { style?: CSSProperties }) {
  return (
    <div style={{ height: 1, background: "var(--pb-border)", margin: "10px 0", ...style }} />
  );
}

/* ═══════════════════════════════════════════════════════════
   Buttons
═══════════════════════════════════════════════════════════ */

export function PrimaryButton({
  label, onClick, disabled,
  accent = "var(--pb-you)", foreground,
}: { label: string; onClick: () => void; disabled?: boolean; accent?: string; foreground?: string }) {
  return (
    <button
      type="button"
      className="pb-primary-btn"
      style={{
        background: accent,
        color: foreground ?? "var(--pb-on-accent)",
        opacity: disabled ? 0.45 : 1,
        cursor: disabled ? "not-allowed" : "pointer",
        boxShadow: disabled ? "none" : `0 4px 16px ${accent}40`,
      }}
      disabled={disabled}
      onClick={onClick}
    >
      {label}
    </button>
  );
}

export function GhostButton({ label, onClick, disabled }: { label: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      className="pb-ghost-btn"
      disabled={disabled}
      onClick={onClick}
      style={{ opacity: disabled ? 0.45 : 1, cursor: disabled ? "not-allowed" : "pointer" }}
    >
      {label}
    </button>
  );
}

/* ═══════════════════════════════════════════════════════════
   Detail-page helpers
═══════════════════════════════════════════════════════════ */

export function PnlText({ value, prefix = "" }: { value: number; prefix?: string }) {
  const pos = value >= 0;
  return (
    <span className="pb-mono" style={{ fontSize: 18, fontWeight: 700, color: pos ? "var(--pb-long)" : "var(--pb-short)" }}>
      {pos ? "+" : ""}{prefix}{value.toFixed(2)}
    </span>
  );
}

export function StatusBlockSimple({ message, variant }: { message: string; variant: "error" | "working" | "ok" }) {
  const color = variant === "error" ? "var(--pb-short)" : variant === "working" ? "var(--pb-warn)" : "var(--pb-long)";
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 10, padding: 12,
      borderRadius: 12, background: `${color}18`, border: `1px solid ${color}35`,
    }}>
      <span style={{ width: 8, height: 8, borderRadius: 99, background: color, flexShrink: 0 }} />
      <span style={{ color, fontSize: 13 }}>{message}</span>
    </div>
  );
}
