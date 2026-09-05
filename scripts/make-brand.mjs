/* Generates brand PNGs (app icon + splash) from the Beam logo SVG. */
import sharp from "sharp";
import { mkdir } from "fs/promises";

const LOGO = `<svg width="512" height="512" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bm" x1="4" y1="26" x2="28" y2="6">
      <stop offset="0" stop-color="#ff8fd6"/>
      <stop offset="0.5" stop-color="#a78bfa"/>
      <stop offset="1" stop-color="#6ee7b7"/>
    </linearGradient>
  </defs>
  <rect width="32" height="32" rx="7" fill="#060607"/>
  <path d="M6 23.5 C 12 23.5, 13.5 8.5, 19.5 8.5 C 24 8.5, 26 11.8, 26 15.4"
        stroke="url(#bm)" stroke-width="3" stroke-linecap="round"/>
  <circle cx="26" cy="15.4" r="2.1" fill="url(#bm)"/>
</svg>`;

const SPLASH = `<svg width="2732" height="2732" viewBox="0 0 2732 2732" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bm" x1="500" y1="2200" x2="2200" y2="500">
      <stop offset="0" stop-color="#ff8fd6"/>
      <stop offset="0.5" stop-color="#a78bfa"/>
      <stop offset="1" stop-color="#6ee7b7"/>
    </linearGradient>
  </defs>
  <rect width="2732" height="2732" fill="#060607"/>
  <g transform="translate(866,866) scale(93.3)">
    <path d="M6 23.5 C 12 23.5, 13.5 8.5, 19.5 8.5 C 24 8.5, 26 11.8, 26 15.4"
          stroke="url(#bm)" stroke-width="3" stroke-linecap="round" fill="none"/>
    <circle cx="26" cy="15.4" r="2.1" fill="url(#bm)"/>
  </g>
</svg>`;

await mkdir("assets", { recursive: true });
await sharp(Buffer.from(LOGO)).resize(1024, 1024).png().toFile("assets/icon.png");
await sharp(Buffer.from(SPLASH)).png().toFile("assets/splash.png");
console.log("assets/icon.png + assets/splash.png written");
