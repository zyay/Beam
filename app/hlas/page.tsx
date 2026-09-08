import { redirect } from "next/navigation";
import { currentUserId } from "@/lib/auth";
import { getUserById } from "@/lib/store";
import HlasClient from "./HlasClient";

export const dynamic = "force-dynamic";

export default async function HlasPage() {
  const id = await currentUserId();
  if (!id) redirect("/login?next=/hlas");
  const user = await getUserById(id);
  if (!user) redirect("/login");
  if (!user.profile) redirect("/onboarding");

  return <HlasClient name={user.name || "kamarát"} />;
}
