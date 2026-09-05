"use client";

import { createAvatar } from "@bible-strong/avatar-react";
import "@bible-strong/avatar-react/styles.css";
import definition from "./avatar.avatar.json";

const BeamAvatar = createAvatar(definition);

/**
 * The Beam mascot (exported from Avatar Lab) — a little white blob.
 * Grok Bot style: sits in the header, greets you, and "thinks" while waiting.
 */
export default function Mascot({
  size = 96,
  animation = "idle",
  className = "",
}: {
  size?: number;
  animation?: string;
  className?: string;
}) {
  return (
    <BeamAvatar
      size={size}
      animation={animation as never}
      ariaLabel="Beam maskot"
      className={className}
    />
  );
}
