import localFont from "next/font/local";
import "./globals.css";
import { AuthProvider } from "@/contexts/AuthContext";

const pretendard = localFont({
  src: "../fonts/PretendardVariable.woff2",
  variable: "--font-pretendard",
  display: "swap",
});

export const metadata = {
  metadataBase: new URL(
    process.env.NEXT_PUBLIC_API_URL || "http://localhost:3000"
  ),
  title: "살래말래",
  description: "소비 습관을 관리하는 서비스",
};

export default function RootLayout({ children }) {
  return (
    <html lang="ko" className={`${pretendard.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
