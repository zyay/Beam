"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { BorderBeam } from "border-beam";
import { Shdr21 } from "@/components/ui/shdr-21";

/** Slovak warm-friend system instruction. Mirrors the Android LiveVoice one
 *  exactly so the model speaks the same way on both clients. */
const SYSTEM_INSTRUCTION =
  "Si Beam — teplý, pokojný hlasový spoločník na rozhovory o duševnej pohode. " +
  "Rozprávaš plynulú slovenčinu, krátko (jednu až tri vety), prirodzene a s teplom, ako starý priateľ. " +
  "Aktívne počúvaš, validuješ pocity a opatrne sa pýtaš jednu dopĺňujúcu otázku. " +
  "Nikdy nediagnostikuješ a nedávaš lekárske rady. " +
  "Ak človek zaznie niečo veľmi vážne — smútok, sebapoškodenie, myšlienky na smrť — spomenieš s láskou Linku krízy 0800 900 900, " +
  "IPčko 0800 500 500 a povzbudíš ho osloviť blízkeho človeka. Tichá chvíľa je v poriadku, neniekaj sa.\n\n" +
  "Keď sa hovor práve pripojil, vždy pozdrav prvá — srdečne, ale stručne. Napríklad: " +
  "„Ahoj, som Beam. Rád ťa počujem. Ako sa dnes máš?“";

type Phase = "idle" | "connecting" | "listening" | "speaking" | "ended" | "failed";

const SAMPLE_IN = 16000;
const SAMPLE_OUT = 24000;
const FRAME_MS = 80;
const FRAME_IN = (SAMPLE_IN * FRAME_MS) / 1000; // 1280 samples per 80ms frame

const MODEL_ID = "models/gemini-2.5-flash-native-audio-latest";

const WS_URL =
  "wss://generativelanguage.googleapis.com/ws/" +
  "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";

/** Crisis patterns mirror the server config so we can short-circuit the
 *  conversation before it goes anywhere dangerous. (Server is the source of
 *  truth; this is a client-side safety net that just bails to a static card.) */
const CRISIS_HINTS = [
  /chcem\s+sa\s+zabit/i,
  /chcem\s+umrieť/i,
  /nechcem\s+žiť/i,
  /samovražd/i,
  /zabiť\s+sa/i,
];

