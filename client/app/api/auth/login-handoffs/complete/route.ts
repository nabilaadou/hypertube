import { type NextRequest, NextResponse } from "next/server";

const completionPath = "/api/auth/login-handoffs/complete";
const codeName = "hypertube_login_code";
const proofName = "hypertube_login_proof";
const codeFormat = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const proofFormat = /^[0-9a-f]{64}$/;

export async function GET(request: NextRequest) {
  const code = request.cookies.get(codeName)?.value;
  const proof = request.cookies.get(proofName)?.value;
  let completed: Response | undefined;

  if (code && proof && codeFormat.test(code) && proofFormat.test(proof)) {
    const backend = process.env.BACKEND_URL || "http://localhost:8080";
    try {
      completed = await fetch(new URL(completionPath, backend), {
        method: "POST",
        body: new URLSearchParams({ code }),
        headers: { Cookie: `${proofName}=${proof}` },
        cache: "no-store",
        redirect: "error",
        signal: AbortSignal.timeout(5000),
      });
    } catch {
      // Route upstream failures back to the sign-in form.
    }
  }

  const response = new NextResponse(null, {
    status: 303,
    headers: {
      Location: completed?.ok ? "/" : "/login?error=failed",
      "Cache-Control": "no-store",
      "Referrer-Policy": "no-referrer",
    },
  });

  for (const name of [codeName, proofName]) {
    response.cookies.set(name, "", { path: completionPath, httpOnly: true, maxAge: 0 });
  }
  if (completed?.ok) {
    for (const cookie of completed.headers.getSetCookie()) {
      if (!cookie.startsWith(`${proofName}=`)) response.headers.append("Set-Cookie", cookie);
    }
  }
  return response;
}
