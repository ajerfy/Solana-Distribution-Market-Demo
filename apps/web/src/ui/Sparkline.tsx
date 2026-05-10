type Props = {
  values: number[];
  color?: string;
  width?: number;
  height?: number;
};

export function Sparkline({
  values,
  color = "var(--pb-crowd)",
  width = 72,
  height = 28,
}: Props) {
  if (values.length < 2) return null;
  const minV = Math.min(...values);
  const maxV = Math.max(...values);
  const span = maxV - minV || 1;
  const step = width / (values.length - 1);
  const pts = values
    .map((v, i) => {
      const x = i * step;
      const y = height - ((v - minV) / span) * height;
      return `${i === 0 ? "M" : "L"} ${x} ${y}`;
    })
    .join(" ");
  const lastY = height - ((values[values.length - 1]! - minV) / span) * height;
  return (
    <svg width={width} height={height} aria-hidden>
      <path
        d={pts}
        fill="none"
        stroke={color}
        strokeWidth={2}
        strokeLinecap="round"
      />
      <circle cx={width} cy={lastY} r={3} fill={color} />
    </svg>
  );
}
