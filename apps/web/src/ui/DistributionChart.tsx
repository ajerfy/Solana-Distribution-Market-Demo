import { visualDistributionPdf } from "../domain/chartMath";

type Props = {
  crowdMu: number;
  crowdSigma: number;
  yourMu: number | null;
  yourSigma: number | null;
  realizedOutcome?: number | null;
  height?: number;
};

export function DistributionChart({
  crowdMu,
  crowdSigma,
  yourMu,
  yourSigma,
  realizedOutcome,
  height = 240,
}: Props) {
  const muMin = Math.min(crowdMu, yourMu ?? crowdMu);
  const muMax = Math.max(crowdMu, yourMu ?? crowdMu);
  const widestSigma = Math.max(crowdSigma, yourSigma ?? crowdSigma);
  const xLow = muMin - widestSigma * 4;
  const xHigh = muMax + widestSigma * 4;
  const samples = 128;
  const crowdPts: number[] = [];
  const yourPts: number[] = [];
  let maxY = 0;
  for (let i = 0; i < samples; i++) {
    const t = i / (samples - 1);
    const x = xLow + (xHigh - xLow) * t;
    const c = visualDistributionPdf(x, crowdMu, crowdSigma, 0);
    crowdPts.push(c);
    maxY = Math.max(maxY, c);
    if (yourMu != null && yourSigma != null) {
      const y = visualDistributionPdf(x, yourMu, yourSigma, 0);
      yourPts.push(y);
      maxY = Math.max(maxY, y);
    }
  }

  const W = 320;
  const padX = 14;
  const padTop = 14;
  const padBottom = 22;
  const plotW = W - padX * 2;
  const plotH = height - padTop - padBottom;
  const yScale = (plotH * 0.92) / maxY;
  const stepW = plotW / (samples - 1);

  const screenY = (v: number) => padTop + plotH - v * yScale;

  const pathLine = (vals: number[]) =>
    vals
      .map((v, i) => {
        const px = padX + plotW * (i / (samples - 1));
        const py = screenY(v);
        return `${i === 0 ? "M" : "L"}${px.toFixed(2)},${py.toFixed(2)}`;
      })
      .join(" ");

  const crowdLinePath = pathLine(crowdPts);
  const crowdFillPath = `${crowdLinePath} L ${(padX + plotW).toFixed(2)},${(padTop + plotH).toFixed(2)} L ${padX},${(padTop + plotH).toFixed(2)} Z`;

  const xToPx = (x: number) => padX + plotW * ((x - xLow) / (xHigh - xLow));
  const crowdX = xToPx(crowdMu);
  const yourX = yourMu != null ? xToPx(yourMu) : null;
  const rx = realizedOutcome != null ? xToPx(realizedOutcome) : null;

  const hasYour = yourPts.length === samples;

  return (
    <svg
      width="100%"
      height={height}
      viewBox={`0 0 ${W} ${height}`}
      preserveAspectRatio="none"
      style={{ borderRadius: 20, background: "var(--pb-surface-el)" }}
    >
      {/* Horizontal grid hairlines */}
      {[1, 2, 3, 4].map((idx) => {
        const gy = padTop + plotH * (idx / 5);
        return (
          <line
            key={idx}
            x1={padX}
            x2={padX + plotW}
            y1={gy}
            y2={gy}
            stroke="var(--pb-border-strong)"
            strokeWidth={0.75}
            strokeDasharray="2 4"
            opacity={0.45}
          />
        );
      })}

      {/* Crowd fill (light) */}
      <path d={crowdFillPath} fill="var(--pb-crowd)" opacity={0.08} />

      {/* Profit / loss zone shading between curves */}
      {hasYour &&
        crowdPts.map((cVal, i) => {
          const yVal = yourPts[i];
          const crowdScreenY = screenY(cVal);
          const yourScreenY = screenY(yVal);
          if (Math.abs(crowdScreenY - yourScreenY) < 0.5) return null;
          const isProfitZone = yourScreenY < crowdScreenY; // your curve is higher = more density = profit
          const x = padX + plotW * (i / (samples - 1)) - stepW / 2;
          const top = Math.min(crowdScreenY, yourScreenY);
          const h = Math.abs(crowdScreenY - yourScreenY);
          return (
            <rect
              key={i}
              x={x}
              y={top}
              width={stepW + 0.5}
              height={h}
              fill={isProfitZone ? "var(--pb-long)" : "var(--pb-short)"}
              opacity={0.14}
            />
          );
        })}

      {/* Crowd curve */}
      <path
        d={crowdLinePath}
        fill="none"
        stroke="var(--pb-crowd)"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Your curve */}
      {hasYour && (
        <path
          d={pathLine(yourPts)}
          fill="none"
          stroke="var(--pb-you)"
          strokeWidth={3}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      )}

      {/* Crowd mean dashed line */}
      <line
        x1={crowdX}
        x2={crowdX}
        y1={padTop}
        y2={padTop + plotH}
        stroke="var(--pb-crowd)"
        strokeWidth={1}
        strokeDasharray="3 3"
        opacity={0.55}
      />

      {/* Your mean line + dot */}
      {yourX != null && (
        <>
          <line
            x1={yourX}
            x2={yourX}
            y1={padTop}
            y2={padTop + plotH}
            stroke="var(--pb-you)"
            strokeWidth={1.4}
          />
          <circle cx={yourX} cy={padTop + plotH} r={5} fill="var(--pb-you)" />
        </>
      )}

      {/* Realized outcome marker */}
      {rx != null && (
        <>
          <line
            x1={rx}
            x2={rx}
            y1={padTop}
            y2={padTop + plotH}
            stroke="var(--pb-warn)"
            strokeWidth={2}
          />
          <circle cx={rx} cy={padTop + plotH} r={6} fill="var(--pb-warn)" />
        </>
      )}

      {/* Baseline */}
      <line
        x1={padX}
        x2={padX + plotW}
        y1={padTop + plotH}
        y2={padTop + plotH}
        stroke="var(--pb-border-strong)"
        strokeWidth={1}
      />
    </svg>
  );
}
