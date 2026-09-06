# Beam Android — technical notes

The Android client is **fully native**: Kotlin + Jetpack Compose, no WebView, no Capacitor, no React
Native, no shared JS runtime. It talks to the same Next.js API routes as the web app and re-implements
the visual language with Compose `Canvas` and `graphicsLayer` instead of WebGL.

3 889 lines of Kotlin across 16 files.

```
android/app/src/main/java/com/beammental/app/
```

| File | LOC | Responsibility |
|---|---:|---|
| `MainActivity.kt` | 80 | Splash, edge-to-edge, session restore, `AnimatedContent` navigation over a 4-state `Screen` enum |
| `data/Locator.kt` | 14 | Service locator (`session`, `api`), initialized once in `onCreate` |
| `data/Session.kt` | 71 | DataStore-backed JWT, name, onboarded flag, theme key, chat history |
| `data/Api.kt` | 272 | OkHttp REST client + SSE streaming + the client-side `crisisLike()` heuristic |
| `data/Updater.kt` | 264 | Self-update from GitHub Releases, Wi-Fi gating, notifications, install intent |
| `screens/AuthScreen.kt` | 292 | Login / signup, password visibility toggle, mascot |
| `screens/OnboardingScreen.kt` | 478 | 10-step profile wizard |
| `screens/ChatScreen.kt` | 504 | Chat with streaming deltas, crisis card, history persistence |
| `screens/VoiceScreen.kt` | 416 | Live voice call UI: phases, mute, timer, captions |
| `screens/SettingsScreen.kt` | 263 | Theme picker, name, history reset, logout, update check |
| `screens/UpdateBanner.kt` | 121 | In-app "new version ready" banner |
| `ui/theme/Theme.kt` | 96 | `BeamThemeDef` + four themes + `BeamColors` mutable holder |
| `ui/effects/Effects.kt` | 43 | `BlueprintGrid` — static hairline texture |
| `ui/effects/WaveBackground.kt` | 155 | The animated ambient background (five procedural layers) |
| `ui/effects/Avatar.kt` | 429 | Kotlin port of the JSON avatar engine + `MascotBlob` |
| `voice/LiveVoice.kt` | 391 | Gemini Live bidirectional audio over two WebSockets |

Build: AGP 8.7.2, Gradle 8.14.3, Kotlin 2.0.21, Compose BOM 2024.12.01, minSdk 24, target/compileSdk 35,
JVM target 17. Dependencies are deliberately thin: OkHttp 4.12, kotlinx-serialization-json 1.7.3,
DataStore Preferences 1.1.1, core-splashscreen 1.0.1.

## Build and run

```bash
cd android
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

On Windows, set `JAVA_HOME` to a JDK 17+ first, e.g.:

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease
```

`lint.checkReleaseBuilds` is disabled — `lintVital` breaks on Windows paths with this project layout,
and CI builds release artifacts anyway.

## Navigation

There is no navigation library. `MainActivity` holds a single `mutableStateOf<Screen?>` and renders one
`AnimatedContent` with a fade cross-transition. The initial screen is decided once, synchronously, in a
`runBlocking` block before `setContent`, so the app never flashes the auth screen for a signed-in user:

```kotlin
runBlocking {
    BeamColors.apply(beamThemeByKey(Locator.session.theme()))
    val token = Locator.session.token()
    screen = when {
        token == null -> Screen.Auth
        !Locator.session.onboarded() -> Screen.Onboarding
        else -> Screen.Chat
    }
}
```

`Api.login()` sets the persisted `onboarded` flag when the server reports `hasProfile: true`, so an
existing account is never routed back through onboarding.

## Theming

`BeamThemeDef` is a flat record: `ink`, `ink2`, `card`, `line`, `fog`, `mist`, `accent`, `accentInk`,
plus `wave: List<Color>` — a four-colour ramp consumed by `WaveBackground`. Four themes ship:

| key | label | accent | wave ramp |
|---|---|---|---|
| `salvia` | Šalvia | `#8FBF9A` | sage / moss / pale green / sand |
| `ocean` | Oceán | `#4FC3FF` | `#0044FF` / `#1467E3` / `#0099FF` / `#52C5FF` |
| `noc` | Noc | `#EDE8DC` | graphite ramp, near-monochrome |
| `ametyst` | Ametyst | `#A98BFF` | `#7C5CFC` / `#4A2E9E` / `#B18CFF` / `#D6C6FF` |

