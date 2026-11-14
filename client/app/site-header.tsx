import Link from "next/link";

export function SiteHeader() {
  return (
    <header className="site-header">
      <Link href="/" className="brand">
        <span className="brand-mark" aria-hidden="true">H</span>
        Hypertube
      </Link>
      <nav className="account-nav" aria-label="Account">
        <Link href="/sign-in">Sign in</Link>
        <Link href="/register" className="register-link">Register</Link>
      </nav>
    </header>
  );
}
