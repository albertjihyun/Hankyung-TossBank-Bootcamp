import Link from "next/link";
import { fetchProducts, fetchCategories } from "@/lib/api";
import ProductCard from "@/components/ProductCard";

export const dynamic = "force-dynamic";

const CATEGORY_LABEL: Record<string, string> = {
  Apparel: "의류",
  Footwear: "슈즈",
  Accessories: "액세서리",
};

function buildQuery(category: string | undefined, page: number) {
  const qs = new URLSearchParams();
  if (category) qs.set("category", category);
  if (page > 0) qs.set("page", String(page));
  const s = qs.toString();
  return s ? `/products?${s}` : "/products";
}

export default async function ProductsPage({
  searchParams,
}: {
  searchParams: Promise<{ category?: string; page?: string }>;
}) {
  const sp = await searchParams;
  const category = sp.category;
  const page = Math.max(0, parseInt(sp.page ?? "0", 10) || 0);
  const size = 40;

  const [data, categories] = await Promise.all([
    fetchProducts({ page, size, category }),
    fetchCategories(),
  ]);

  // 페이지네이션 윈도우 (현재 기준 ±2)
  const start = Math.max(0, page - 2);
  const end = Math.min(data.totalPages - 1, page + 2);
  const pages: number[] = [];
  for (let i = start; i <= end; i++) pages.push(i);

  return (
    <main className="section">
      <div className="container">
        <p className="eyebrow">SHOP</p>
        <div className="section-head">
          <h2 className="serif">{category ? CATEGORY_LABEL[category] ?? category : "전체 상품"}</h2>
          <span className="more">{data.totalElements.toLocaleString()}개 상품</span>
        </div>

        <div className="tabs">
          <Link href="/products" className={`tab ${!category ? "active" : ""}`}>
            전체
          </Link>
          {categories.map((c) => (
            <Link
              key={c}
              href={buildQuery(c, 0)}
              className={`tab ${category === c ? "active" : ""}`}
            >
              {CATEGORY_LABEL[c] ?? c}
            </Link>
          ))}
        </div>

        <div className="grid cols-4">
          {data.content.map((p) => (
            <ProductCard key={p.id} p={p} />
          ))}
        </div>

        {data.totalPages > 1 && (
          <nav className="pagination">
            <Link
              href={buildQuery(category, Math.max(0, page - 1))}
              className={data.first ? "disabled" : ""}
            >
              ‹
            </Link>
            {start > 0 && <span>…</span>}
            {pages.map((i) =>
              i === page ? (
                <span key={i} className="current">
                  {i + 1}
                </span>
              ) : (
                <Link key={i} href={buildQuery(category, i)}>
                  {i + 1}
                </Link>
              )
            )}
            {end < data.totalPages - 1 && <span>…</span>}
            <Link
              href={buildQuery(category, page + 1)}
              className={data.last ? "disabled" : ""}
            >
              ›
            </Link>
          </nav>
        )}
      </div>
    </main>
  );
}
