import type { Metadata } from "next";
import { Barlow_Condensed, DM_Sans, IBM_Plex_Mono } from "next/font/google";
import type { ReactNode } from "react";
import { SmoothScroll } from "./components/smooth-scroll";
import "./globals.css";

const bodyFont = DM_Sans({ subsets: ["latin"], variable: "--font-dm-sans" });
const displayFont = Barlow_Condensed({ subsets: ["latin"], weight: ["700", "800", "900"], variable: "--font-barlow" });
const monoFont = IBM_Plex_Mono({ subsets: ["latin"], weight: ["400"], variable: "--font-ibm-plex" });

export const metadata: Metadata = {
  title: "Hypertube",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body className={`${bodyFont.variable} ${displayFont.variable} ${monoFont.variable}`}>
        <SmoothScroll>{children}</SmoothScroll>
      </body>
    </html>
  );
}
