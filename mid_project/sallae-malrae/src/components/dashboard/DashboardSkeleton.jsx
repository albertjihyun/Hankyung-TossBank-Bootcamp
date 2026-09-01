import Card from "@/components/ui/Card";

export default function DashboardSkeleton() {
  return (
    <Card className="flex flex-col gap-4 border border-white/70 p-4 shadow-[0_24px_60px_rgba(33,70,56,0.10)] sm:p-5 lg:p-6">
      <div className="space-y-2">
        <div className="h-7 w-48 animate-pulse rounded bg-[#EEF1EA]" />
        <div className="h-4 w-72 animate-pulse rounded bg-[#EEF1EA]" />
      </div>
      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div
            key={i}
            className="h-24 animate-pulse rounded-2xl bg-[#EEF1EA]"
          />
        ))}
      </div>
      <div className="grid grid-cols-1 gap-3 xl:grid-cols-[1.12fr_0.88fr]">
        <div className="h-56 animate-pulse rounded-2xl bg-[#EEF1EA]" />
        <div className="h-56 animate-pulse rounded-2xl bg-[#EEF1EA]" />
      </div>
    </Card>
  );
}