Switching themes writes to `BeamColors`, a global object whose `current` is a `var by mutableStateOf`
with a private setter. Every colour is a `get() = current.x` property, so any composable reading one is
subscribed — the whole screen recomposes, wave ramp included, without an activity restart and without a
`CompositionLocal`. `BeamTheme` maps those values onto a `darkColorScheme`; the app is dark-only by
design and ignores the system light theme. The theme key persists in DataStore and is applied in
`MainActivity` before the first frame.

> In the definitions, "sage"-family slot names mean *accent*. The `noc` theme is the one that matches
> the web app's monochrome look most closely.

## WaveBackground — five layers, no shaders

The web background is a WebGL fragment shader. Android has no equivalent that works down to API 24
without AGSL (API 33+), so this is pure procedural drawing in one `DrawScope`, driven by a single
`rememberInfiniteTransition` on a 36 000 ms linear loop. One time value feeds everything, so the layers
never drift out of phase. The transition animates a plain 0 → 1 float; it is converted once to a phase in
radians (`time = t * 2π`) and every layer reads that. In the formulas below, `t` is that phase.

1. **Deep wash** — a vertical gradient: `wave[0]` at 5.5 % alpha bleeding in from the top, transparent
   at 42 %, then `theme.ink` at 55 % alpha at the bottom for depth.
2. **Three aurora blooms** (`for (i in 0..2)`) — radial gradients whose centres follow Lissajous paths:
   `cx = w * (0.22 + 0.56 * (0.5 + 0.5·sin(t·(0.6 + 0.23i) + 2.4i)))`,
   `cy = h * (0.14 + 0.26i + 0.05·sin(t·(0.9 + 0.31i) + i))`, radius breathing on `sin(t·1.1 + 1.7i)`,
   alpha on `0.5 + 0.5·sin(t·0.8 + 2i)`.
3. **Four waves** (`for (i in 0 until 4)`) — each ridge is three superposed sinusoids sampled across the
   width: `sin(u·3.6π + φ)·a + sin(u·7.7π − 1.4φ)·0.35a + sin(u·1.3π + 0.6φ)·0.55a`. The three
   incommensurate frequencies are what stop it from looking like a looping sine. The filled body is
   translucent; the ridge itself gets a brighter stroke so it reads as a glowing line.
4. **Eighteen particles** (`for (i in 0 until 18)`) — positions, speeds (`0.30 + hash01(i·7.7)·0.45`),
   sizes and twinkle rates all come from `hash01`, a `fract(sin(n·127.1 + 311.7)·43758.5453)` hash. It is
   deterministic, so the field is identical on every launch and every device — no per-instance `Random`,
   no stored state. Each particle rises on a cycle, fades in and out with `sin(π·cycle)`, and twinkles
   on its own frequency.
5. **Vignette** — a radial gradient darkening the corners to pull the eye to the content.

`Effects.kt` holds the other background, `BlueprintGrid`: a static hairline grid with a 32 dp cell, used
on the auth screen where motion would compete with the form.

## Avatar.kt — the mascot engine, ported by hand

The web mascot is `components/mascot/avatar.avatar.json` rendered by `@bible-strong/avatar-react`.
Android has no JS runtime, so `Avatar.kt` is a from-scratch Kotlin port reading the same definition
(`app/src/main/assets/avatar.json`, schema `bible-strong/avatar-definition`).

**Model.** Twelve `@Serializable` classes mirror the JSON shape, parsed with kotlinx-serialization:
`AvatarDef`, `BodyDef`, `SurfaceDef`, `ColorsDef`, `ExprDef`, `Vec3`, `EyesDef`, `EyeDef`, `MotionDef`,
`AnimDef`, `AnimStep`, `BlinkDef`. Every field has a default, so a partial definition still loads.

**Playback.** `AvatarEngine` is a step machine over the definition's animation tracks:
`stepIndex`, `direction` and `phase` (hold / transition), with `loop`, `once` and `pingPong` playback
modes. `requestAnimation(key)` switches tracks — the UI uses `idle`, `thinking` and named expressions.
`advance(now)` moves the machine; `frame(now)` produces an `AvatarFrame`: interpolated eye geometry
(position, size, angle per eye), body colour and corner ratio, head rotation on three axes.

**Easing.** Three curves, ported 1:1 from `avatar-core`:

```kotlin
"smooth" -> t * t * (3f - 2f * t)                       // Hermite smoothstep
"snappy" -> 1f - (1f - t).pow(3)                        // cubic ease-out
else     -> (1 - exp(-6t)·cos(8t)) / (1 - exp(-6)·cos(8))  // damped spring, normalized to end at 1
```

**Ambient motion.** Two noise functions keep the mascot from looking robotic:
`vnoise(time, channel, seed, period)` — value noise over an integer lattice with `smooth01`
interpolation — drives slow drift, and `saccade(time, channel)` produces micro-saccades: a quick 140 ms
jump, then a hold until the next period. Blinking is a track in the definition, not a timer.

