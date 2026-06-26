import Link from "next/link";
import { fetchProducts } from "@/lib/api";
import ProductCard from "@/components/ProductCard";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  const page = await fetchProducts({ page: 0, size: 6 });

  return (
    <main>
      <section className="hero">
        <div className="container">
          <p className="eyebrow">NEW SEASON 2026</p>
          <h1 className="serif">
            절제된 무드,
            <br />
            새로운 시즌
          </h1>
          <div className="badge">VIEW COLLECTION</div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head">
            <div>
              <p className="eyebrow">THIS WEEK</p>
              <h2 className="serif">이번 주 추천</h2>
            </div>
            <Link href="/products" className="more">
              전체보기 →
            </Link>
          </div>
          <div className="grid">
            {page.content.map((p) => (
              <ProductCard key={p.id} p={p} />
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
