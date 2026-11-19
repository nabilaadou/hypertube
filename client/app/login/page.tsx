import type { Metadata } from "next";
import { AuthForm } from "../components/auth-form";

export const metadata: Metadata = { title: "Log in | Hypertube" };

export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const { error } = await searchParams;
  return <AuthForm error={typeof error === "string" ? error : undefined} />;
}