**Rendering.** `MascotBlob` runs a `while (true)` loop inside `LaunchedEffect`, sampling
`withFrameNanos` and pushing a new `AvatarFrame` into state. Head tilt is applied with
`graphicsLayer { rotationX/Y/Z }` (real 3D transform, hardware-accelerated), while the body and eyes are
drawn on a `Canvas`: a rounded rect for the body and two rotated rounded-rect pills for the eyes. The
avatar's own 245-unit coordinate space is scaled by `min(width, height) / 245f`, so the JSON's numbers
are resolution-independent.

## Networking

`Api` is a thin OkHttp client over `BuildConfig.BEAM_URL`. The JWT travels as
`Authorization: Bearer <token>`; the web app uses a cookie instead, and `lib/auth.ts` accepts either.

`chatStream()` handles both response shapes on one endpoint, because the server may answer with JSON
(crisis guard, errors) or with an SSE stream:

```kotlin
val ctype = resp.header("Content-Type").orEmpty()
if (resp.code != 200 || "application/json" in ctype) { /* parse a single JSON body */ }
else { /* read data: lines until [DONE] */ }
```

Delta extraction is `choices[0].delta.content`. Accumulated text is kept in a local `StringBuilder` so
that a mid-stream network failure still yields the partial answer instead of an error.

> **Gotcha that cost a release.** `jsonPrimitive.contentOrNull` returns **null for non-string
> primitives**. `obj["crisis"]?.jsonPrimitive?.contentOrNull == "true"` is therefore always false, and
> `obj["hasProfile"]?.jsonPrimitive?.contentOrNull == "true"` was silently routing every login back
> through onboarding. Booleans need `booleanOrNull`, numbers `intOrNull`. Fixed in v1.8.1.

`Api.crisisLike()` is a client-side copy of the server's crisis regex, used to render the crisis card
optimistically before the response arrives. **It is Slovak-only and is not the authority** — the server
returns `crisis: true` and the app honours that regardless. Porting this heuristic is the main thing to
do when localizing.

## LiveVoice — Gemini Live over two WebSockets

`voice/LiveVoice.kt` opens **two** parallel sockets to the same endpoint,
`wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`:

- **Dialog session** — `models/gemini-2.5-flash-native-audio-latest`, `voice_name: "Kore"`. Audio in,
  native audio out. Its system prompt carries the same crisis-line instructions as the web system prompt.
- **Transcribe session** — `models/gemini-3.5-transcribe-live`, used only for live captions of what the
  user is saying. If it fails or closes, `transcribeDead` flips and captions fall back to the dialog
  session's own transcription — the call continues.

Both sockets are `pingInterval(20, TimeUnit.SECONDS)` to survive mobile NAT timeouts.

**Capture.** `AudioRecord` with `MediaRecorder.AudioSource.VOICE_COMMUNICATION` (hardware echo
cancellation — essential, otherwise the model hears itself), 16 kHz mono PCM16, buffer
`max(minBufferSize, 5120)`. Frames of `FRAME_IN = 1280` samples = **80 ms**, base64-encoded
(`Base64.NO_WRAP`) and sent to *both* sockets.

