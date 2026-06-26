// 서버/클라이언트 양쪽에서 쓰는 순수 함수 + 타입 (process.env 의존 없음).

export type Product = {
  id: number;
  name: string;
  gender: string;
  masterCategory: string;
  subCategory: string;
  articleType: string;
  baseColour: string;
  season: string;
  price: number;
  discountRate: number;
  stock: number;
  imageUrl: string;
};

export type ProductPage = {
  content: Product[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type User = { id: number; email: string; name: string; role: string };

export type CartItemView = {
  id: number;
  productId: number;
  name: string;
  imageUrl: string;
  price: number;
  discountRate: number;
  finalPrice: number;
  quantity: number;
  stock: number;
  lineTotal: number;
  soldOut: boolean;
};

export type CartView = {
  items: CartItemView[];
  totalQuantity: number;
  totalAmount: number;
};

export type OrderItemView = {
  productId: number;
  productName: string;
  imageUrl: string;
  unitPrice: number;
  quantity: number;
};

export type OrderView = {
  id: number;
  status: string;
  totalAmount: number;
  recipientName: string;
  phone: string;
  address: string;
  createdAt: string;
  items: OrderItemView[];
};

export function discountedPrice(p: { price: number; discountRate: number }): number {
  if (!p.discountRate) return p.price;
  return Math.round((p.price * (100 - p.discountRate)) / 100 / 100) * 100;
}

export function formatWon(n: number): string {
  return "₩" + n.toLocaleString("ko-KR");
}
