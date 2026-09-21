import Image from "next/image";
import Link from "next/link";
import { AppShell } from "@/components/product/AppShell";
import { COPY, PRODUCT_NAME } from "@/lib/copy";

export default function HomePage() {
  return (
    <AppShell variant="public">
      <section className="kjx-hero" aria-labelledby="hero-title">
        <div className="kjx-wrap kjx-hero-layout">
          <div>
            <figure className="kjx-brand-art">
              <Image
                src="/brand/koujunxin-logo.jpg"
                alt="叩君芯渐变图标"
                width={1024}
                height={1024}
                sizes="(max-width: 767px) 132px, 168px"
                priority
              />
              <figcaption>晴空海蓝 · 暖橙光 · 奶油米白</figcaption>
            </figure>
            <p className="kjx-kicker">会员制自营平台</p>
            <h1 id="hero-title" className="kjx-title">
              {PRODUCT_NAME}
            </h1>
            <p className="kjx-lead">{COPY.homeLead}</p>
            <div className="kjx-actions">
              <Link className="kjx-btn" href="/works">
                {COPY.browse}
              </Link>
              <Link className="kjx-btn kjx-btn-ghost" href="/login">
                {COPY.login}
              </Link>
              <Link className="kjx-btn kjx-btn-ghost" href="/register">
                {COPY.register}
              </Link>
            </div>
          </div>
          <div className="kjx-ticket">
            <p className="kjx-kicker">KJX-A3F9K2B7</p>
            <h2>{COPY.ticketTitle}</h2>
            <p className="kjx-lead">{COPY.ticketBody}</p>
            <div className="kjx-actions">
              <Link className="kjx-btn" href="/me">
                {COPY.fillBinding}
              </Link>
              <Link className="kjx-btn kjx-btn-ghost" href="/works">
                {COPY.browse}
              </Link>
            </div>
          </div>
        </div>
      </section>
    </AppShell>
  );
}
