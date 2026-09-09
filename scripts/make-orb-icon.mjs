/* Renders the Beam orb — the SHDR-21 volumetric cloud — to scripts/orb.png.
 *
 * This is a straight JS port of the AGSL march in
 * android/app/src/main/java/com/beammental/app/ui/effects/Orb.kt, so the
 * launcher icon and the in-app orb are literally the same image, not two
 * approximations of one idea. It renders once at high resolution with a
 * transparent background and frame-filling sphere; make-icons.mjs then scales
 * and pads that art for every icon slot.
 *
 * One march, ~10s. Run: node scripts/make-orb-icon.mjs
 */
import sharp from "sharp";

const RES = 512;
const STEPS = 30;
const LIGHT_STEPS = 2;

// OrbState.Idle palette from Orb.kt
const LIGHT = [1.0, 0.953, 0.894]; // #FFF3E4
const SHADOW = [0.549, 0.58, 0.78]; // #8C94C7

// The shadow colour is an ambient floor for crevices, not a second light: at
// lift ~1 the shaded half saturates like the lit half and the whole cloud
// flattens to one pale tone. Density and power stay low enough that the
// interior never saturates — that is what keeps the lobe shading visible.
const POWER = 4.2;
const EXPOSURE = 1.2;
const AMBIENT = 0.10;
const SHADOW_LIFT = 0.26;
const DENSITY = 1.2;
const OCTAVES = 5;
const TIME = 21; // clock value — picks which pose of the cloud we freeze

const RADIUS = 2.0;
const CAM_DIST = 4.4;
const FOCAL = 1.8;
const ANISO = 0.5;
const CHURN = 0.3;
const NOISE_SCALE = 3.2;
const ABSORB = 1.4;
const SHADOW_ABSORB = 1.6;
// The surface radius is displaced by the noise (cumulus lobes); EDGE is the
// width of the soft skin between empty air and full density.
const SURF0 = 0.72;
const SURF1 = 0.32;
const EDGE = 0.55;

const smoothstep = (e0, e1, x) => {
  const t = Math.min(1, Math.max(0, (x - e0) / (e1 - e0)));
  return t * t * (3 - 2 * t);
};

const norm = (v) => {
  const l = Math.hypot(v[0], v[1], v[2]) || 1;
  return [v[0] / l, v[1] / l, v[2] / l];
};

const phaseHG = (c, g) => {
  const g2 = g * g;
  return (1 - g2) / Math.pow(Math.max(1 + g2 - 2 * g * c, 0.0001), 1.5);
};

function density(x, y, z) {
  const r = Math.hypot(x, y, z);

  let qx = x * NOISE_SCALE;
  let qy = y * NOISE_SCALE;
  let qz = z * NOISE_SCALE;
  let f = 1;
  const ph = TIME * CHURN;
  for (let k = 0; k < OCTAVES; k++) {
    // q += cos(q.yzx * f + ph) / f — the RHS uses the pre-update q
    const nx = qx + Math.cos(qy * f + ph) / f;
    const ny = qy + Math.cos(qz * f + ph) / f;
    const nz = qz + Math.cos(qx * f + ph) / f;
    qx = nx;
    qy = ny;
    qz = nz;
    f *= 1.8;
  }
  const n = ((Math.sin(qx) + Math.sin(qy) + Math.sin(qz)) / 3) * 0.5 + 0.5;
  const surf = RADIUS * (SURF0 + SURF1 * n);
  const d = surf - r;
  if (d <= 0) return 0;
  return smoothstep(0, EDGE, d) * DENSITY;
}

// Light stays mostly frontal with a slight sway, matching Orb.kt.
const L = norm([Math.cos(TIME * 0.12) * 0.30, 0.38, Math.sin(TIME * 0.12) * 0.18 + 0.88]);

const buf = Buffer.alloc(RES * RES * 4);
const tStart = CAM_DIST - RADIUS;
const dt = (2 * RADIUS) / STEPS;
const lstep = RADIUS / LIGHT_STEPS;

for (let py = 0; py < RES; py++) {
  // y is flipped: the image buffer is top-down, the shader's fragCoord is up
  const uvy = -((2 * (py + 0.5) - RES) / RES);
  for (let px = 0; px < RES; px++) {
    const uvx = (2 * (px + 0.5) - RES) / RES;
    const rd = norm([uvx, uvy, FOCAL]);
    const phase = phaseHG(rd[0] * L[0] + rd[1] * L[1] + rd[2] * L[2], ANISO);

    let T = 1;
    let sr = 0;
    let sg = 0;
    let sb = 0;

    for (let i = 0; i < STEPS; i++) {
      const tt = tStart + (i + 0.5) * dt;
      const x = rd[0] * tt;
      const y = rd[1] * tt;
      const z = -CAM_DIST + rd[2] * tt;
      const dn = density(x, y, z);
      if (dn <= 0.001) continue;

      // short march toward the light for self-shadowing
      let shadow = 1;
      for (let k = 1; k <= LIGHT_STEPS; k++) {
        const d = (k - 0.5) * lstep;
        shadow *= Math.exp(-density(x + L[0] * d, y + L[1] * d, z + L[2] * d) * lstep * SHADOW_ABSORB);
      }

      // shadow appears once, inside the mix — multiplying by it again would
      // scale the cool end to zero and flatten the cloud to monochrome beige
      const cr = SHADOW[0] * SHADOW_LIFT + (LIGHT[0] - SHADOW[0] * SHADOW_LIFT) * shadow;
      const cg = SHADOW[1] * SHADOW_LIFT + (LIGHT[1] - SHADOW[1] * SHADOW_LIFT) * shadow;
      const cb = SHADOW[2] * SHADOW_LIFT + (LIGHT[2] - SHADOW[2] * SHADOW_LIFT) * shadow;
      const w = T * dn * dt * phase * POWER;
      sr += cr * w;
      sg += cg * w;
      sb += cb * w;

      T *= Math.exp(-dn * dt * ABSORB);
      if (T < 0.02) break;
    }

    const body = 1 - T;
    sr += SHADOW[0] * body * AMBIENT;
    sg += SHADOW[1] * body * AMBIENT;
    sb += SHADOW[2] * body * AMBIENT;

    // exponential tone map. PNG carries straight alpha, so the colour channels
    // stay un-premultiplied here — unlike the AGSL version, where Skia expects
    // premultiplied output.
    const a = Math.min(1, Math.max(0, body * 1.5));
    const o = (py * RES + px) * 4;
    buf[o] = Math.round(Math.min(1, 1 - Math.exp(-sr * EXPOSURE)) * 255);
    buf[o + 1] = Math.round(Math.min(1, 1 - Math.exp(-sg * EXPOSURE)) * 255);
    buf[o + 2] = Math.round(Math.min(1, 1 - Math.exp(-sb * EXPOSURE)) * 255);
    buf[o + 3] = Math.round(a * 255);
  }
}

await sharp(buf, { raw: { width: RES, height: RES, channels: 4 } })
  .png()
  .toFile("scripts/orb.png");

console.log(`scripts/orb.png written (${RES}x${RES}, transparent)`);
