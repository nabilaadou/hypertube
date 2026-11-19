"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

export function AccountNav() {
  const [account, setAccount] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    fetch("/api/auth/me", { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        if (response.ok) {
          const { displayName, email } = await response.json();
          setAccount(displayName || email || "Signed in");
        }
      })
      .catch(() => {});

    return () => controller.abort();
  }, []);

  return (
    <nav aria-label="Account" className="flex min-w-0 items-center gap-1.5 text-sm font-semibold whitespace-nowrap sm:gap-2">
      {account ? (
        <span title={account} className="max-w-28 truncate rounded-card bg-ink/90 px-3 py-2 sm:max-w-48">{account}</span>
      ) : (
        <>
          <Link href="/login" className="flex min-h-11 items-center rounded-card bg-ink/90 px-2.5 hover:bg-quote sm:px-3">Log in</Link>
          <Link href="/signup" className="flex min-h-11 items-center rounded-card bg-paper px-2.5 text-ink hover:bg-white sm:px-3">Sign up</Link>
        </>
      )}
    </nav>
  );
}