export default function HlasClient({ name }: { name: string }) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [reason, setReason] = useState<string | null>(null);
  const [modelCaption, setModelCaption] = useState("");
  const [userCaption, setUserCaption] = useState("");
  const [crisis, setCrisis] = useState(false);
  const [inputVol, setInputVol] = useState(0);
  const [outputVol, setOutputVol] = useState(0);
  const [muted, setMuted] = useState(false);
  const [micReady, setMicReady] = useState(false);
  const [micError, setMicError] = useState<string | null>(null);

  // Refs that need to survive re-renders without re-creating the connection.
  const wsRef = useRef<WebSocket | null>(null);
  const micStreamRef = useRef<MediaStream | null>(null);
  const micSourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const micCtxRef = useRef<AudioContext | null>(null);
  const playCtxRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const nextPlayTimeRef = useRef(0);
  const startedAtRef = useRef(0);
  const userTextAtRef = useRef(0);
  const newModelTurnRef = useRef(true);
  const inputAnalyserRef = useRef<AnalyserNode | null>(null);
  const outputAnalyserRef = useRef<AnalyserNode | null>(null);
  const cancelledRef = useRef(false);
  const phaseRef = useRef<Phase>("idle");

  /* ---- volume polling drives the orb's input/output signals ---- */
  useEffect(() => {
    phaseRef.current = phase;
    if (phase === "idle" || phase === "ended" || phase === "failed") {
      setInputVol(0);
      setOutputVol(0);
      return;
    }
    let raf = 0;
    const tick = () => {
      const i = computeRms(inputAnalyserRef.current);
      const o = computeRms(outputAnalyserRef.current);
      setInputVol(i);
      setOutputVol(o);
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [phase]);

  /* ---- tear down everything on unmount ---- */
  useEffect(() => {
    return () => endCall();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* ---- mic setup: we open the stream on first interaction (browsers
          refuse to give you a mic without a user gesture). ---- */
  const ensureMic = useCallback(async () => {
    if (micStreamRef.current) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      micStreamRef.current = stream;
      // AudioContext that we use to (a) compute the input RMS and (b) feed a
      // ScriptProcessor that pumps 16 kHz PCM frames out the websocket.
      const ctx = new (window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)({
        sampleRate: SAMPLE_IN,
        latencyHint: "interactive",
      });
      micCtxRef.current = ctx;
      const source = ctx.createMediaStreamSource(stream);
      micSourceRef.current = source;
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 1024;
      source.connect(analyser);
      inputAnalyserRef.current = analyser;
      setMicReady(true);
      return { ctx, source };
    } catch (err) {
      setMicError(
        err instanceof Error
          ? `Mikrofón nie je dostupný: ${err.message}`
          : "Mikrofón nie je dostupný.",
      );
      setPhase("failed");
      return null;
    }
  }, []);

  /* ---- play context (24 kHz) ---- */
  const ensurePlayCtx = useCallback(() => {
    if (playCtxRef.current) return playCtxRef.current;
    const ctx = new (window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)({
      sampleRate: SAMPLE_OUT,
        latencyHint: "interactive",
      });
    playCtxRef.current = ctx;
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 1024;
    // Analyser hangs off the destination so it sees whatever we schedule.
    analyser.connect(ctx.destination);
    outputAnalyserRef.current = analyser;
    return ctx;
  }, []);

  /* ---- enqueue base64 24 kHz mono PCM for playback ---- */
  const playPcm = useCallback(
    (b64: string) => {
      const ctx = ensurePlayCtx();
      if (ctx.state === "suspended") void ctx.resume();
      const bytes = base64ToBytes(b64);
      if (bytes.byteLength === 0) return;
      // bytes is little-endian 16-bit PCM
      const i16 = new Int16Array(bytes.buffer, bytes.byteOffset, bytes.byteLength / 2);
      const f32 = new Float32Array(i16.length);
      for (let i = 0; i < i16.length; i++) f32[i] = i16[i] / 32768;
      const buf = ctx.createBuffer(1, f32.length, SAMPLE_OUT);
      buf.copyToChannel(f32, 0);
      const src = ctx.createBufferSource();
      src.buffer = buf;
      if (outputAnalyserRef.current) src.connect(outputAnalyserRef.current);
      else src.connect(ctx.destination);
      const now = ctx.currentTime;
      const startAt = Math.max(now + 0.02, nextPlayTimeRef.current);
      src.start(startAt);
      nextPlayTimeRef.current = startAt + buf.duration;
    },
    [ensurePlayCtx],
  );

  /* ---- open the WebSocket and start the conversation ---- */
  const startCall = useCallback(async () => {
    setPhase("connecting");
    setReason(null);
    setModelCaption("");
    setUserCaption("");
    setCrisis(false);
    cancelledRef.current = false;
    startedAtRef.current = Date.now();
    newModelTurnRef.current = true;

    // 1 · mic — must come from a user gesture.
    const mic = await ensureMic();
    if (!mic) return;
    if (mic.ctx.state === "suspended") await mic.ctx.resume();

    // 2 · grab the Gemini Live key from our own server (it sits in env so
    //    we never ship it in the bundle).
    const tokenRes = await fetch("/api/live-voice/token", { cache: "no-store" });
    if (!tokenRes.ok) {
      const body = await tokenRes.json().catch(() => ({}));
      setReason(body.error || "Hlasový režim nie je dostupný.");
      setPhase("failed");
      return;
    }
    const { key } = (await tokenRes.json()) as { key: string };
    if (!key) {
      setReason("Server nevrátil kľúč.");
      setPhase("failed");
      return;
    }

    // 3 · websocket
    const ws = new WebSocket(`${WS_URL}?key=${encodeURIComponent(key)}`);
    wsRef.current = ws;

    ws.onopen = () => {
      // setup frame — same shape the Android app sends.
      const setup = {
        setup: {
          model: MODEL_ID,
          generation_config: {
            response_modalities: ["AUDIO"],
            speech_config: {
              voice_config: { prebuilt_voice_config: { voice_name: "Kore" } },
            },
            // skip the model's default 10-13s of "thoughts" before first audio
            thinking_config: { thinking_budget: 0 },
          },
          system_instruction: {
            parts: [{ text: SYSTEM_INSTRUCTION }],
          },
          output_audio_transcription: {},
          input_audio_transcription: {},
        },
      };
      ws.send(JSON.stringify(setup));
    };

    ws.onmessage = (ev) => {
      if (cancelledRef.current) return;
      let root: Record<string, unknown> | null = null;
      try {
        root = JSON.parse(typeof ev.data === "string" ? ev.data : "") as Record<string, unknown>;
      } catch {
        return;
      }
      if (!root) return;
      if ("setupComplete" in root) {
        // push a tiny user turn so the model greets instead of waiting silent
        ws.send(
          JSON.stringify({
            client_content: {
              turns: [{ role: "user", parts: [{ text: "Ahoj" }] }],
              turn_complete: true,
            },
          }),
        );
        setPhase("listening");
        startMicPump(ws);
        return;
      }
      const sc = (root.serverContent as Record<string, unknown> | undefined) ?? null;
      if (!sc) return;
      const parts = ((sc.modelTurn as Record<string, unknown> | undefined)?.parts ??
        []) as Array<Record<string, unknown>>;
      for (const part of parts) {
        const inline = (part.inlineData as Record<string, unknown> | undefined) ?? null;
        const data = inline ? (inline.data as string | undefined) : undefined;
        if (data) {
          setPhase("speaking");
          playPcm(data);
        }
      }
      const ot = (sc.outputTranscription as Record<string, unknown> | undefined)?.text as
        | string
        | undefined;
      if (ot) {
        if (newModelTurnRef.current) {
          setModelCaption("");
          newModelTurnRef.current = false;
        }
        setModelCaption((prev) => prev + ot);
      }
      const it = (sc.inputTranscription as Record<string, unknown> | undefined)?.text as
        | string
        | undefined;
      if (it) onUserCaption(it);
      if (sc.turnComplete) {
        if (!cancelledRef.current) setPhase("listening");
        newModelTurnRef.current = true;
      }
      if (sc.interrupted) {
        // barge-in: drop everything we had queued for playback
        nextPlayTimeRef.current = 0;
        newModelTurnRef.current = true;
        setPhase("listening");
      }
    };

    ws.onerror = () => {
      if (cancelledRef.current) return;
      setReason("Nepodarilo sa pripojiť k hlasovému serveru.");
      setPhase("failed");
    };
    ws.onclose = () => {
      if (cancelledRef.current) return;
      const p = phaseRef.current;
      if (p === "speaking" || p === "listening" || p === "connecting") {
        setPhase("ended");
      }
    };
  }, [ensureMic, phase, playPcm]);

  /* ---- forward 80 ms frames of mic audio to the WebSocket ---- */
  const startMicPump = useCallback((ws: WebSocket) => {
    const ctx = micCtxRef.current;
    const source = micSourceRef.current;
    if (!ctx || !source) return;
    if (processorRef.current) return;
    const processor = ctx.createScriptProcessor(2048, 1, 1);
    processorRef.current = processor;
    // ScriptProcessor only fires when it's connected to the destination, so
    // we route through a zero-gain sink. The user's mic never echoes back.
    const sink = ctx.createGain();
    sink.gain.value = 0;
    source.connect(processor);
    processor.connect(sink);
    sink.connect(ctx.destination);

    let pcmBuf: Int16Array = new Int16Array(0);

    processor.onaudioprocess = (e) => {
      if (cancelledRef.current) return;
      if (ws.readyState !== WebSocket.OPEN) return;
      const channel = e.inputBuffer.getChannelData(0);
      // Resample to 16 kHz if the browser gave us a different rate.
      const ratio = ctx.sampleRate / SAMPLE_IN;
      const outLen = Math.floor(channel.length / ratio);
      const out = new Int16Array(outLen);
      for (let i = 0; i < outLen; i++) {
        const f = channel[Math.floor(i * ratio)] ?? 0;
        out[i] = Math.max(-32768, Math.min(32767, Math.round(f * 32767)));
      }
      // accumulate and ship 80 ms chunks
      const next = new Int16Array(pcmBuf.length + out.length);
      next.set(pcmBuf);
      next.set(out, pcmBuf.length);
      pcmBuf = next;
      while (pcmBuf.length >= FRAME_IN) {
        if (muted) {
          pcmBuf = pcmBuf.slice(FRAME_IN);
          continue;
        }
        const frame = pcmBuf.slice(0, FRAME_IN);
        pcmBuf = pcmBuf.slice(FRAME_IN);
        const b64 = int16ToBase64(frame);
        ws.send(
          JSON.stringify({
            realtime_input: {
              media_chunks: [{ mime_type: `audio/pcm;rate=${SAMPLE_IN}`, data: b64 }],
            },
          }),
        );
      }
    };
  }, [muted]);

  /* ---- captions + crisis guard ---- */
  const onUserCaption = useCallback((t: string) => {
    const now = Date.now();
    if (now - userTextAtRef.current > 3200) setUserCaption("");
    userTextAtRef.current = now;
    setUserCaption((prev) => prev + t);
    if (CRISIS_HINTS.some((rx) => rx.test(t))) setCrisis(true);
  }, []);

  /* ---- hangup ---- */
  const endCall = useCallback(() => {
    cancelledRef.current = true;
    if (wsRef.current) {
      try {
        wsRef.current.close(1000, "user ended");
      } catch {
        /* noop */
      }
      wsRef.current = null;
    }
    if (processorRef.current) {
      try {
        processorRef.current.disconnect();
      } catch {
        /* noop */
      }
      processorRef.current = null;
    }
    if (micStreamRef.current) {
      micStreamRef.current.getTracks().forEach((t) => t.stop());
      micStreamRef.current = null;
    }
    if (micCtxRef.current && micCtxRef.current.state !== "closed") {
      micCtxRef.current.close().catch(() => {});
    }
    if (playCtxRef.current && playCtxRef.current.state !== "closed") {
      playCtxRef.current.close().catch(() => {});
    }
    micCtxRef.current = null;
    playCtxRef.current = null;
    inputAnalyserRef.current = null;
    outputAnalyserRef.current = null;
    nextPlayTimeRef.current = 0;
    setMicReady(false);
    if (phase === "connecting" || phase === "listening" || phase === "speaking") {
      setPhase("ended");
    }
  }, [phase]);

  /* ---- map our state to the orb's three modes ---- */
  const orbState: "idle" | "thinking" | "speaking" =
    phase === "speaking"
      ? "speaking"
      : phase === "connecting" || phase === "listening"
        ? "thinking"
        : "idle";

  const orbWrapper: "none" | "ring" | "glass" | "dotted" | "ticks" | "reticle" | "grid" | "halftone" | "scanlines" =
    phase === "speaking" ? "ring" : phase === "listening" ? "dotted" : "none";

  return (
    <main className="relative min-h-dvh w-full overflow-hidden bg-ink text-mist">
      {/* faint mesh background — same tokens as the rest of the app, just dialed down */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse at 20% 20%, rgba(167,139,250,0.10), transparent 55%), radial-gradient(ellipse at 80% 80%, rgba(110,231,183,0.08), transparent 55%), radial-gradient(ellipse at 60% 30%, rgba(255,143,214,0.07), transparent 60%)",
        }}
      />

      {/* top bar */}
      <header className="relative z-10 flex items-center justify-between px-6 pt-6 sm:px-10">
        <div className="flex items-center gap-2 text-xs text-fog">
          <span
            className={
              "inline-block size-2 rounded-full " +
              (phase === "speaking"
                ? "bg-emerald-400 animate-pulse"
                : phase === "listening"
                  ? "bg-amber-300 animate-pulse"
                  : "bg-fog/40")
            }
          />
          <span>{phaseLabel(phase)}</span>
        </div>
        <span className="text-sm text-fog">{name}</span>
      </header>

      {/* main stage */}
      <section className="relative z-10 flex min-h-[calc(100dvh-100px)] flex-col items-center justify-center gap-10 px-6 py-10">
        <div className="text-center">
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">
            {phase === "speaking"
              ? "Beam rozpráva…"
              : phase === "listening"
                ? "Počúvam ťa"
                : phase === "connecting"
                  ? "Pripájam sa…"
                  : "Hlasový hovor"}
          </h1>
          <p className="mt-2 text-sm text-fog">
            {phase === "idle"
              ? "Klepni na mikrofón a začni sa rozprávať. Beam pozdraví prvý."
              : phase === "ended"
                ? "Hovor sa skončil."
                : phase === "failed"
                  ? (reason ?? "Niečo sa pokazilo.")
                  : "Môžeš hovoriť kedykoľvek — Beam ťa pustí k slovu."}
          </p>
        </div>

        <Shdr21
          size={300}
          state={orbState}
          volumes={{ input: inputVol, output: outputVol }}
          wrapper={orbWrapper}
          wrapperColor="rgba(232,232,236,0.55)"
          ariaLabel="Beam hlasový stav"
        />

        {/* captions */}
        <div className="flex w-full max-w-xl flex-col gap-2 text-sm">
          {modelCaption && (
            <BorderBeam size="md" colorVariant="colorful" strength={0.7} theme="dark">
            <div className="rounded-2xl border border-line/60 bg-card/70 px-4 py-3 text-mist">
              <div className="mb-1 text-[10px] uppercase tracking-widest text-fog">Beam</div>
              {modelCaption}
            </div>
            </BorderBeam>
          )}
          {userCaption && (
            <BorderBeam size="md" colorVariant="colorful" strength={0.7} theme="dark">
            <div className="rounded-2xl border border-line/60 bg-card/40 px-4 py-3 text-right text-fog">
              <div className="mb-1 text-[10px] uppercase tracking-widest">Ty</div>
              {userCaption}
            </div>
            </BorderBeam>
          )}
        </div>

        {/* controls */}
        <div className="flex items-center gap-4">
          {(phase === "idle" || phase === "ended" || phase === "failed") && (
            <button
              type="button"
              onClick={startCall}
              className="rounded-full bg-mist px-7 py-4 text-sm font-medium text-ink transition-transform hover:scale-[1.03] active:scale-[0.98]"
            >
              {phase === "failed" ? "Skúsiť znova" : "Začať hovor"}
            </button>
          )}
          {phase !== "idle" && phase !== "ended" && phase !== "failed" && (
            <>
              <button
                type="button"
                onClick={() => setMuted((m) => !m)}
                className="rounded-full border border-line bg-card px-5 py-3 text-sm text-mist transition-colors hover:bg-card/70"
                title={muted ? "Zapnúť mikrofón" : "Stlmiť mikrofón"}
              >
                {muted ? "Zapnúť mik" : "Stlmiť"}
              </button>
              <button
                type="button"
                onClick={endCall}
                className="rounded-full border border-rose-400/40 bg-rose-500/10 px-5 py-3 text-sm text-rose-200 transition-colors hover:bg-rose-500/20"
              >
                Ukončiť
              </button>
            </>
          )}
        </div>

        {micError && <p className="text-xs text-rose-300">{micError}</p>}
      </section>

      {/* crisis card — always on top of the call UI when the user mentions
          something dangerous, with hotline numbers front-and-centre. */}
      {crisis && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/85 p-6 backdrop-blur-md">
          <BorderBeam size="md" colorVariant="colorful" strength={0.7} theme="dark">
          <div className="w-full max-w-md rounded-3xl border border-rose-400/30 bg-ink-2 p-7 shadow-2xl">
            <h2 className="text-xl font-semibold text-mist">Si tu pre seba. Nie si na to sám/sama.</h2>
            <p className="mt-3 text-sm text-fog">
              Ak sa cítiš na dne, kľudne sa obráť na niekoho — anonymne a zadarmo, 24/7.
            </p>
            <ul className="mt-5 space-y-2 text-sm">
              <li>
                <a className="text-mist underline underline-offset-2" href="tel:0800900900">
                  0800 900 900
                </a>{" "}
                <span className="text-fog">— Linka krízy (nonstop)</span>
              </li>
              <li>
                <a className="text-mist underline underline-offset-2" href="tel:0800500500">
                  0800 500 500
                </a>{" "}
                <span className="text-fog">— IPčko (nonstop)</span>
              </li>
              <li>
                <a className="text-mist underline underline-offset-2" href="tel:112">
                  112
                </a>{" "}
                <span className="text-fog">— Tiesňové volanie</span>
              </li>
            </ul>
            <button
              type="button"
              onClick={() => {
                setCrisis(false);
                endCall();
              }}
              className="mt-6 w-full rounded-2xl border border-line bg-card py-3 text-sm text-mist transition-colors hover:bg-card/70"
            >
              Zavrieť
            </button>
          </div>
          </BorderBeam>
        </div>
      )}
    </main>
  );
}

