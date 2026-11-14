import type { Metadata } from "next";
import { AccountEntry } from "../account-entry";

export const metadata: Metadata = { title: "Sign in | Hypertube" };

export default function SignIn() {
  return <AccountEntry mode="sign-in" />;
}
