# Beam

**How to build a full-stack AI chatbot with raw WebGL shaders and a fully native Android companion.**

Beam is a complete, production-deployed AI chat application: a Next.js 16 web app that doubles as the
backend, and a native Kotlin / Jetpack Compose Android client that talks to it — no WebView, no
Capacitor, no React Native. The interesting parts are the ones people usually skip: a crisis guard that
outranks the model, a shader background written directly against OGL, streaming SSE chat, a
live bidirectional voice call over Gemini Live, and an app that updates itself from GitHub Releases.

Everything here runs in production at **[beam-mental-health.vercel.app](https://beam-mental-health.vercel.app)**,
and the Android APK ships from GitHub Actions on every `v*` tag.

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https%3A%2F%2Fgithub.com%2Fzyay%2Fbeam-mental-health&project-name=beam&env=AI_GATEWAY_KEY%2CPOSTGRES_URL%2CAUTH_SECRET&envDescription=AI_GATEWAY_KEY%3A+key+from+the+Vercel+AI+Gateway+dashboard+%28chat+returns+503+without+it%29.+POSTGRES_URL%3A+any+Postgres+connection+string%2C+then+run+sql%2Fschema.sql+once.+AUTH_SECRET%3A+random+string%2C+e.g.+openssl+rand+-base64+32+-+required+in+production.&demo-title=Beam&demo-url=https%3A%2F%2Fbeam-mental-health.vercel.app)

*The Deploy button copies this repo into your own GitHub account with the environment variables pre-filled.*

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

**Slovak version:** [README.sk.md](./README.sk.md) — the deployed product itself is in Slovak.

---

## Why fork this

Most "AI chat template" repos give you a text box and an API call. Beam gives you the parts that take
a weekend each to get right:

