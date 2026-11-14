import Link from "next/link";
import { SiteHeader } from "./site-header";

export function AccountEntry({ mode }: { mode: "sign-in" | "register" }) {
  const isRegistration = mode === "register";
  return (
    <div className="account-page">
      <SiteHeader />
      <main id="main-content" className="account-content">
        <h1 className="display">{isRegistration ? "Register" : "Sign in"}</h1>
        <p>{isRegistration ? "Registration" : "Sign-in"} will be available when accounts launch.</p>
        <Link className="outline-button" href="/">Back to Hypertube</Link>
      </main>
    </div>
  );
}
