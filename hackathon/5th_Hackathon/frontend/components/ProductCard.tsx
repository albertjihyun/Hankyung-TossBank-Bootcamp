import Link from "next/link";
import Image from "next/image";
import { Product, discountedPrice, formatWon } from "@/lib/format";

export default function ProductCard({ p }: { p: Product }) {
  const soldout = p.stock <= 0;
  const final = discountedPrice(p);
  return (
    <Link href={`/products/${p.id}`} className="card">
      <div className="thumb">
        <Image
          src={p.imageUrl}
          alt={p.name}
          fill
          sizes="(max-width: 860px) 50vw, 25vw"
          unoptimized
        />
        {soldout && <div className="soldout">SOLD OUT</div>}
      </div>
      <div className="meta">
        <div className="cat">{p.articleType}</div>
        <div className="name">{p.name}</div>
        <div className="price-row">
          {p.discountRate > 0 && <span className="sale">{p.discountRate}%</span>}
          <span className="final">{formatWon(final)}</span>
          {p.discountRate > 0 && <span className="origin">{formatWon(p.price)}</span>}
        </div>
      </div>
    </Link>
  );
}
