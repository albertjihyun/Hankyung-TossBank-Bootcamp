"use client";

import { useEffect } from "react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

export default function LandingAnimations() {
  useEffect(() => {
    document.documentElement.style.scrollSnapType = "y mandatory";
    return () => {
      document.documentElement.style.scrollSnapType = "";
    };
  }, []);

  useEffect(() => {
    const ctx = gsap.context(() => {
      const sections = gsap.utils.toArray(".landing-section");

      sections.forEach((section) => {
        const isCooling = section.id === "cooling-off-section";
        const bg = section.querySelector(".section-bg");
        const sectionContent = section.querySelector(".section-content");
        const items = section.querySelectorAll(".section-content > *:not(.step-card-fan)");
        const stepCards = [...section.querySelectorAll(".step-card-item")].filter(
          (el) => el.offsetParent !== null
        );
        const heroBills = section.querySelectorAll(".hero-bill");
        const coolingCard = isCooling ? section.querySelector(".cooling-card") : null;
        const coolingDecos = isCooling ? section.querySelectorAll(".cooling-deco") : [];
        const coolingTexts = isCooling ? section.querySelectorAll(".cooling-text") : [];
        if (!bg) return;

        gsap.set(bg, { yPercent: 100 });
        if (isCooling) {
          if (coolingCard) gsap.set(coolingCard, { opacity: 0, y: 40 });
          if (coolingDecos.length) gsap.set(coolingDecos, { opacity: 0, y: 20 });
          if (coolingTexts.length) gsap.set(coolingTexts, { opacity: 0, y: 20 });
        } else {
          if (sectionContent) gsap.set(sectionContent, { opacity: 0, y: 40 });
          gsap.set(items, { opacity: 0, y: 30 });
          if (stepCards.length) gsap.set(stepCards, { opacity: 0, y: 150 });
          if (heroBills.length) gsap.set(heroBills, { opacity: 0, y: 40 });
        }

        const tl = gsap.timeline({
          scrollTrigger: {
            trigger: section,
            start: "top 80%",
            once: true,
          },
        });

        tl.to(bg, { yPercent: 0, duration: 0.45, ease: "power2.out" });

        if (isCooling) {
          if (coolingCard) {
            tl.to(
              coolingCard,
              { opacity: 1, y: 0, duration: 0.4, ease: "power2.out", clearProps: "transform" },
              "+=0.1"
            );
          }
          if (coolingTexts.length) {
            tl.to(
              coolingTexts,
              { opacity: 1, y: 0, duration: 0.45, stagger: 0.3, ease: "power2.out", clearProps: "transform" },
              "+=0.1"
            );
          }
          if (coolingDecos.length) {
            tl.to(
              coolingDecos,
              { opacity: 1, y: 0, duration: 0.45, stagger: 0.3, ease: "power2.out", clearProps: "transform" },
              "+=0.1"
            );
          }
        } else {
          if (sectionContent) {
            tl.to(
              sectionContent,
              { opacity: 1, y: 0, duration: 0.4, ease: "power2.out", clearProps: "transform" },
              "+=0.1"
            );
          }
          tl.to(
            items,
            { opacity: 1, y: 0, duration: 0.35, stagger: 0.3, ease: "power2.out", clearProps: "transform" },
            "+=0.1"
          );
          if (heroBills.length) {
            tl.to(
              heroBills,
              { opacity: 0.75, y: 0, duration: 0.7, stagger: 0.4, ease: "power2.out", clearProps: "transform" },
              "-=0.2"
            );
          }
          if (stepCards.length) {
            const isMobile = window.innerWidth < 768;
            tl.to(
              stepCards,
              {
                opacity: 1,
                y: 0,
                duration: 0.5,
                stagger: isMobile ? 0 : 0.35,
                ease: "power2.out",
                clearProps: "transform",
              },
              "+=0.15"
            );
          }
        }
      });

      ScrollTrigger.refresh();
    });

    return () => ctx.revert();
  }, []);

  return null;
}
