import { useMemo } from "react";
import { buildMarketListings } from "../domain/mockMarkets";
import { useParabolaStore } from "../state/parabolaStore";
import { EstimationBetSheet } from "./BetSheet";
import { PerpBetSheet, RegimeBetSheet } from "./RegimePerpBetSheets";

export function BetSheetHost() {
  const show = useParabolaStore((s) => s.showBetSheet);
  const selectedId = useParabolaStore((s) => s.selectedMarketId);
  const payload = useParabolaStore((s) => s.payload);
  const market = useMemo(() => {
    if (!payload || !selectedId) return undefined;
    return buildMarketListings(payload).find((m) => m.id === selectedId);
  }, [payload, selectedId]);
  const setShow = useParabolaStore((s) => s.setShowBetSheet);

  if (!show || !market) return null;

  const close = () => setShow(false);

  if (market.marketType === "Estimation") {
    return <EstimationBetSheet market={market} onDismiss={close} />;
  }
  if (market.marketType === "RegimeIndex" && market.regime) {
    return <RegimeBetSheet market={market} regime={market.regime} onDismiss={close} />;
  }
  if (market.marketType === "Perp" && market.perp) {
    return <PerpBetSheet market={market} perp={market.perp} onDismiss={close} />;
  }
  return null;
}
