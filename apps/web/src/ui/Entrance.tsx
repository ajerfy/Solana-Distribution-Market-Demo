import { useEffect, useRef, useState } from "react";

type Props = { onEnter: () => void };

const W = 320;
const H = 360;
const CENTER_Y = H * 0.48; // 172.8
const SAMPLES = 72;
// Wider than the circle so tails bleed outside (circle edge is at ~51 and ~269)
const CHART_LEFT = 10;
const CHART_RIGHT = 310;
const CHART_WIDTH = CHART_RIGHT - CHART_LEFT;
const CIRC_R = W * 0.34; // 108.8

function normalY(step: number, mean: number, sigma: number): number {
  const x = step / SAMPLES;
  const z = (x - mean) / sigma;
  const density = Math.exp(-(z * z) / 2);
  return CENTER_Y + 80 - density * 145;
}

function buildPath(visibleSamples: number, mean: number, sigma: number): string {
  if (visibleSamples < 2) return "";
  return Array.from({ length: visibleSamples }, (_, i) => {
    const px = CHART_LEFT + CHART_WIDTH * (i / SAMPLES);
    const py = normalY(i, mean, sigma);
    return `${i === 0 ? "M" : "L"}${px.toFixed(1)},${py.toFixed(1)}`;
  }).join(" ");
}

export function Entrance({ onEnter }: Props) {
  const [progress, setProgress] = useState(0);
  const startRef = useRef<number | null>(null);
  const rafRef = useRef<number>(0);

  useEffect(() => {
    const DURATION = 900;
    const tick = (ts: number) => {
      if (startRef.current == null) startRef.current = ts;
      const p = Math.min((ts - startRef.current) / DURATION, 1);
      setProgress(p);
      if (p < 1) rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, []);

  const visibleSamples = Math.floor(SAMPLES * progress);
  const crowdPath = buildPath(visibleSamples, 0.45, 0.19);
  const yourPath = buildPath(visibleSamples, 0.58, 0.17);
  const dotsVisible = progress > 0.72;

  const greenDotX = CHART_LEFT + CHART_WIDTH * 0.60;
  const greenDotY = CENTER_Y - 100;
  const redDotX = CHART_LEFT + CHART_WIDTH * 0.42;
  const redDotY = CENTER_Y - 82;

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        justifyContent: "flex-end",
        padding: "28px",
        paddingBottom: "54px",
        background: "var(--pb-bg)",
      }}
    >
      <style>{`
        @keyframes greenGlow {
          from { r: 9; opacity: 0.22; }
          to   { r: 14; opacity: 0; }
        }
        @keyframes redGlow {
          from { r: 13; opacity: 0.22; }
          to   { r: 8; opacity: 0; }
        }
      `}</style>

      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <svg width="100%" height={320} viewBox={`0 0 ${W} ${H}`} aria-hidden>
          <defs>
            <clipPath id="circClip">
              <circle cx={W / 2} cy={CENTER_Y} r={CIRC_R - 1} />
            </clipPath>
          </defs>

          {/* Circular background */}
          <circle
            cx={W / 2}
            cy={CENTER_Y}
            r={CIRC_R}
            fill="var(--pb-surface-el)"
            fillOpacity={0.92}
            stroke="var(--pb-border-strong)"
            strokeOpacity={0.80}
            strokeWidth={2.5}
          />

          {/* Grid hairlines clipped to circle */}
          <g clipPath="url(#circClip)">
            {[0, 1, 2, 3].map((idx) => {
              const gy = CENTER_Y - 96 + idx * 64;
              return (
                <line
                  key={idx}
                  x1={W / 2 - CIRC_R}
                  x2={W / 2 + CIRC_R}
                  y1={gy}
                  y2={gy}
                  stroke="var(--pb-border-strong)"
                  strokeWidth={0.8}
                  strokeOpacity={0.35}
                  strokeDasharray="3 4"
                />
              );
            })}

            {/* Glow rings clipped inside circle */}
            {dotsVisible && (
              <circle cx={greenDotX} cy={greenDotY} r={9} fill="var(--pb-long)" style={{ animation: "greenGlow 920ms ease-in-out infinite alternate" }} />
            )}
            {dotsVisible && (
              <circle cx={redDotX} cy={redDotY} r={13} fill="var(--pb-short)" style={{ animation: "redGlow 1080ms ease-in-out infinite alternate" }} />
            )}
          </g>

          {/* Curves drawn outside clip so tails bleed past circle edge */}
          {crowdPath && (
            <path
              d={crowdPath}
              fill="none"
              stroke="var(--pb-text-sec)"
              strokeWidth={3.2}
              strokeLinecap="round"
              strokeLinejoin="round"
              opacity={progress}
            />
          )}
          {yourPath && (
            <path
              d={yourPath}
              fill="none"
              stroke="var(--pb-crowd)"
              strokeWidth={4.2}
              strokeLinecap="round"
              strokeLinejoin="round"
              opacity={progress}
            />
          )}

          {/* Solid dots on top */}
          {dotsVisible && (
            <>
              <circle cx={greenDotX} cy={greenDotY} r={5} fill="var(--pb-long)" />
              <circle cx={redDotX} cy={redDotY} r={5} fill="var(--pb-short)" />
            </>
          )}
        </svg>
      </div>

      <div style={{ textAlign: "center" }}>
        <h1
          style={{
            fontSize: "36px",
            fontWeight: 800,
            marginBottom: "6px",
            letterSpacing: "-0.03em",
            background: "linear-gradient(135deg, var(--pb-you) 0%, var(--pb-crowd) 100%)",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent",
            backgroundClip: "text",
          }}
        >
          Parabola
        </h1>
        <p style={{ color: "var(--pb-text-sec)", fontSize: 15, margin: "0 0 24px", lineHeight: 1.5 }}>
          Bet on the shape, not the side.
        </p>
        <button
          type="button"
          className="pb-primary-btn"
          style={{
            background: "linear-gradient(135deg, var(--pb-you) 0%, #80e830 100%)",
            color: "var(--pb-on-accent)",
            boxShadow: "0 4px 24px rgba(182, 255, 96, 0.35)",
          }}
          onClick={onEnter}
        >
          Trade on estimation markets
        </button>
      </div>
    </div>
  );
}
