import Link from "next/link";
import Image from "next/image";
import { notFound } from "next/navigation";
import { fetchProduct } from "@/lib/api";
import { discountedPrice, formatWon } from "@/lib/format";
import ProductActions from "@/components/ProductActions";

export const dynamic = "force-dynamic";

export default async function ProductDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const p = await fetchProduct(id);
  if (!p) notFound();

  const soldout = p.stock <= 0;
  const final = discountedPrice(p);

  return (
    <main className="container">
      <div className="detail">
        <div className="image">
          <Image src={p.imageUrl} alt={p.name} fill sizes="50vw" unoptimized />
          {soldout && <div className="soldout">SOLD OUT</div>}
        </div>

        <div className="info">
          <div className="crumbs">
            {p.masterCategory} / {p.subCategory} / {p.articleType}
          </div>
          <h1 className="serif">{p.name}</h1>

          <div className="price-block">
            {p.discountRate > 0 && <span className="sale">{p.discountRate}%</span>}
            <span className="final">{formatWon(final)}</span>
            {p.discountRate > 0 && <span className="origin">{formatWon(p.price)}</span>}
          </div>

          <div className="stock-line">
            {soldout ? "품절된 상품입니다" : `재고 ${p.stock}개 · 즉시 배송 가능`}
          </div>

          <table className="spec">
            <tbody>
              <tr><th>상품번호</th><td>{p.id}</td></tr>
              <tr><th>카테고리</th><td>{p.articleType}</td></tr>
              <tr><th>색상</th><td>{p.baseColour}</td></tr>
              <tr><th>대상</th><td>{p.gender}</td></tr>
              <tr><th>시즌</th><td>{p.season}</td></tr>
            </tbody>
          </table>

          {/* 클라이언트 아일랜드: 수량/장바구니/구매 */}
          <ProductActions productId={p.id} stock={p.stock} />

          <p style={{ marginTop: 24, fontSize: 12, color: "var(--ink-soft)" }}>
            <Link href="/products">← 목록으로</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
