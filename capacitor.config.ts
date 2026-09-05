import type { CapacitorConfig } from "@capacitor/cli";

/**
 * The Android app is a native shell around the deployed Vercel site —
 * always up to date, no model or secrets inside the APK.
 * BEAM_SERVER_URL can override the domain at `cap sync` time (used by CI).
 */
const serverUrl =
  process.env.BEAM_SERVER_URL || "https://beam-mental-health.vercel.app";

const config: CapacitorConfig = {
  appId: "com.beammental.app",
  appName: "Beam",
  webDir: "public",
  server: {
    url: serverUrl,
    cleartext: false,
    androidScheme: "https",
  },
  android: {
    backgroundColor: "#060607",
    allowMixedContent: false,
  },
  plugins: {
    SplashScreen: {
      backgroundColor: "#060607",
      showSpinner: false,
      launchAutoHide: true,
      launchShowDuration: 900,
    },
  },
};

export default config;