function phaseLabel(p: Phase): string {
  switch (p) {
    case "idle":
      return "Neaktívne";
    case "connecting":
      return "Pripájam sa";
    case "listening":
      return "Počúvam";
    case "speaking":
      return "Hovorím";
    case "ended":
      return "Ukončené";
    case "failed":
      return "Zlyhanie";
  }
}

/* ---- helpers ---- */

function computeRms(node: AnalyserNode | null): number {
  if (!node) return 0;
  const buf = new Float32Array(node.fftSize);
  // AnalyserNode.getFloatTimeDomainData exists on modern browsers.
  type MaybeHas = { getFloatTimeDomainData?: (b: Float32Array) => void; getByteTimeDomainData?: (b: Uint8Array) => void };
  const n = node as unknown as MaybeHas;
  if (n.getFloatTimeDomainData) {
    n.getFloatTimeDomainData(buf);
  } else if (n.getByteTimeDomainData) {
    const u8 = new Uint8Array(node.fftSize);
    n.getByteTimeDomainData(u8);
    for (let i = 0; i < buf.length; i++) buf[i] = (u8[i] - 128) / 128;
  } else {
    return 0;
  }
  let sum = 0;
  for (let i = 0; i < buf.length; i++) sum += buf[i] * buf[i];
  const rms = Math.sqrt(sum / buf.length);
  return Math.min(1, rms * 4);
}

function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function int16ToBase64(i16: Int16Array): string {
  // little-endian, no wrap
  const bytes = new Uint8Array(i16.length * 2);
  const view = new DataView(bytes.buffer);
  for (let i = 0; i < i16.length; i++) view.setInt16(i * 2, i16[i], true);
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}
