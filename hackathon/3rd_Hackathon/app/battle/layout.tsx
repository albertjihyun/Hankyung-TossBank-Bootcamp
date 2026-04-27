import { Header } from '@/components/layout/Header';

export default function BattleLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-transparent">
      <Header />
      {children}
    </div>
  );
}
