"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

const providers = [
  { name: "GitHub", id: "github" },
  { name: "Google", id: "google_oauth2" },
  { name: "42", id: "marvin" },
];

type ProviderOptions = { csrfToken: string; providers: string[] };

export function AuthForm({ signup = false, error }: { signup?: boolean; error?: string }) {
  const [options, setOptions] = useState<ProviderOptions | null>(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    fetch("/auth/providers", { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error("Could not load sign-in providers");
        setOptions(await response.json());
      })
      .catch(() => {
        if (!controller.signal.aborted) setUnavailable(true);
      });

    return () => controller.abort();
  }, []);

  const action = signup ? "Sign up" : "Log in";
  const fieldClass = "mt-1.5 block h-11 w-full rounded-card border border-paper/20 bg-ink px-3 text-paper placeholder:text-muted";

  return (
    <main className="grid min-h-svh content-center bg-theater px-page py-8 selection:bg-gold selection:text-ink sm:py-12">
      <div className="mx-auto w-full max-w-md">
        <Link href="/" className="mb-6 inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-muted hover:text-paper">
          <span aria-hidden="true">&larr;</span> Back to Hypertube
        </Link>
        <div className="rounded-2xl border border-paper/15 bg-quote p-5 sm:p-8">
          <h1 className="font-display text-5xl leading-none font-bold">{action}</h1>
          {error && (
            <p role="alert" className="mt-4 text-sm text-rose-200">
              {error === "cancelled" ? "Sign-in was cancelled. Choose a provider to try again." : "Sign-in couldn't be completed. Choose a provider to try again."}
            </p>
          )}
          <div className="mt-6 grid gap-2">
            {providers.filter(({ id }) => id !== "marvin" || options?.providers.includes(id)).map(({ name, id }) => (
              <form key={id} method="post" action={`/auth/${id}`}>
                <input type="hidden" name="authenticity_token" value={options?.csrfToken ?? ""} />
                <button type="submit" disabled={!options?.providers.includes(id)} className="min-h-11 w-full cursor-pointer rounded-card bg-paper px-3 py-2.5 text-sm font-bold text-ink hover:bg-white disabled:cursor-default disabled:bg-ink disabled:text-muted">
                  Continue with {name}
                </button>
              </form>
            ))}
            {unavailable || options?.providers.length === 0 ? (
              <p role="status" className="text-sm text-muted">Provider sign-in is unavailable. Try again later.</p>
            ) : !options ? <p role="status" className="sr-only">Loading sign-in providers</p> : null}
          </div>
          <fieldset disabled className="mt-6 grid w-full gap-3 border-t border-paper/15 pt-4 text-sm" aria-describedby="manual-auth-status">
            <legend className="sr-only">{action} with email</legend>
            <p id="manual-auth-status" className="text-muted">Email and password sign-in is unavailable for now.</p>
            {signup && <label>Display name<input type="text" autoComplete="nickname" className={fieldClass} /></label>}
            <label>Email<input type="email" autoComplete="email" className={fieldClass} /></label>
            <label>Password<input type="password" autoComplete={signup ? "new-password" : "current-password"} className={fieldClass} /></label>
          </fieldset>
          <p className="mt-6 text-sm text-muted">
            {signup ? "Already have an account?" : "New to Hypertube?"}{" "}
            <Link href={signup ? "/login" : "/signup"} className="font-bold text-paper underline underline-offset-4 hover:text-white">{signup ? "Log in" : "Sign up"}</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