**Playback.** `AudioTrack` in `MODE_STREAM` at 24 kHz mono PCM16 (the API's fixed output rate). Incoming
`serverContent.modelTurn.parts[].inlineData.data` is base64-decoded onto a
`LinkedBlockingQueue<ByteArray>`; a dedicated playback loop drains it in 960-byte slices —
`SAMPLE_OUT / 50 * 2` = **20 ms** — with `WRITE_BLOCKING`, so small writes never underrun the track.

**Barge-in.** When `serverContent.interrupted` arrives, playback pauses and the queue is flushed, so the
user can talk over the model and it stops immediately rather than finishing its sentence.

**Phases.** `VoicePhase = CONNECTING | LISTENING | SPEAKING | ENDED | FAILED`. A 20 s watchdog thread
catches the failure mode where sockets open but `setupComplete` never arrives — without it the UI hangs
in CONNECTING forever.

If `BuildConfig.BEAM_GOOGLE_KEY` is blank (any local build without the CI variable), `VoiceScreen`
shows a notice instead of crashing.

## Updater — self-update from GitHub Releases

`data/Updater.kt` polls `https://api.github.com/repos/zyay/beam-mental-health/releases/latest`, compares
`parseVersion(tag)` against `BuildConfig.VERSION_NAME`, and:

1. Downloads to `Beam-vX.Y.Z.apk.part` in app-private storage, then `renameTo` the final name — an
   interrupted download can never be mistaken for a complete APK.
2. **Gates on Wi-Fi.** `onWifi()` checks `NetworkCapabilities.TRANSPORT_WIFI`; `observeWifi()` registers
   a `NetworkCallback` so a download queued on mobile data starts the moment Wi-Fi appears.
3. Installs via `FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)` and an
   `ACTION_VIEW` intent with `application/vnd.android.package-archive`, backed by `res/xml/file_paths.xml`.
4. Raises notifications (`notifyAvailable`, `notifyReady`) on a dedicated channel.
5. `cleanup()` removes superseded APKs, keeping only the current one.

Every `ConnectivityManager` call is wrapped in `runCatching`. This is load-bearing, not defensive
posturing: `registerNetworkCallback` throws `SecurityException` without `ACCESS_NETWORK_STATE`, and an
exception inside a `DisposableEffect` takes the whole app down. v1.7.0 shipped that crash — the chat
screen died on every composition — and it was fixed in v1.8.1.

## Manifest

```
INTERNET                 REST, SSE, WebSockets, release downloads
ACCESS_NETWORK_STATE     Wi-Fi gating in Updater (see above — omitting it crashed the app)
RECORD_AUDIO             LiveVoice capture
REQUEST_INSTALL_PACKAGES self-update install intent
POST_NOTIFICATIONS       update notifications (API 33+)
```

`usesCleartextTraffic="false"` — everything is HTTPS/WSS. `windowSoftInputMode="adjustResize"` on the
single activity so the chat composer is never covered by the keyboard. A splash theme
(`Theme.Beam.Splash`) is installed via `core-splashscreen`.

## Release flow

`.github/workflows/android-release.yml` runs on `push: tags: ["v*"]` and on `workflow_dispatch`:

```
checkout@v4 → setup-java@v4 (temurin 21)
  → cd android && ./gradlew assembleRelease --no-daemon
  → cp app-release.apk dist/Beam-${GITHUB_REF_NAME}.apk
  → softprops/action-gh-release@v2  (tag runs, generate_release_notes: true)
  → actions/upload-artifact@v4      (manual runs)
```

with `permissions: contents: write`. Installed apps then pick the release up by themselves over Wi-Fi.

```bash
# bump versionCode/versionName in android/app/build.gradle.kts first
git commit -am "v1.9.0" && git tag v1.9.0 && git push origin master --tags
```

### Env injection: the empty-string trap

`BEAM_SERVER_URL` and `BEAM_GOOGLE_KEY` are GitHub Actions **Variables** (not secrets) passed as
environment variables to the Gradle step, and become `BuildConfig` fields:

```kotlin
buildConfigField(
    "String", "BEAM_URL",
    "\"${providers.environmentVariable("BEAM_SERVER_URL")
        .getOrElse("https://beam-mental-health.vercel.app")
        .trim()
        .ifEmpty { "https://beam-mental-health.vercel.app" }}\""
)
```

`getOrElse` alone is not enough. An unset `vars.X` in Actions resolves to an **empty string**, which
counts as a *present* environment variable — so `getOrElse` returns `""` and the app builds pointing at
an empty base URL. The `.trim().ifEmpty { default }` tail is what actually protects the default. This
shipped broken once (v1.3.0) and was fixed in v1.3.1.

`BEAM_GOOGLE_KEY` intentionally uses `getOrElse("").trim()` with no fallback: a blank key is the correct
local-build state, and `VoiceScreen` handles it with a notice.

## Caveats

- **Release APKs are signed with the debug keystore** — `signingConfig = signingConfigs.getByName("debug")`.
  That is what lets CI produce an installable APK with zero secret management, but it cannot be published
  to Google Play and every install shares one well-known signature. Add a real keystore (base64 into a
  repo secret, `signingConfigs.create("release")`) before shipping to a store.
- **`crisisLike()` is Slovak-only** — see above. Server-authoritative, but the optimistic card will not
  appear for other languages.
- **Crisis hotlines are Slovak** (`0800 900 900`, `0800 500 500`, `112`) and are hardcoded in
  `LiveVoice`'s system prompt and in `AuthScreen`, `ChatScreen`, `OnboardingScreen` and
  `SettingsScreen`. Localize all five places.
- **`MascotBlob` hardcodes the avatar coordinate space** — it scales by `min(width, height) / 245f`
  rather than reading `SurfaceDef.width`/`height`, which default to 245. A definition exported at a
  different size renders mis-scaled. Read the surface size from the definition if you author your own.
- **The whole UI is Slovak** and is not behind a string-resource layer — text is inline in the
  composables. A real localization pass means extracting to `strings.xml` first.
