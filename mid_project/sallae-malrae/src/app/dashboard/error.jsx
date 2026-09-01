"use client";

import Header from "@/components/layout/Header";
import Footer from "@/components/layout/Footer";
import Card from "@/components/ui/Card";
import ErrorAlert from "@/components/ui/ErrorAlert";

export default function DashboardError() {
  return (
    <div className="flex min-h-screen flex-col">
      <Header activeMenu="dashboard" />

      <main className="flex min-h-screen flex-col px-4 pb-6 pt-28 sm:px-6 lg:px-8">
        <div className="mx-auto flex w-full max-w-7xl flex-col">
          <p className="mb-2 text-xs font-semibold tracking-[0.24em] text-[#8FA58D]">
            DASHBOARD
          </p>
          <Card className="p-6">
            <ErrorAlert message="대시보드 정보를 불러오지 못했어요." />
          </Card>
        </div>
      </main>

      <Footer />
    </div>
  );
}
