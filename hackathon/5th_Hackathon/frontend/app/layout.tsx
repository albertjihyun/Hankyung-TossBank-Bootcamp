import "./globals.css";
import type { Metadata } from "next";
import { AuthProvider } from "@/providers/AuthProvider";
import Header from "@/components/Header";

export const metadata: Metadata = {
  title: "OLIVE — 절제된 무드, 새로운 시즌",
  description: "OLIVE 패션몰",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body>
        <AuthProvider>
          <Header />
          {children}
          <footer className="site-footer serif">OLIVE — DEMO STORE</footer>
        </AuthProvider>
      </body>
    </html>
  );
}
