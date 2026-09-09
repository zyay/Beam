import { redirect } from "next/navigation";
import { currentUserId } from "@/lib/auth";
import { getUserById } from "@/lib/store";
import PrehladClient from "./PrehladClient";

export default async function PrehladPage() {
  const id = await currentUserId();
  if (!id) redirect("/login?next=/prehled");
  const user = await getUserById(id);
  if (!user) redirect("/login");
  if (!user.profile) redirect("/onboarding");
  return <PrehladClient name={user.name || "kamarát"} />;
}
