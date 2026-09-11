# Beam Android — libraries.dev redesign ("cozy premium dark")

Date: 2026-09-11
Scope: **Android client only** (`android/`). Web app untouched this pass.
Reference: https://libraries.dev/beam and sibling pages (`/orbs.html`, `/gooey.html`, `/metal.html`, `/image.html`).
Baseline: commit `b9f6422` (v1.14.0). Target release: **v1.15.0** (versionCode 21).

## Why

The v1.14 Android UI reads *muddy*, not cozy. Verified on the BeamTest emulator
(`.audit/before/*.png`): the Šalvia `MeshBackground` wash renders as a low-contrast
olive-green fog; suggestion pills barely separate from it; the sage primary button
renders pale and washed; cards do not lift off the background. Separately the codebase
has no shared component layer (the same `OutlinedTextField` colors are duplicated 5×,
the back-arrow+mascot header 3×, the primary button 5×), 19 hardcoded `Color(0xFF…)`
literals that bypass `BeamColors`, touch targets down to **16dp**, `indication = null`
on nearly every custom clickable (no ripple, no focus), and 10–11sp text.

The user's direction is explicit: make the UI look like **libraries.dev** and use all of
its effects. libraries.dev's own design language is a soft neutral dark (`#121212`, not
OLED black, not olive) with elevated `#1d1d1d` surfaces, hairline borders, an inner
"inset lamplight" glow, tight-tracked large headings, and motion-led accents
(border beam, thinking orbs, gooey, metal, image reveal). That language is warmer and
calmer than the current fog while reading premium.

## Design tokens (verbatim from libraries.dev `site.css` / `beam-spec.json`)

| token | value | use |
|---|---|---|
| `ink` | `#121212` | page background |
| `ink2` | `#101010` | recessed stages |
| `card` | `#1d1d1d` | every surface |
| `cardPressed` | `#1c1c1c` | pressed surface |
| `cardHover` | `#181818` | hover surface |
| `line` | `rgba(44,47,54,0.52)` | hairline border |
| `glow` | `inset 0 0 50px rgba(255,255,255,0.02)` | inner lamplight on every `BeamSurface` |
| `mist` | `#f8f8f8` | primary text |
| text 2..5 | `#ededed`, `#c9c9c9`, `#9e9e9e`, `#767676` | descending text scale |
| `accent` | `#0071fc` | primary accent |
| `accentHover` | `#1a7dff` | |
| `accentActive` | `#0063e0` | |
| `accentSoft` | `#7cd4ff` | light accent |
| neutral btn | `#2a2a2a` / hover `#323232` / active `#262626` | secondary button |
| radii | 24 / 16 / 14 / 12 / 6 / 50 dp | card / medium / stage / icon / chip / pill |
| spacing | 24dp card padding, 10dp internal gap, 12dp gutter | |
| motion | 250ms smooth-out; beam line 3.1s, pulse 2.3s, rotate 1.96s; fade in 0.6s / out 0.5s | |

Typography: bundle **Inter** (OFL) as the UI face — it is libraries.dev's own body face
in its Metal demo. Hero tracking `-0.21px`. Mono retained for meta labels.

## Themes

Keep the existing 4-slot live-swappable picker, but every theme now shares the neutral
`#121212` base and differs only in accent hue + border-beam palette — exactly how
libraries.dev models `colorVariant`:

| slot | key | accent | beam palette |
|---|---|---|---|
| 1 (default) | `beam` | `#0071fc` | `colorful` (9-stop rainbow) |
| 2 | `ocean` | `#4FC3FF` | `ocean` |
| 3 | `zapad` | `#FF8A4C` | `sunset` |
| 4 | `noc` | `#ededed` | `mono` |

The muddy per-theme `ink`/`ink2`/`card` values are deleted. `MeshBackground` keeps its
Lissajous drift but draws from a much lower-gain, neutral-tinted ramp so it reads as
ambient depth, never as fog.

