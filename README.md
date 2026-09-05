# Beam — mental health

Kľudný slovenský chatbot na podporu duševnej pohody. Tmavé UI inšpirované
[libraries.dev](https://libraries.dev/beam) efektmi: WebGL lúče na pozadí,
border-beam karty, particle thinking orb. Žiadne emoji — Phosphor icons.

> Beam nie je zdravotnícka pomôcka ani náhrada odbornej pomoci.
> V kríze volaj **0800 900 900** (nonstop) alebo **112**.

## Ako to funguje

- **Web** (Next.js 16 + Tailwind v4 + OGL + framer-motion) beží na Verceli.
- **Chat**: `/api/chat` → krízový guard (pevná odpoveď s linkami) →
  DeepSeek cez Hugging Face router. `HF_TOKEN` je len na serveri.
- **Účty**: e-mail + heslo (bcrypt), JWT session cookie, Vercel Postgres (Neon).
- **Android APK**: Capacitor shell ukazuje na Vercel URL — APK vždy odráža
  aktuálnu web verziu. Builduje ho GitHub Actions pri každom tagu `v*`.

## Lokálny beh

```bash
npm install
npm run dev          # http://localhost:3000
```

`.env.local`:

```
HF_TOKEN=hf_...            # https://huggingface.co/settings/tokens
# POSTGRES_URL nepotrebný lokálne — bez neho účty ukladá do .data/users.json
```

## Deploy na Vercel

1. Importuj repo vo Verceli (framework: Next.js, nič nemenniť).
2. Pridaj Environment Variables (Production + Preview):
   - `HF_TOKEN` — token z huggingface.co
   - `POSTGRES_URL` — Vercel Postgres / Neon connection string
   - `AUTH_SECRET` — náhodný reťazec (`openssl rand -base64 32`)
3. V SQL editore databázy spusti `sql/schema.sql`.
4. Deploy. Po prvej návšteve sa zaregistruj → onboarding → chat.

## Android APK

Pri každom pushi tagu `v*` (napr. `git tag v1.0.0 && git push origin v1.0.0`)
vybuduje GitHub Actions `Beam-vX.Y.Z.apk` a vloží ho do [Releases](../../releases).

APK číta URL z repo variable `BEAM_SERVER_URL` (Settings → Secrets and
variables → Actions → Variables); bez nej sa použije
`https://beam-mental-health.vercel.app`.

Lokálny build: `npx cap sync android && cd android && ./gradlew assembleRelease`.

## Štruktúra

```
app/                 # App Router: /login /onboarding /chat /nastavenia /pravne /api/*
components/effects/  # BeamBackground (OGL), BorderBeam, ThinkingOrb
lib/                 # auth (jose JWT) + store (Postgres | lokálny JSON)
android/             # Capacitor shell
.github/workflows/   # APK release CI
sql/schema.sql       # users tabuľka
```

## Právne

`/pravne/ochrana-sukromia`, `/pravne/vseobecne-podmienky`,
`/pravne/zdravotny-disclaimer` — slovenské texty; chat históriu drží len
zariadenie, správy prechádzajú cez server len na vygenerovanie odpovede.
