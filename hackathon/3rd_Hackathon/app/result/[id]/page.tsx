import { ResultScreen } from '@/components/result/ResultScreen';

export default async function ResultPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ResultScreen battleId={Number(id)} />;
}
