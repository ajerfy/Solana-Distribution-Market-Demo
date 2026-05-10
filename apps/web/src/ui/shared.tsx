import type { CSSProperties, ReactNode } from "react";

export function Card({
  children,
  className,
  onClick,
  style,
}: {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
  style?: CSSProperties;
}) {
  return (
    <div
      className={`pb-card${onClick ? " pb-card-click" : ""}${className ? ` ${className}` : ""}`}
      style={style}
      onClick={onClick}
      onKeyDown={
        onClick
          ? (e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onClick();
              }
            }
          : undefined
      }
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      {children}
    </div>
  );
}

export function TagPill({
  children,
  colorVar,
  filled,
}: {
  children: ReactNode;
  colorVar: string;
  filled?: boolean;
}) {
  return (
    <span
      className="pb-tag"
      style={{
        background: filled ? `${colorVar}33` : "transparent",
        border: filled ? "none" : `1px solid ${colorVar}55`,
        color: colorVar,
      }}
    >
      {children}
    </span>
  );
}

export function MetricPill({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent?: string;
}) {
  return (
    <div className="pb-metric" style={{ flex: 1, minWidth: 0 }}>
      <div className="pb-section-label" style={{ marginBottom: 4 }}>
        {label}
      </div>
      <div
        className="pb-mono"
        style={{
          fontSize: 14,
          fontWeight: 600,
          color: accent ?? "var(--pb-text)",
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {value}
      </div>
    </div>
  );
}

export function SectionLabel({ children }: { children: ReactNode }) {
  return <div className="pb-section-label">{children}</div>;
}

export function StatRow({
  label,
  value,
  accent,
  strong,
}: {
  label: string;
  value: string;
  accent?: string;
  strong?: boolean;
}) {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "space-between",
        gap: 12,
        alignItems: "baseline",
        marginBottom: 4,
      }}
    >
      <span style={{ color: "var(--pb-text-dim)", fontSize: 13 }}>{label}</span>
      <span
        className="pb-mono"
        style={{
          color: accent ?? "var(--pb-text)",
          fontWeight: strong ? 700 : 500,
          fontSize: 14,
          textAlign: "right",
        }}
      >
        {value}
      </span>
    </div>
  );
}

export function CompactDivider({ style }: { style?: React.CSSProperties }) {
  return (
    <div
      style={{
        height: 1,
        background: "var(--pb-border)",
        margin: "10px 0",
        ...style,
      }}
    />
  );
}

export function PrimaryButton({
  label,
  onClick,
  disabled,
  accent = "var(--pb-you)",
  foreground,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  accent?: string;
  foreground?: string;
}) {
  return (
    <button
      type="button"
      className="pb-primary-btn"
      style={{
        background: accent,
        color: foreground ?? "var(--pb-on-accent)",
        opacity: disabled ? 0.45 : 1,
        cursor: disabled ? "not-allowed" : "pointer",
      }}
      disabled={disabled}
      onClick={onClick}
    >
      {label}
    </button>
  );
}

export function GhostButton({
  label,
  onClick,
  disabled,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
}) {
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

export function DetailBackBar({
  onBack,
  trailing,
}: {
  onBack: () => void;
  trailing: string;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        padding: "14px 12px",
        gap: 10,
      }}
    >
      <button
        type="button"
        onClick={onBack}
        className="pb-mono"
        style={{
          width: 36,
          height: 36,
          borderRadius: 10,
          border: "none",
          background: "var(--pb-surface-el)",
          color: "var(--pb-text)",
          cursor: "pointer",
          fontSize: 18,
        }}
        aria-label="Back"
      >
        ←
      </button>
      <span style={{ color: "var(--pb-text-sec)", fontSize: 15 }}>Markets</span>
      <span style={{ flex: 1 }} />
      <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
        {trailing}
      </span>
    </div>
  );
}

export function BackBar({
  onBack,
  unitUpper,
}: {
  onBack: () => void;
  unitUpper: string;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        padding: "14px 12px",
        gap: 10,
      }}
    >
      <button
        type="button"
        onClick={onBack}
        className="pb-mono"
        style={{
          width: 36,
          height: 36,
          borderRadius: 10,
          border: "none",
          background: "var(--pb-surface-el)",
          color: "var(--pb-text)",
          cursor: "pointer",
          fontSize: 18,
        }}
        aria-label="Back"
      >
        ←
      </button>
      <span style={{ color: "var(--pb-text-sec)", fontSize: 15 }}>Markets</span>
      <span style={{ flex: 1 }} />
      <span className="pb-mono" style={{ color: "var(--pb-text-dim)", fontSize: 11 }}>
        {unitUpper}
      </span>
    </div>
  );
}

export function PnlText({ value, prefix = "" }: { value: number; prefix?: string }) {
  const pos = value >= 0;
  return (
    <span
      className="pb-mono"
      style={{
        fontSize: 18,
        fontWeight: 700,
        color: pos ? "var(--pb-long)" : "var(--pb-short)",
      }}
    >
      {pos ? "+" : ""}
      {prefix}
      {value.toFixed(2)}
    </span>
  );
}

export function StatusBlockSimple({
  message,
  variant,
}: {
  message: string;
  variant: "error" | "working" | "ok";
}) {
  const color =
    variant === "error"
      ? "var(--pb-short)"
      : variant === "working"
        ? "var(--pb-warn)"
        : "var(--pb-long)";
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: 12,
        borderRadius: 10,
        background: `${color}22`,
      }}
    >
      <span
        style={{
          width: 8,
          height: 8,
          borderRadius: 99,
          background: color,
          flexShrink: 0,
        }}
      />
      <span style={{ color, fontSize: 13 }}>{message}</span>
    </div>
  );
}
