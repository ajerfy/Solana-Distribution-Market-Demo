import { useMemo } from "react";
import { buildMarketListings } from "../domain/mockMarkets";
import { useParabolaStore } from "../state/parabolaStore";
import { EstimationDetail } from "./EstimationDetail";
import { RegimeDetail } from "./RegimeDetail";
import { PerpDetail } from "./PerpDetail";

export function MarketDetail() {
  const payload = useParabolaStore((s) => s.payload);
  const selectedMarketId = useParabolaStore((s) => s.selectedMarketId);
  const market = useMemo(() => {
    if (!payload || !selectedMarketId) return undefined;
    return buildMarketListings(payload).find((m) => m.id === selectedMarketId);
  }, [payload, selectedMarketId]);
  if (!market) return null;

  if (market.marketType === "Estimation") {
    return <EstimationDetail market={market} />;
  }
  if (market.marketType === "RegimeIndex" && market.regime) {
    return <RegimeDetail market={market} regime={market.regime} />;
  }
  if (market.marketType === "Perp" && market.perp) {
    return <PerpDetail market={market} perp={market.perp} />;
  }
  return null;
}
