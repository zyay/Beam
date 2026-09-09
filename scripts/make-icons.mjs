/* Generates the Beam launcher icons from the rendered orb (scripts/orb.png).
 *
 * The orb art is the identity: a white volumetric cloud on the app's ink
 * (#110F08, the default theme background and the splash colour), so the icon
 * reads as a window into the app rather than a sticker on a plate.
 *
 * Outputs:
 *   scripts/icon.png                        — 512px master, orb at 88% of frame
 *   mipmap-<dpi>/ic_launcher.png            — legacy square, full-bleed master
 *   mipmap-<dpi>/ic_launcher_round.png      — legacy circular mask of the master
 *   mipmap-<dpi>/ic_launcher_foreground.png — adaptive foreground layer (108dp),
 *                                             orb inside the 66dp safe zone
 * The adaptive XML layers live in mipmap-anydpi-v26 and reference
 * @color/ic_background for the plate.
 *
 * Run: node scripts/make-icons.mjs   (after scripts/make-orb-icon.mjs)
 */
import sharp from "sharp";
import { mkdir } from "fs/promises";

const ORB = "scripts/orb.png";
const INK = "#110F08";
const MASTER = 512;
const ORB_IN_MASTER = 0.88;

// legacy icon sizes per density
const DENSITIES = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
// adaptive foreground layer is 108dp; the safe zone is the inner 66dp, so the
// orb may span at most ~61% of the layer before a masked launcher crops it
const FG_DENSITIES = { mdpi: 108, hdpi: 162, xhdpi: 216, xxhdpi: 324, xxxhdpi: 432 };
const ORB_IN_LAYER = 0.6;

const roundMask = (size) => Buffer.from(
  `<svg width="${size}" height="${size}"><circle cx="${size / 2}" cy="${size / 2}" r="${size / 2}" fill="#fff"/></svg>`
);

// master: ink plate + orb centred
const orbInMaster = Math.round(MASTER * ORB_IN_MASTER);
const master = await sharp({
  create: { width: MASTER, height: MASTER, channels: 4, background: INK },
})
  .composite([{
    input: await sharp(ORB).resize(orbInMaster, orbInMaster, { fit: "inside" }).png().toBuffer(),
    gravity: "centre",
  }])
  .png()
  .toBuffer();
await sharp(master).toFile("scripts/icon.png");

for (const [dpi, size] of Object.entries(DENSITIES)) {
  const dir = `android/app/src/main/res/mipmap-${dpi}`;
  await mkdir(dir, { recursive: true });
  await sharp(master).resize(size, size, { fit: "cover" }).png().toFile(`${dir}/ic_launcher.png`);
  await sharp(master)
    .resize(size, size, { fit: "cover" })
    .composite([{ input: roundMask(size), blend: "dest-in" }])
    .png()
    .toFile(`${dir}/ic_launcher_round.png`);
}

for (const [dpi, size] of Object.entries(FG_DENSITIES)) {
  const dir = `android/app/src/main/res/mipmap-${dpi}`;
  await mkdir(dir, { recursive: true });
  const orb = Math.round(size * ORB_IN_LAYER);
  await sharp({
    create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
  })
    .composite([{
      input: await sharp(ORB).resize(orb, orb, { fit: "inside" }).png().toBuffer(),
      gravity: "centre",
    }])
    .png()
    .toFile(`${dir}/ic_launcher_foreground.png`);
}

console.log("launcher icons + adaptive foregrounds written");
