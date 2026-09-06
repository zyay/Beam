# Beam — mental health

Kľudný slovenský chatbot na podporu duševnej pohody. Tmavé monochromatické UI: WebGL lúče na pozadí,
blueprint mriežka, border-beam karty, particle thinking orb. Žiadne emoji — Phosphor icons.

Živá verzia: **[beam-mental-health.vercel.app](https://beam-mental-health.vercel.app)**

Anglická verzia (primárna, pre vývojárov): **[README.md](./README.md)**

> Beam nie je zdravotnícka pomôcka ani náhrada odbornej pomoci.
> V kríze volaj **0800 900 900** (nonstop), **IPčko 0800 500 500** alebo **112**.

## Ako to funguje

- **Web** (Next.js 16 + Tailwind v4 + OGL + framer-motion + GSAP) beží na Verceli a zároveň je
  backendom pre appku.
- **Chat**: `POST /api/chat` → krízový guard (pevná odpoveď s linkami, model sa vôbec nezavolá) →
  Vercel AI Gateway, predvolený model `minimax/minimax-m3`. Kľúč `AI_GATEWAY_KEY` je len na serveri.
- **Accounts**: e-mail + heslo (bcrypt), JWT HS256 (web: cookie `beam_session` / appka: Bearer header),
  Vercel Postgres (Neon). Bez `POSTGRES_URL` lokálne ukladá účty do `.data/users.json`; na Verceli
  bez databázy padá zámerne chybou `NO_DB`.
- **História chatu** ostáva v zariadení (localStorage, posledných 60 správ).
- **Android APK**: **plne natívna appka** — Kotlin + Jetpack Compose, žiadny webview.
  Builduje ju GitHub Actions pri každom tagu `v*` a appka sa sama aktualizuje z Releases.

## Lokálny beh

```bash
npm install
cp .env.example .env.local   # dopĺň AI_GATEWAY_KEY
npm run dev                  # http://localhost:3000
```

Premenné:

| Premenná | Povinná | Bez nej |
|---|---|---|
| `AI_GATEWAY_KEY` | áno | `/api/chat` vráti **503** |
| `POSTGRES_URL` | v produkcii | lokálne `.data/users.json`, na Verceli `NO_DB` |
| `AUTH_SECRET` | **áno v produkcii** | `lib/auth.ts` má dev fallback — v prode by si mohol ktokoľvek vyrobiť platnú session |
| `AI_GATEWAY_MODEL` | nie | default `minimax/minimax-m3` |

`BEAM_SERVER_URL` a `BEAM_GOOGLE_KEY` patria do GitHub Actions Variables, nie do `.env.local`.

## Deploy na Vercel

1. Importuj repo vo Verceli (framework: Next.js, nič nemeniť) — alebo použi Deploy button v
   [README.md](./README.md), ktorý rovno vytvorí fork.
2. Pridaj Environment Variables (Production + Preview): `AI_GATEWAY_KEY`, `POSTGRES_URL`, `AUTH_SECRET`.
3. V SQL editore databázy spusti `sql/schema.sql`.
4. Deploy. Po prvej návšteve sa zaregistruj → onboarding → chat.

## config/prompts.json — lokalizácia

System prompt, krízové regexy, krízová odpoveď aj všetky chybové texty sú v
[`config/prompts.json`](./config/prompts.json). `lib/prompts.ts` ich skompiluje pri importe.

Krízový guard je bezpečnostná funkcia, preto loader nemôže padnúť: regex sa skladá v `try/catch`
**a** odmietne sa, ak by matchoval prázdny reťazec (prázdne pole patterns by znamenalo, že každá správa
je kríza). Pri akejkoľvek chybe sa použije zabudovaný slovensko-anglický pattern — config teda guard nikdy
nevypne, nanajvýš rozšíri.

Patterns sa matchujú na texte bez diakritiky a malými písmenami, takže písať ich treba ako
`zabit sa`, nie `zabiť sa`.

Guard zámerne uprednostňuje **úplnosť pred presnosťou**: matchovanie holého časovaného tvaru
(`zomrie(t|m|s|me|te)`) znamená, že aj hyperbola *„zomriem od únavy“* dostane krízovú kartu — to je
akceptované, pretože alternatívou je prehliadnuť obyčajné *„zomriem“*. Zámerne sa **nematchuje** minulý
čas (`zomrel`, `umrel`) ani tretia osoba (`zomiera`), takže veta *„starý otec zomrel minulý rok“* ide
normálne do modelu a dostane empatiu, nie linku krízy.

## Android APK (natívna Kotlin appka)

Pri každom pushi tagu `v*` (napr. `git tag v1.9.0 && git push origin v1.9.0`) vybuduje GitHub Actions
`Beam-vX.Y.Z.apk` a vloží ho do [Releases](https://github.com/zyay/beam-mental-health/releases).
Nainštalovaná appka si update stiahne sama, len na Wi-Fi.

Lokálny build (potrebný Android SDK):

```bash
cd android
./gradlew assembleRelease
# výsledok: android/app/build/outputs/apk/release/app-release.apk
```

Natívna časť: `android/app/src/main/java/com/beammental/app/` — `MainActivity` (navigácia),
`screens/` (Auth, Onboarding, Chat, Voice, Settings, UpdateBanner),
`ui/effects/` (`Effects.kt` = blueprint mriežka, `WaveBackground.kt` = procedurálne animované pozadie,
`Avatar.kt` = Kotlin port mascot enginu), `ui/theme/Theme.kt` (4 motívy: Šalvia, Oceán, Noc, Ametyst),
`voice/LiveVoice.kt` (Gemini Live), `data/` (`Api` cez OkHttp, `Session` cez DataStore, `Updater`,
`Locator`).

Technický rozpis natívnej časti: **[docs/ANDROID.md](./docs/ANDROID.md)**.

**Pozor:** release APK sú podpísané debug kľúčom (`signingConfigs.getByName("debug")`) — dá sa tak
inštalovať priamo, ale nie publikovať na Google Play.

## Štruktúra

```
app/                 # App Router: /login /onboarding /chat /nastavenia /pravne /api/*
components/effects/  # BeamBackground (OGL), BlueprintGrid, GlowCard, BorderBeam, ThinkingOrb
components/mascot/   # Mascot + avatar.avatar.json
config/prompts.json  # system prompt, krízové patterns, fallback texty
lib/                 # auth (jose JWT), store (Postgres | lokálny JSON), prompts
android/             # natívna Kotlin/Compose appka
.github/workflows/   # APK release CI
sql/schema.sql       # users tabuľka
```

## Právne

`/pravne/ochrana-sukromia`, `/pravne/vseobecne-podmienky`, `/pravne/zdravotny-disclaimer` —
slovenské texty; chat históriu drží len zariadenie, správy prechádzajú cez server len na
vygenerovanie odpovede.

## Licencia

MIT — [LICENSE](./LICENSE).
