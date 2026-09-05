import type { ReactNode } from "react";

/** Glass card with a light beam running along its border (libraries.dev style). */
export default function BorderBeam({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`beam-border glass ${className}`}
      style={{ borderRadius: 20 }}
    >
      {children}
    </div>
  );
}
