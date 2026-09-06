"use client";

import { useEffect, useRef, useState } from "react";
import { Renderer, Triangle, Program, Mesh } from "ogl";

const FRAG = /* glsl */ `
precision highp float;

uniform float u_time;
uniform vec2 u_res;

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
    mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
    u.y
  );
}

// a soft light beam along a rotated axis
float beam(vec2 uv, float offset, float width, float angle, float speed, float t) {
  float c = cos(angle), s = sin(angle);
  vec2 r = vec2(uv.x * c - uv.y * s, uv.x * s + uv.y * c);
  float center = offset + sin(t * speed) * 0.18;
  float d = abs(r.y - center);
  float core = width / (d + width * 0.35);
  // fade the ends
  float fade = smoothstep(0.0, 0.35, r.x + 0.5) * smoothstep(1.4, 0.55, r.x + 0.5);
  return core * fade;
}

void main() {
  vec2 uv = gl_FragCoord.xy / u_res.xy;
  vec2 p = uv; p.x *= u_res.x / u_res.y;
  float t = u_time;

  vec3 col = vec3(0.024, 0.024, 0.028); // #060607 base

  float b1 = beam(p, 0.62, 0.050, 0.50, 0.11, t);
  float b2 = beam(p, 0.40, 0.030, 0.50, 0.07, t * 1.3 + 2.0);
  float b3 = beam(p, 0.78, 0.022, 0.50, 0.09, t * 0.8 + 4.0);

  float beams = b1 * 0.38 + b2 * 0.42 + b3 * 0.32;
  col += vec3(0.92, 0.92, 0.93) * beams; // monochrome mist

  // gentle vertical falloff so the bottom stays darkest
  col *= 0.65 + 0.35 * smoothstep(0.0, 0.9, uv.y);

  // darken the center column so UI text stays readable
  float cx = smoothstep(0.45, 0.0, abs(uv.x - 0.5) );
  col *= 1.0 - 0.45 * cx;

  // film grain
  col += (hash(gl_FragCoord.xy + fract(t)) - 0.5) * 0.028;

  gl_FragColor = vec4(col, 1.0);
}
`;

export default function BeamBackground({ className = "" }: { className?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) {
      setFailed(true);
      return;
    }
    const host = ref.current;
    if (!host) return;

    let renderer: Renderer;
    try {
      renderer = new Renderer({ alpha: false, antialias: false, dpr: Math.min(window.devicePixelRatio, 1.5) });
    } catch {
      setFailed(true);
      return;
    }
    const gl = renderer.gl;
    gl.clearColor(0.024, 0.024, 0.028, 1);
    host.appendChild(gl.canvas);

    const geometry = new Triangle(gl);
    const program = new Program(gl, {
      vertex: /* glsl */ `
        attribute vec2 uv;
        attribute vec2 position;
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = vec4(position, 0.0, 1.0);
        }
      `,
      fragment: FRAG,
      uniforms: {
        u_time: { value: 0 },
        u_res: { value: [gl.canvas.width, gl.canvas.height] },
      },
    });
    const mesh = new Mesh(gl, { geometry, program });

    let raf = 0;

    // A dropped GL context (mobile GPU eviction, suspended tab) would otherwise
    // leave a frozen canvas on screen. Removing it here matters: flipping
    // `failed` re-renders without unmounting, so the cleanup below never runs.
    const onContextLost = (e: Event) => {
      e.preventDefault();
      cancelAnimationFrame(raf);
      gl.canvas.remove();
      setFailed(true);
    };
    gl.canvas.addEventListener("webglcontextlost", onContextLost);

    const resize = () => {
      renderer.setSize(host.offsetWidth, host.offsetHeight);
      program.uniforms.u_res.value = [gl.canvas.width, gl.canvas.height];
    };
    resize();
    window.addEventListener("resize", resize);

    const start = performance.now();
    const loop = () => {
      program.uniforms.u_time.value = (performance.now() - start) / 1000;
      renderer.render({ scene: mesh });
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", resize);
      gl.canvas.removeEventListener("webglcontextlost", onContextLost);
      gl.canvas.remove();
    };
  }, []);

  if (failed) {
    // static fallback: a monochrome echo of the shader
    return (
      <div
        ref={ref}
        aria-hidden
        className={`pointer-events-none absolute inset-0 -z-10 ${className}`}
        style={{
          background:
            "radial-gradient(120% 60% at 70% 10%, rgba(235,235,238,0.14), transparent 60%)," +
            "radial-gradient(90% 50% at 20% 30%, rgba(235,235,238,0.08), transparent 55%)," +
            "radial-gradient(80% 50% at 50% 90%, rgba(235,235,238,0.05), transparent 60%)," +
            "#060607",
        }}
      />
    );
  }

  return (
    <div
      ref={ref}
      aria-hidden
      className={`pointer-events-none absolute inset-0 -z-10 [&_canvas]:h-full [&_canvas]:w-full ${className}`}
    />
  );
}
