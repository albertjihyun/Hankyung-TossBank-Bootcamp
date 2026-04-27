import { BattleScreen } from '@/components/battle/BattleScreen';

export default async function BattlePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <BattleScreen battleId={Number(id)} />;
}
