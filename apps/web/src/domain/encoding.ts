/** Matches Kotlin `Encoding.kt` for trade instruction hex parity. */

export const FIXED_SCALE = 1_000_000_000;
export const FIXED_EPSILON = 1e-9;

export function encodeHex(bytes: Uint8Array): string {
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += ((bytes[i]! >>> 4) & 0x0f).toString(16);
    hex += (bytes[i]! & 0x0f).toString(16);
  }
  return hex;
}

export function decodeHex(value: string): Uint8Array {
  const bytes = new Uint8Array(value.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = Number.parseInt(value.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}

function packU64(value: bigint): Uint8Array {
  const out = new Uint8Array(8);
  let v = value;
  for (let i = 0; i < 8; i++) {
    out[i] = Number(v & 0xffn);
    v >>= 8n;
  }
  return out;
}

function packU32(value: bigint): Uint8Array {
  const out = new Uint8Array(4);
  let v = value;
  for (let i = 0; i < 4; i++) {
    out[i] = Number(v & 0xffn);
    v >>= 8n;
  }
  return out;
}

/** Signed i128, little-endian — mirrors Kotlin `packI128LittleEndian`. */
function packI128LittleEndian(value: bigint): Uint8Array {
  const out = new Uint8Array(16);
  const mod = 1n << 128n;
  let v = value % mod;
  if (v < 0n) v += mod;
  for (let i = 0; i < 16; i++) {
    out[i] = Number(v & 0xffn);
    v >>= 8n;
  }
  return out;
}

export function packFixed(value: number): Uint8Array {
  const scaled = BigInt(Math.round(value * FIXED_SCALE));
  return packI128LittleEndian(scaled);
}

function concatBytes(...parts: Uint8Array[]): Uint8Array {
  const len = parts.reduce((a, b) => a + b.length, 0);
  const out = new Uint8Array(len);
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}

export function buildTradeInstructionHex(
  market: import("./types").DemoMarket,
  targetMu: number,
  targetSigma: number,
  collateralRequired: number,
  feePaid: number,
  totalDebit: number,
  maxTotalDebit: number,
  lowerBound: number,
  upperBound: number,
): string {
  const idBytes = decodeHex(market.market_id_hex);
  const bytes = concatBytes(
    Uint8Array.of(1),
    idBytes,
    packU64(BigInt(market.state_version)),
    packFixed(targetMu),
    packFixed(targetSigma),
    packFixed(collateralRequired),
    packFixed(feePaid),
    packFixed(totalDebit),
    packFixed(maxTotalDebit),
    packU32(BigInt(market.taker_fee_bps)),
    packFixed(Number(market.min_taker_fee_display)),
    packFixed(lowerBound),
    packFixed(upperBound),
    packU32(BigInt(market.coarse_samples)),
    packU32(BigInt(market.refine_samples)),
    packU64(BigInt(market.demo_quote_slot)),
    packU64(BigInt(market.demo_quote_expiry_slot)),
  );
  return encodeHex(bytes);
}
