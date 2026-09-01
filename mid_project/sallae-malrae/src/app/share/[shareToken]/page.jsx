import { notFound } from "next/navigation";

import { getShareData } from "@/lib/share";

import ShareView from "@/components/share/ShareView";

export async function generateMetadata({ params }) {
  const { shareToken } = await params;

  const title = "살래말래 — 충동구매 브레이크";
  const description =
    "오늘 사고 싶은 그거, N일 뒤에도 사고 싶을까요? 살래말래에서 친구의 쿨링오프 기록을 확인해보세요.";
  const ogImage = "/og/share.png";
  const pageUrl = `/share/${shareToken}`;

  return {
    title,
    description,
    openGraph: {
      title,
      description,
      url: pageUrl,
      siteName: "살래말래",
      images: [
        {
          url: ogImage,
          width: 1200,
          height: 630,
          alt: "살래말래 — 충동구매 브레이크 서비스",
        },
      ],
      locale: "ko_KR",
      type: "website",
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images: [ogImage],
    },
  };
}

export default async function SharePage({ params }) {
  const { shareToken } = await params;

  const data = await getShareData(shareToken);
  if (!data) notFound();

  return <ShareView data={data} />;
}
