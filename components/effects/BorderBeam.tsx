import type { ReactNode } from "react";
import { BorderBeam as NpmBorderBeam } from "border-beam";

export default function BorderBeam({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <NpmBorderBeam size="md" colorVariant="colorful" strength={0.7} theme="dark">
      <div
        className={`glass ${className}`}
        style={{ borderRadius: 20 }}
      >
        {children}
      </div>
    </NpmBorderBeam>
  );
}
