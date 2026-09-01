import Header from "@/components/layout/Header";
import Footer from "@/components/layout/Footer";
import DashboardSkeleton from "@/components/dashboard/DashboardSkeleton";

export default function Loading() {
  return (
    <div className="flex min-h-screen flex-col">
      <Header activeMenu="dashboard" />

      <main className="flex min-h-screen flex-col px-4 pb-6 pt-28 sm:px-6 lg:px-8">
        <div className="mx-auto flex w-full max-w-7xl flex-col">
          <p className="mb-2 text-xs font-semibold tracking-[0.24em] text-[#8FA58D]">
            DASHBOARD
          </p>
          <DashboardSkeleton />
        </div>
      </main>

      <Footer />
    </div>
  );
}
