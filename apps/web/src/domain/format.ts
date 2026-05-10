export function compactDecimal(value: number, places = 2): string {
  const s = value.toFixed(places);
  return s.includes(".") ? s.replace(/\.?0+$/, "") : s;
}

export function formatVolume(usd: number): string {
  if (usd >= 1_000_000) return `$${compactDecimal(usd / 1_000_000, 2)}M`;
  if (usd >= 1_000) return `$${compactDecimal(usd / 1_000, 1)}k`;
  return `$${compactDecimal(usd, 0)}`;
}

export function shorten(s: string, max = 12): string {
  return s.length > max ? `${s.slice(0, 11)}…` : s;
}

export function shortHash(value: string, head = 4, tail = 4): string {
  if (value.length <= head + tail + 1) return value;
  return `${value.slice(0, head)}…${value.slice(-tail)}`;
}
