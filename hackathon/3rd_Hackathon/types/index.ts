export interface User {
  id: number;
  email: string;
  nickname: string;
}

export interface Room {
  id: number;
  title: string;
  hostId: number;
  hostNickname: string;
  memberCount: number;
  maxMembers: 4;
  status: 'waiting' | 'active' | 'closed';
}

export interface RoomMember {
  userId: number;
  nickname: string;
  isHost: boolean;
}

export interface RoomDetail {
  id: number;
  title: string;
  hostId: number;
  status: string;
  members: RoomMember[];
  battleStarted: boolean;
  battleId: number | null;
}

export interface Stock {
  id: number;
  symbol: string;
  name: string;
  volatility: number;
  initialPrice: number;
}

export interface PricePoint {
  price: number;
  recordedAt: string;
}

export interface StockPrice {
  stockId: number;
  symbol: string;
  currentPrice: number;
  initialPrice: number;
  changeRate: number;
  history: PricePoint[];
}

export interface Holding {
  stockId: number;
  symbol: string;
  quantity: number;
  avgBuyPrice: number;
}

export interface Portfolio {
  cashBalance: number;
  holdings: Holding[];
}

export interface Ranking {
  rank: number;
  userId: number;
  nickname: string;
  totalAssets: number;
  changeRate: number;
}

export interface ResultRanking extends Ranking {
  finalAssets: number;
  isMe: boolean;
}

export interface Battle {
  id: number;
  status: 'active' | 'ended';
  endsAt: string;
  initialCash: number;
  participants: { userId: number; nickname: string }[];
}

export interface ActiveRoom {
  roomId: number;
  battleId: number | null;
  status: 'waiting' | 'active' | 'ended';
}

export interface ApiSuccess<T> {
  success: true;
  data: T;
}

export interface ApiError {
  success: false;
  error: { code: string; message: string };
}

export type ApiResponse<T> = ApiSuccess<T> | ApiError;
