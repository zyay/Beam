import { redirect } from "next/navigation";
import { currentUserId } from "@/lib/auth";
import { getUserById } from "@/lib/store";

export default async function Home() {
  const id = await currentUserId();
  if (id) {
    const user = await getUserById(id);
    if (user) redirect(user.profile ? "/chat" : "/onboarding");
  }
  redirect("/login");
}
