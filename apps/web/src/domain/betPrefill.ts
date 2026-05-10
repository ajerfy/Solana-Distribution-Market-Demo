/** Mirrors Kotlin `BetSheetPrefill`. */

export type RegimeBetSide = "Long" | "Short";
export type PerpBetSide = "Long" | "Short";

export const BetPrefill = {
  muOverride: undefined as number | undefined,
  sigmaOverride: undefined as number | undefined,
  regimeSide: undefined as RegimeBetSide | undefined,
  perpSide: undefined as PerpBetSide | undefined,

  consume(): [number | undefined, number | undefined] {
    const m = BetPrefill.muOverride;
    const s = BetPrefill.sigmaOverride;
    BetPrefill.muOverride = undefined;
    BetPrefill.sigmaOverride = undefined;
    return [m, s];
  },

  consumeRegimeSide(): RegimeBetSide | undefined {
    const s = BetPrefill.regimeSide;
    BetPrefill.regimeSide = undefined;
    return s;
  },

  consumePerpSide(): PerpBetSide | undefined {
    const s = BetPrefill.perpSide;
    BetPrefill.perpSide = undefined;
    return s;
  },
};
