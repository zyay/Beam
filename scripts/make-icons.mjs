/* Generates Android launcher icons (all densities) from scripts/icon.png.
 * Square keeps the source's rounded corners; round is circular-masked. */
import sharp from "sharp";
import { mkdir } from "fs/promises";

const SRC = "scripts/icon.png";
const DENSITIES = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };

const roundMask = (size) => Buffer.from(
  `<svg width="${size}" height="${size}"><circle cx="${size / 2}" cy="${size / 2}" r="${size / 2}" fill="#fff"/></svg>`
);

for (const [dpi, size] of Object.entries(DENSITIES)) {
  const dir = `android/app/src/main/res/mipmap-${dpi}`;
  await mkdir(dir, { recursive: true });
  await sharp(SRC).resize(size, size, { fit: "cover" }).png().toFile(`${dir}/ic_launcher.png`);
  await sharp(SRC)
    .resize(size, size, { fit: "cover" })
    .composite([{ input: roundMask(size), blend: "dest-in" }])
    .png()
    .toFile(`${dir}/ic_launcher_round.png`);
}
console.log("launcher icons written");
