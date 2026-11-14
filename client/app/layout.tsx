import "@fontsource/barlow-condensed/latin-700.css";
import "@fontsource/barlow-condensed/latin-900.css";
import "@fontsource-variable/dm-sans";
import "lenis/dist/lenis.css";
import "./globals.css";
import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = {
  title: "Hypertube",
  description: "Discover unforgettable stories and revisit the films that stay with you.",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <a className="skip-link" href="#main-content">Skip to content</a>
        {children}
      </body>
    </html>
  );
}
