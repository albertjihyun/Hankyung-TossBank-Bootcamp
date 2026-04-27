import { WaitingRoom } from '@/components/room/WaitingRoom';

export default async function RoomPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <WaitingRoom roomId={Number(id)} />;
}
