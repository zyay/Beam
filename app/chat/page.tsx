import { redirect } from "next/navigation";
import { currentUserId } from "@/lib/auth";
import { getUserById } from "@/lib/store";
import ChatClient from "./ChatClient";

export default async function ChatPage() {
  const id = await currentUserId();
  if (!id) redirect("/login?next=/chat");
  const user = await getUserById(id);
  if (!user) redirect("/login");
  if (!user.profile) redirect("/onboarding");

  return <ChatClient name={user.name || "kamarát"} />;
}