| | |
|---|---|
| **Crisis guard that the model cannot override** | `POST /api/chat` runs a regex over the last user message *before* any upstream call. On a match it returns a fixed protocol message with hotline numbers and `crisis: true`, and the language model is never invoked. Patterns, response and all fallback strings live in [`config/prompts.json`](./config/prompts.json) — see [Localize it](#localize-it-in-one-file). |
| **Raw WebGL, not a library wrapper** | [`BeamBackground`](./components/effects/BeamBackground.tsx) is ~170 lines of OGL: a full-screen triangle, a hand-written fragment shader with value noise and three drifting beams, DPR clamping, `prefers-reduced-motion` bail-out, `webglcontextlost` recovery and a pure-CSS fallback gradient. |
| **Streaming SSE chat** | The same endpoint serves both a JSON response and a streamed one. With `stream: true` it passes the upstream SSE body straight through; the native app consumes it token by token. |
| **Native Gemini Live voice** | [`LiveVoice.kt`](./android/app/src/main/java/com/beammental/app/voice/LiveVoice.kt) holds two parallel WebSockets to `BidiGenerateContent` — one for `gemini-2.5-flash-native-audio-latest`, one for `gemini-3.5-transcribe-live` — with `AudioRecord` capture, `AudioTrack` playback and barge-in interruption. |
| **Self-updating APK** | [`Updater.kt`](./android/app/src/main/java/com/beammental/app/data/Updater.kt) polls `releases/latest`, downloads only on Wi-Fi (observed via `NetworkCallback`), writes to `.part` then renames, and installs through a FileProvider. |
| **Procedural Android backgrounds** | [`WaveBackground.kt`](./android/app/src/main/java/com/beammental/app/ui/effects/WaveBackground.kt) draws five layers — deep wash, three Lissajous-drifting aurora blooms, four superposed-sinusoid waves, 18 hash-deterministic particles, vignette — in one `DrawScope`, no shaders, no AGSL. |
| **Data-driven mascot** | The web mascot is a 40 KB JSON avatar definition rendered by `@bible-strong/avatar-react`; the Android mascot is a hand-written Kotlin port of the same engine (step machine, three easing curves, value noise, micro-saccades). One JSON file drives both platforms. |
| **Four live-swappable themes** | A global `BeamColors` object backed by `mutableStateOf` (ink / card / line / fog / mist / accent + a 4-colour wave ramp). Every reader subscribes, so switching themes recomposes everything on screen — background included — without an activity restart. |

Full technical write-up of the native side: **[docs/ANDROID.md](./docs/ANDROID.md)**.

## Stack

**Web / backend**

| Layer | Choice |
|---|---|
| Framework | Next.js 16.3 (App Router) on React 19.2, TypeScript strict |
| Styling | Tailwind CSS v4 with `@theme` design tokens (`--color-ink`, `-mist`, `-fog`, `-line`) |
| Motion | framer-motion 13, GSAP 3.15 via `@gsap/react` (`useGSAP` scoped timelines) |
| Graphics | OGL 1.0 (raw WebGL) + Canvas 2D |
| Icons | `@phosphor-icons/react` — no emoji anywhere in the UI |
| Auth | bcryptjs password hashing, HS256 JWT via `jose`, 30-day expiry |
| Data | `@vercel/postgres` (Neon) when `POSTGRES_URL` is set, else a local `.data/users.json` file |
| AI | Vercel AI Gateway (`ai-gateway.vercel.sh/v1/chat/completions`), default model `minimax/minimax-m3` |
| Hosting | Vercel serverless functions, `maxDuration = 60` |

**Android**

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3 |
| Build | AGP 8.7.2, Gradle 8.14.3, minSdk 24, target/compileSdk 35 |
| Networking | OkHttp 4.12 (REST + SSE + two WebSockets) |
| Serialization | kotlinx-serialization-json 1.7.3 |
| Persistence | DataStore Preferences 1.1.1 |
| Voice | Gemini Live API, native audio |
| CI | GitHub Actions → Temurin 21 → `assembleRelease` → GitHub Releases |

## Quick start

```bash
git clone https://github.com/zyay/beam-mental-health
cd beam-mental-health
npm install
cp .env.example .env.local    # then fill in AI_GATEWAY_KEY
npm run dev                   # http://localhost:3000
```

Accounts work with no database at all in local dev — `lib/store.ts` falls back to `.data/users.json`
(git-ignored). On Vercel it fails loudly with `NO_DB` instead of silently dropping signups, because the
serverless filesystem is ephemeral.

To run the schema against a real database:

```bash
psql "$POSTGRES_URL" -f sql/schema.sql
```

Android:

```bash
cd android
./gradlew assembleRelease
# android/app/build/outputs/apk/release/app-release.apk
```

The client's backend URL is `BuildConfig.BEAM_URL`, injected from the `BEAM_SERVER_URL` environment
variable when Gradle configures, defaulting to the production deployment. The guard is
`.getOrElse(default).trim().ifEmpty { default }` because an unset GitHub Actions variable resolves to an
empty string, not `null` — see [docs/ANDROID.md](./docs/ANDROID.md).

### Environment variables

| Variable | Required | What happens without it |
|---|---|---|
| `AI_GATEWAY_KEY` | Yes | `/api/chat` returns **503** with a message pointing at the missing key. |
| `POSTGRES_URL` | In production | Local dev uses `.data/users.json`. On Vercel, auth throws `NO_DB`. |
| `AUTH_SECRET` | **Yes, in production** | `lib/auth.ts` falls back to a hardcoded development secret. That is fine on your laptop and unacceptable in production — anyone could forge a session. Set it (`openssl rand -base64 32`). |
| `AI_GATEWAY_MODEL` | No | Defaults to `minimax/minimax-m3`. Any model your gateway key can reach works — this is the only place the model is named. |

`BEAM_SERVER_URL` and `BEAM_GOOGLE_KEY` are **Android CI** variables and belong in
*Settings → Secrets and variables → Actions → Variables*, not in `.env.local`. See `.env.example`.

## Localize it in one file

The product ships in Slovak, but nothing language-specific is buried in code. Edit
[`config/prompts.json`](./config/prompts.json):

```jsonc
{
  "system": "…the full system prompt…",
  "crisis": {
    "patterns": ["…regex alternation, matched against lowercased, diacritic-stripped text…"],
    "response": "…the fixed message returned instead of a model answer…"
  },
  "fallbacks": {
    "unauthenticated": "…", "badRequest": "…", "missingMessage": "…",
    "noKey": "…", "upstreamError": "…", "emptyResponse": "…", "timeout": "…"
  }
}
```

`lib/prompts.ts` compiles it at import time. The crisis guard is a safety feature, so the loader is
deliberately paranoid: the pattern is built inside `try/catch`, **and** rejected if it matches the empty
string (an empty `patterns` array would compile to a regex matching *everything* and flag every message
as a crisis — strictly worse than no guard). On any failure it logs and uses a built-in Slovak + English
pattern, so a bad edit can widen the guard but can never switch it off.

Patterns are matched against text that has been lowercased and stripped of diacritics, because users
type without them. Write your patterns accordingly — `zabit sa`, not `zabiť sa`.

The shipped patterns favour **recall over precision**. Matching a bare conjugated form (`zomrie(t|m|s|me|te)`)
means hyperbole like *"zomriem od únavy"* also gets the crisis card — accepted, because the alternative is
missing a plain *"zomriem"*. What is deliberately **not** matched: past tense (`zomrel`, `umrel`) and third
person (`zomiera`), so bereavement disclosures like *"starý otec zomrel minulý rok"* reach the model and get
empathy instead of a hotline card. Negated conditionals (*"nebojím sa, že by som zomrel"*) are excluded the
same way — the conditional pattern is anchored on `radšej`/`keby som`, not on `by som`.

> **Known limitation.** The Android client has its own client-side heuristic
> (`Api.crisisLike()`) used to render the crisis card optimistically. It is Slovak-only. The server is
> authoritative: after localizing `prompts.json` the API still returns `crisis: true` and the app honours
> it. Porting the client heuristic is a good first issue.

## UI effects you can lift out

These are self-contained — copy the file, keep the Tailwind tokens or inline the colours:

| Component | Technique |
|---|---|
| [`BeamBackground`](./components/effects/BeamBackground.tsx) | OGL full-screen triangle + fragment shader (value noise, three drifting beams, film grain). Rendered on `/login` at 7% opacity. |
| [`BlueprintGrid`](./components/effects/BlueprintGrid.tsx) | DOM-based blueprint sheet: hairline grid, dot intersections, ruler ticks, hatched corners, and cells that relight at random every 1.6 s. Responsive column count, no `<canvas>`. On `/login`. |
| [`GlowCard`](./components/effects/GlowCard.tsx) | Pointer-tracked border highlight — the cursor position is written into `--mx`/`--my` and a radial gradient is masked to the border. |
| [`BorderBeam`](./components/effects/BorderBeam.tsx) | A light travelling around a card's border. On `/onboarding` and `/nastavenia`. |
| [`ThinkingOrb`](./components/effects/ThinkingOrb.tsx) | 140-point Fibonacci sphere projected onto a 2D canvas, far hemisphere dimmed, `prefers-reduced-motion` aware. The "model is thinking" indicator. |
| [`Mascot`](./components/mascot/Mascot.tsx) | Renders a JSON avatar definition; the same JSON drives the Kotlin port. |
| [`AnimatedBeam`](./components/effects/AnimatedBeam.tsx) | Available but **not rendered anywhere** — the login page settled on the static blueprint look. Kept because it works. |

Using one in your own app:

```tsx
import BeamBackground from "@/components/effects/BeamBackground";

<main className="relative">
  <BeamBackground className="opacity-[0.07]" />
  <div className="relative z-0">{/* your content */}</div>
</main>
```

The effect positions itself `absolute inset-0 -z-10` and is `pointer-events-none`, so give your content
`relative z-0`. It opts out to a static CSS gradient under `prefers-reduced-motion` and if the GL context
is lost, and never throws if WebGL is unavailable.

## Project structure

```
app/
  login/            # marketing + auth screen (GSAP timeline, bento grid, WebGL background)
  onboarding/       # profile setup
  chat/             # web chat client (history in localStorage, last 60 messages)
  nastavenia/       # settings
  pravne/[slug]/    # privacy, terms, medical disclaimer
  api/auth/*        # signup, login, logout, me
  api/chat/         # crisis guard → AI Gateway (JSON or SSE passthrough)
  api/profile/      # onboarding profile
components/
  effects/          # BeamBackground, BlueprintGrid, GlowCard, BorderBeam, ThinkingOrb, AnimatedBeam
  mascot/           # Mascot + avatar.avatar.json definition
config/prompts.json # system prompt, crisis patterns/response, all fallback strings
lib/                # auth (jose JWT), store (Postgres | local JSON), prompts (config loader)
middleware.ts       # route protection
sql/schema.sql      # users table
android/            # native Kotlin/Compose client — see docs/ANDROID.md
.github/workflows/  # android-release.yml: v* tag → APK → GitHub Release
```

## Releases

Tag it and CI does the rest:

```bash
git tag v1.2.3 && git push origin v1.2.3
```

The workflow builds with Temurin 21, runs `assembleRelease`, and publishes `Beam-vX.Y.Z.apk` to
[Releases](https://github.com/zyay/beam-mental-health/releases). Installed apps pick it up on their
own over Wi-Fi.

## Before you fork — three caveats

1. **This is not a medical device.** Beam is a mental-wellbeing conversation app. It does not diagnose
   or treat anything, and the system prompt forbids medication advice. If you deploy a derivative for
   real users, you own the regulatory position in your jurisdiction.
2. **The crisis numbers are Slovak.** `0800 900 900` (Linka krízy), `0800 500 500` (IPčko) and `112`
   appear in `config/prompts.json`, in the legal pages and in the UI. If you localize, **replace them** —
   a dead hotline in a crisis path is worse than none.
3. **Release APKs are signed with the debug keystore.** `android/app/build.gradle.kts` uses
   `signingConfigs.getByName("debug")`, which is what makes CI produce an installable APK with zero
   secret management. You cannot publish that to Google Play. Add a real keystore before shipping.

## License

MIT — see [LICENSE](./LICENSE).
