import type { Metadata } from 'next';
import './globals.css';
import { AppProviders } from '@/components/providers/AppProviders';
import { ToastContainer } from '@/components/ui/Toast';

export const metadata: Metadata = {
  title: 'StockBattle',
  description: '실시간 모의 주식 배틀',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>
        <AppProviders>
          {children}
          <ToastContainer />
        </AppProviders>
      </body>
    </html>
  );
}
