"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { NextAction } from "@/components/product/NextAction";
import { COPY } from "@/lib/copy";
import { loginHref, useSessionPresence } from "@/lib/session-presence";
import { WORLD_COPIES } from "@/lib/world";

export function WorldHome() {
  const pathname = usePathname();
  const { presence } = useSessionPresence(pathname);
  const railRef = useRef<HTMLOListElement>(null);
  const [active, setActive] = useState(0);

  useEffect(() => {
    const rail = railRef.current;
    if (!rail) {
      return;
    }
    const cards = () => [...rail.querySelectorAll<HTMLElement>("[data-focus-card]")];
    const mark = () => {
      const railBox = rail.getBoundingClientRect();
      const mid = railBox.top + railBox.height / 2;
      let focus: HTMLElement | null = null;
      let focusIndex = 0;
      let nearest = Number.POSITIVE_INFINITY;
      cards().forEach((card, index) => {
        const box = card.getBoundingClientRect();
        const distance = Math.abs(box.top + box.height / 2 - mid);
        if (distance < nearest) {
          nearest = distance;
          focus = card;
          focusIndex = index;
        }
      });
      cards().forEach((card) => card.classList.toggle("is-focus", card === focus));
      setActive(focusIndex);
    };
    mark();
    rail.addEventListener("scroll", mark, { passive: true });
    window.addEventListener("resize", mark);
    return () => {
      rail.removeEventListener("scroll", mark);
      window.removeEventListener("resize", mark);
    };
  }, []);

  const scrollToIndex = (index: number) => {
    const rail = railRef.current;
    if (!rail) {
      return;
    }
    const card = rail.querySelectorAll<HTMLElement>("[data-focus-card]")[index];
    if (!card) {
      return;
    }
    const top = card.offsetTop - (rail.clientHeight - card.offsetHeight) / 2;
    rail.scrollTo({ top: Math.max(0, top), behavior: "smooth" });
  };

  return (
    <section className="kjx-world" aria-labelledby="hero-title">
      <div className="kjx-atlas" aria-hidden="true" />
      <div className="kjx-wrap kjx-world-layout">
        <div className="kjx-world-hero">
          <h1 id="hero-title" className="kjx-title">
            <span className="kjx-reveal-line">
              <span>{COPY.worldTitle}</span>
            </span>
          </h1>
          <p className="kjx-lead kjx-world-lead">
            <span className="kjx-reveal-line kjx-reveal-delay">
              <span>{COPY.homeLead}</span>
            </span>
          </p>
          <div className="kjx-reveal-actions">
            <NextAction />
          </div>
        </div>
        <div className="kjx-focus-stage">
          <ol ref={railRef} className="kjx-focus-rail" aria-label="作品">
            {WORLD_COPIES.map((copy) => {
              const href = presence === "guest" ? loginHref(copy.href) : copy.href;
              return (
                <li key={copy.id} data-focus-card className={copy.trial ? "kjx-copy kjx-copy-primary" : "kjx-copy kjx-copy-next"}>
                  <p className="kjx-copy-meta">
                    <span>{copy.kicker}</span>
                    {copy.trial ? <span className="kjx-copy-badge">{COPY.trial}</span> : <span className="kjx-copy-badge">{COPY.comingSoon}</span>}
                  </p>
                  <h2>{copy.name}</h2>
                  <p className="kjx-copy-quote">{copy.quote}</p>
                  <p className="kjx-hint">{copy.people}</p>
                  <div className="kjx-actions kjx-actions-single">
                    <Link className="kjx-btn kjx-btn-ghost" href={href}>
                      {copy.trial ? COPY.trial : COPY.works}
                    </Link>
                  </div>
                  {copy.trial ? <p className="kjx-hint">{COPY.trialHint}</p> : null}
                </li>
              );
            })}
          </ol>
          <div className="kjx-focus-dots" role="tablist" aria-label="作品位置">
            {WORLD_COPIES.map((copy, index) => (
              <button
                key={copy.id}
                type="button"
                role="tab"
                aria-label={copy.name}
                aria-selected={active === index}
                className={active === index ? "is-active" : undefined}
                onClick={() => scrollToIndex(index)}
              />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
