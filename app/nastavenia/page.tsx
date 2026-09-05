import { redirect } from "next/navigation";
import { currentUserId } from "@/lib/auth";
import { getUserById } from "@/lib/store";
import SettingsClient from "./SettingsClient";

export default async function SettingsPage() {
  const id = await currentUserId();
  if (!id) redirect("/login?next=/nastavenia");
  const user = await getUserById(id);
  if (!user) redirect("/login");

  return (
    <SettingsClient
      email={user.email}
      name={user.name || ""}
      profile={user.profile}
    />
  );
}