`BeamThemeDef` gains semantic tokens: `danger`, `dangerInk`, `warn`, `ok`, `glow`,
`accentHover`, `accentActive`, `accentSoft`. This deletes all 19 hardcoded literals
(error pink `0xFFFF6E82`/`0xFFFF9FB0`/`0xFFFFC4CB`, danger `0xFFB4525E`, periwinkle
`0xFFC3CBFF`).

## Effects (all five libraries.dev effects, ported to Compose)

1. **Border beam** — new `Modifier.borderBeam()` in `ui/effects/BorderBeam.kt`, faithful
   to `beam-spec.json`: 1dp sweep-gradient rim clipped to the shape outline; dark-theme
   opacities stroke 0.26 / inner 0.42 / bloom 0.24; `innerShadow rgba(255,255,255,0.27)`;
   saturation 1.2; hueRange 30; rotate 1.96s; fade in 0.6s / out 0.5s. Uses the real
   9-stop `colorful` palette (and mono/ocean/sunset per theme). Applied sparingly:
   focused composer, streaming assistant message, crisis card, active theme tile,
   active bottom-nav tab.
2. **Thinking orbs** — new `ThinkingOrb` composable in `ui/effects/ThinkingOrbs.kt`:
   dotted orb with the nine libraries.dev states (`working, searching, solving,
   listening, connecting, weaving, composing, breathing, shaping`), sizes 64 (avatar)
   and 20 (inline), `dark` tuning, `speed`, `paused`. Used as the chat streaming
   presence indicator (replaces the bare caret) and in the voice screen. The
   `breathing` state doubles as a grounding/calm affordance.
3. **Gooey** — new `ui/effects/Gooey.kt`: liquid merge via `RenderEffect` blur +
   contrast on API 31+, graceful no-op fallback below. Used for the composer send
   button morph and the onboarding step transitions.
4. **Metal** — resurrect the existing Compose port at
   `../beam-mental-health/android/.../ui/effects/Metal.kt` (526 lines, single
   `MetalClock` frame loop, counter-rotating halo, reads `BeamColors`) into
   `ui/effects/Metal.kt` essentially verbatim. Used for primary CTAs and voice controls.
5. **Image reveal** — new `ui/effects/ImageReveal.kt`: the img-fx sweep-gradient card
   reveal, used on the onboarding summary card and the Prehľad chart card.

## Component kit (new `ui/components/`)

`BeamSurface` (card + inset glow + hairline), `BeamButton` (primary / neutral / pill),
`BeamIconButton` (**48dp minimum**, ripple, `contentDescription` required by signature),
`BeamTopBar` (back + mascot + title), `BeamTextField` (single source of field colors),
`BeamChip`, `BeamSectionLabel`, `CrisisCard` (shared; crisis lines centralized in one
`CrisisLines` object). All 10 screens migrate onto the kit; the 5× field duplication,
3× header duplication and 5× button duplication are deleted.

## Navigation

Add a bottom navigation bar (Chat / Prehľad / Nastavenia) on the three main screens,
48dp+ targets, border-beam on the active tab. Promote `VoiceScreen` from a
`ChatScreen` overlay to a first-class `Screen` in `MainActivity`.

## Accessibility

- Every interactive target ≥ 48dp (fixes 16dp update-banner close, 20dp back arrows,
  26dp voice close, 36dp header icons, 44dp save, 46dp send).
- Remove `indication = null`; restore ripple and focus feedback everywhere.
- Minimum text size 12sp (fixes 10sp voice labels, 11sp mood/stat/step text).
- `contentDescription` on all icon-only controls; toggle semantics on mute/speaker;
  semantics roles on the crisis phone rows.
- Voice status `letterSpacing` reduced from 1.8sp to a readable value.

## Out of scope

Web app, `strings.xml` extraction of all ~150 strings (only crisis lines are
centralized), light theme, Google Play publishing.

## Verification

Build `assembleDebug`, install on the BeamTest emulator (Android 15, 1080×2400 @420dpi),
capture `.audit/after/*.png` for chat, settings, prehlad, voice, onboarding and compare
against `.audit/before/`. Then `assembleRelease`, bump to 1.15.0 / 21, commit. Tagging
`v1.15.0` (which triggers CI to publish the APK for the in-app self-updater) requires
explicit user confirmation before pushing.
