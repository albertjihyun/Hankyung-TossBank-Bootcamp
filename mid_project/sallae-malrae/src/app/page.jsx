import Link from "next/link";
import Header from "@/components/layout/Header";
import Footer from "@/components/layout/Footer";
import { LandingDayProvider } from "@/contexts/LandingDayContext";
import LandingHero from "@/components/landing/LandingHero";
import LandingQuote from "@/components/landing/LandingQuote";
import LandingCoolingOff from "@/components/landing/LandingCoolingOff";
import LandingSteps from "@/components/landing/LandingSteps";
import LandingCTA from "@/components/landing/LandingCTA";
import LandingAnimations from "@/components/landing/LandingAnimations";

export default function Home() {
  return (
    <LandingDayProvider>
      <Header
        navItems={[]}
        transparent
        rightSlot={
          <div className="flex items-center gap-3">
            <Link href="/auth/login" className="text-sm text-[#5D7A62]">
              로그인
            </Link>
            <Link
              href="/auth/signup"
              className="rounded-full bg-[#214638] px-4 py-1.5 text-sm font-medium text-white hover:bg-[#1a3529] transition-colors"
            >
              회원가입
            </Link>
          </div>
        }
      />

      <main>
        <LandingHero />
        <LandingQuote />
        <LandingCoolingOff />
        <LandingSteps />
        <LandingCTA />
      </main>

      <section className="snap-section">
        <Footer />
      </section>

      <LandingAnimations />
    </LandingDayProvider>
  );
}
