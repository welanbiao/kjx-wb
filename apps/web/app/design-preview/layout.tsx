import type { Metadata } from "next";
import type { ReactNode } from "react";
import {
  Archivo,
  Barlow,
  Figtree,
  IBM_Plex_Mono,
  Newsreader,
  Noto_Sans_SC,
  Noto_Serif_SC,
  Nunito,
  Spline_Sans_Mono,
} from "next/font/google";
import "./preview.css";

const archivo = Archivo({
  subsets: ["latin"],
  weight: ["500", "700", "800"],
  variable: "--font-archivo",
  display: "swap",
});

const nunito = Nunito({
  subsets: ["latin"],
  weight: ["400", "600", "700"],
  variable: "--font-nunito",
  display: "swap",
});

const barlow = Barlow({
  subsets: ["latin"],
  weight: ["400", "600", "800", "900"],
  variable: "--font-barlow",
  display: "swap",
});

const ibmMono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "600"],
  variable: "--font-ibm-mono",
  display: "swap",
});

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
  title: "视觉方向对比 · 叩君芯",
  description: "叩君芯前台三套视觉方向预览：公开入口、会员解锁任务、后台表格样例。",
};

export default function DesignPreviewLayout({ children }: { children: ReactNode }) {
  return (
    <div
      className={[
        archivo.variable,
        nunito.variable,
        barlow.variable,
        ibmMono.variable,
        newsreader.variable,
        figtree.variable,
        splineMono.variable,
        notoSans.variable,
        notoSerif.variable,
      ].join(" ")}
    >
      {children}
    </div>
  );
}
