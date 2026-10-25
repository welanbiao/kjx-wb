import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Figtree, Newsreader, Noto_Sans_SC, Noto_Serif_SC, Spline_Sans_Mono } from "next/font/google";
import "./globals.css";

const newsreader = Newsreader({
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  style: ["normal", "italic"],
  variable: "--font-newsreader",
  display: "swap",
});

const figtree = Figtree({
  subsets: ["latin"],
  weight: ["400", "600", "700"],
  variable: "--font-figtree",
  display: "swap",
});

const splineMono = Spline_Sans_Mono({
  subsets: ["latin"],
  weight: ["400", "600"],
  variable: "--font-spline-mono",
  display: "swap",
});

const notoSans = Noto_Sans_SC({
  subsets: ["latin"],
  weight: ["400", "500", "700"],
  variable: "--font-noto-sans",
  display: "swap",
  preload: false,
});

const notoSerif = Noto_Serif_SC({
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  variable: "--font-noto-serif",
  display: "swap",
  preload: false,
});

export const metadata: Metadata = {
  title: "叩君芯",
  description: "叩君芯无限世界：登录、试玩、进入文游副本。",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body
        className={[
          newsreader.variable,
          figtree.variable,
          splineMono.variable,
          notoSans.variable,
          notoSerif.variable,
        ].join(" ")}
      >
        {children}
      </body>
    </html>
  );
}
