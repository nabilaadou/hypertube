import { connection } from "next/server";
import { MovieEdit } from "./components/movie-edit";

async function getApiStatus() {
  const backendUrl = process.env.BACKEND_URL?.trim();

  if (!backendUrl) {
    return "Set BACKEND_URL in client/.env and restart the client.";
  }

  const origin = URL.parse(backendUrl);

  if (!origin || !["http:", "https:"].includes(origin.protocol) || origin.username || origin.password) {
    return "Set BACKEND_URL to a valid HTTP or HTTPS URL without credentials and restart the client.";
  }

  try {
    const response = await fetch(new URL("/api/health", origin), {
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(3000),
    });

    if (response.ok) {
      return "API is online.";
    }
  } catch {
    // Keep configuration values and connection details out of the response and logs.
  }

  return "Could not reach the API. Check that the server is running and BACKEND_URL is correct.";
}

export default async function Home() {
  await connection();
  const status = await getApiStatus();

  return (
    <main className="min-h-svh px-page py-16">
      <h1 className="font-display text-6xl font-bold uppercase tracking-tight">Hypertube</h1>
      <p className="mt-6 text-muted">{status}</p>
      <MovieEdit />
    </main>
  );
}
