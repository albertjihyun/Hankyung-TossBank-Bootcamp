"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Header from "@/components/layout/Header";
import Footer from "@/components/layout/Footer";
import Button from "@/components/ui/Button";
import ErrorAlert from "@/components/ui/ErrorAlert";
import { ERROR_MESSAGES } from "@/constants/errors";
import ProductInfoCard from "@/components/coolingoff/new/ProductInfoCard";
import CoolingOffDetailCard from "@/components/coolingoff/new/CoolingOffDetailCard";
import RegisterSuccessModal from "@/components/coolingoff/new/RegisterSuccessModal";

function buildExpireAt(dateStr, _period, hour) {
  const hour24 = parseInt(hour, 10);
  return new Date(
    `${dateStr}T${String(hour24).padStart(2, "0")}:00:00+09:00`,
  ).toISOString();
}

function PageTitle() {
  return (
    <div className="w-full max-w-2xl">
      <p className="text-sm font-semibold uppercase tracking-[0.24em] text-[#8FA58D]">
        Cooling Off
      </p>
      <h1 className="mt-3 text-3xl font-bold text-[#1F2A1F]">잠깐 멈춰봐요.</h1>
      <p className="mt-2 text-sm leading-6 text-[#7E8A7C]">
        지금 등록하고, 조금만 기다린 뒤 다시 결정해요.
      </p>
    </div>
  );
}

export default function CoolingOffNewPage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [showSuccessModal, setShowSuccessModal] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setIsSubmitting(true);

    try {
      const raw = new FormData(e.target);
      const decisionDate = raw.get("decisionDate");
      const decisionPeriod = raw.get("decisionPeriod");
      const decisionHour = raw.get("decisionHour");

      if (!decisionDate) {
        setError("쿨링오프 날짜를 선택해주세요.");
        return;
      }

      const formData = new FormData();
      formData.append("name", raw.get("name"));
      formData.append("price", String(raw.get("price")).replace(/,/g, ""));
      formData.append("category_id", raw.get("category_id"));
      formData.append("impulse_score", raw.get("impulse_score"));
      formData.append(
        "expire_at",
        buildExpireAt(decisionDate, decisionPeriod, decisionHour),
      );
      const memo = raw.get("memo");
      if (memo) formData.append("memo", memo);
      const image = raw.get("image");
      if (image && image.size > 0) formData.append("image", image);

      const res = await fetch("/api/items", { method: "POST", body: formData });
      const json = await res.json();

      if (json.success) {
        setShowSuccessModal(true);
        return;
      }

      if (json.code === "UNAUTHORIZED") {
        router.push("/auth/login");
        return;
      }

      setError(
        ERROR_MESSAGES[json.code] ?? "오류가 발생했어요. 다시 시도해주세요.",
      );
    } catch {
      setError("오류가 발생했어요. 다시 시도해주세요.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen flex-col bg-[#F6FAF5]">
      <Header activeMenu="coolingoff" />
      <section className="flex-1 px-6 pb-16 pt-28">
        <div className="mx-auto flex w-full max-w-3xl flex-col items-center">
          <PageTitle />
          <form
            onSubmit={handleSubmit}
            className="mt-8 flex w-full max-w-2xl flex-col items-center gap-5"
          >
            <div className="flex w-full flex-col gap-5">
              <ProductInfoCard />
              <CoolingOffDetailCard />
            </div>
            <ErrorAlert message={error} className="w-full" />
            <div className="flex w-full gap-3">
              <Button
                variant="secondary"
                size="lg"
                fullWidth
                type="button"
                onClick={() => router.push("/coolingoff")}
                className="border-transparent bg-[#D8DDDA] text-[#5B655D] hover:bg-[#CDD4D0]"
              >
                취소
              </Button>
              <Button type="submit" size="lg" fullWidth disabled={isSubmitting}>
                등록하고 멈춰보기
              </Button>
            </div>
          </form>
        </div>
      </section>
      <Footer />
      <RegisterSuccessModal
        open={showSuccessModal}
        onConfirm={() => router.push("/coolingoff")}
      />
    </main>
  );
}
