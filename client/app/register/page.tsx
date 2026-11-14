import type { Metadata } from "next";
import { AccountEntry } from "../account-entry";

export const metadata: Metadata = { title: "Register | Hypertube" };

export default function Register() {
  return <AccountEntry mode="register" />;
}
