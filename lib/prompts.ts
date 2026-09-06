import config from "@/config/prompts.json";

/** Built-in guard, used only if config/prompts.json yields an unusable pattern.
 * Editing the JSON is enough to localize — this stays as a floor, so it must
 * never be narrower than the shipped guard or a typo would silently reduce
 * crisis coverage. */
const FALLBACK_PATTERN =
  "(nechcem\\s+(uz\\s+)?zit|uz\\s+nechcem\\s+zit|nemam\\s+(chut|silu)\\s+zi(t|t)|nemam\\s+(dovod|preco|zmysel)\\s+zit|skoncit\\s+(so zivo|to)|zabi(t|jem|jeme|ju)\\s+sa|chcem\\s+(sa\\s+)?zabit|obes\\w*\\s+sa|podrez\\w*\\s+(si\\s+)?(zil|ruk|krk|zapast)|skocim\\s+(pod|z|zo|pred)\\s+|predavk\\w*|sebavraz\\w*|sebapostodzov\\w*|sebaposkodzov\\w*|suicid\\w*|zomrie(t|m|s|me|te)|umrie(t|m|s|me|te)|zomiera(t|m|s|me|te)|umiera(t|m|s|me|te)|prehltn\\w*\\s+(table|pilul)|chcem\\s+zomrie|jedno\\s+ci\\s+(zijem|ze\\s+zijem)|((radsej|najradsej)\\s+(by\\s+)?(som\\s+)?|keby\\s+som\\s+(tak\\s+)?)(zomrel|umrel)(a|i)?|kill\\s+myself|end\\s+my\\s+life|end\\s+it\\s+all|want\\s+to\\s+die|hurt\\s+myself|self[\\s\\-]?harm|overdos\\w*|take\\s+my\\s+(own\\s+)?life|no\\s+reason\\s+to\\s+live|better\\s+off\\s+dead|want\\s+to\\s+be\\s+dead|wish\\s+(i\\s+was|i\\s+were|to\\s+be)\\s+dead|(i\\s+am|i'?m)\\s+dying\\b(?!\\s+(to|of|for)))";

/** Matched against diacritic-stripped, lowercased text. */
function buildCrisisRx(patterns: string[]): RegExp {
  try {
    const rx = new RegExp(patterns.join("|"));
    // A pattern matching the empty string would flag every message as a crisis,
    // which is as broken as no guard at all.
    if (!rx.test("")) return rx;
  } catch {
    // invalid pattern string
  }
  console.error(
    "[prompts] config/prompts.json crisis.patterns is unusable — using the built-in guard"
  );
  return new RegExp(FALLBACK_PATTERN);
}

export const CRISIS_RX = buildCrisisRx(config.crisis.patterns);
export const CRISIS_RESPONSE = config.crisis.response;
export const SYSTEM = config.system;
export const FALLBACKS = config.fallbacks;
