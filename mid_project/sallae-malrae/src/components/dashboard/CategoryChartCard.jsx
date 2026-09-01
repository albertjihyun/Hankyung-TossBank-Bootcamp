export default function CategoryChartCard({ items, background }) {
  const isEmpty = items.length === 0;

  return (
    <div className="flex flex-col rounded-2xl border border-[#EEF1EA] bg-[#F7F8F2] p-4 shadow-[0_10px_28px_rgba(33,70,56,0.06)]">
      <h2 className="text-base font-semibold text-[#24352A]">
        이번 달 카테고리 비율
      </h2>

      {isEmpty ? (
        <div className="mt-4 flex flex-1 flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-[#D5DBC9] bg-white py-8 text-center">
          <p className="text-sm text-[#6B766F]">
            이번 달에 참은 항목이 쌓이면 카테고리 비율을 보여드릴게요.
          </p>
        </div>
      ) : (
        <div className="mt-2.5 flex flex-1 flex-col items-center justify-center gap-6 lg:flex-row lg:justify-center lg:gap-12">
          <div
            className="relative h-36 w-36 shrink-0 rounded-full lg:h-40 lg:w-40"
            style={{ background }}
          >
            <div className="absolute inset-[26%] rounded-full bg-[#F7F8F2]" />
          </div>

          <div className="w-full max-w-[220px] space-y-2 lg:w-[180px] lg:max-w-none">
            {items.map((item) => (
              <div
                key={item.name}
                className="flex items-center justify-between gap-3 text-sm"
              >
                <div className="flex items-center gap-2 text-[#55655A]">
                  <span
                    className="h-2.5 w-2.5 rounded-full"
                    style={{ backgroundColor: item.color }}
                  />
                  <span>{item.name}</span>
                </div>
                <span className="font-semibold text-[#24352A]">
                  {item.ratio}%
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
