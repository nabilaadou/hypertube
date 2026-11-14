import Link from "next/link";

export function SiteHeader({ onOpening }: { onOpening?: () => void }) {
  return (
    <header className="site-header">
      <Link
        href="/"
        className="brand"
        onClick={onOpening && ((event) => {
          if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
          event.preventDefault();
          onOpening();
        })}
      >
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
