/* Generates Android launcher icons (all densities) from the Beam logo SVG. */
import sharp from "sharp";
import { mkdir } from "fs/promises";

const LOGO = `<svg width="512" height="512" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bm" x1="4" y1="26" x2="28" y2="6">
      <stop offset="0" stop-color="#ff8fd6"/>
      <stop offset="0.5" stop-color="#a78bfa"/>
      <stop offset="1" stop-color="#6ee7b7"/>
    </linearGradient>
    <clipPath id="round"><rect width="32" height="32" rx="6.4"/></clipPath>
  </defs>
  <g clip-path="url(#round)">
    <rect width="32" height="32" fill="#060607"/>
    <path d="M6 23.5 C 12 23.5, 13.5 8.5, 19.5 8.5 C 24 8.5, 26 11.8, 26 15.4"
          stroke="url(#bm)" stroke-width="3" stroke-linecap="round" fill="none"/>
    <circle cx="26" cy="15.4" r="2.1" fill="url(#bm)"/>
  </g>
</svg>`;

const ROUND = LOGO.replace('rx="6.4"', 'rx="16"');

const DENSITIES = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };

for (const [dpi, size] of Object.entries(DENSITIES)) {
  const dir = `android/app/src/main/res/mipmap-${dpi}`;
  await mkdir(dir, { recursive: true });
  await sharp(Buffer.from(LOGO)).resize(size, size).png().toFile(`${dir}/ic_launcher.png`);
  await sharp(Buffer.from(ROUND)).resize(size, size).png().toFile(`${dir}/ic_launcher_round.png`);
}
console.log("launcher icons written");
