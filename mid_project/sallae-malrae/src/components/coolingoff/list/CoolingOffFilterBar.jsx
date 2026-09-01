const FILTERS = [
  { label: "참는중", value: "ongoing" },
  { label: "완료됨", value: "done" },
];

export default function CoolingOffFilterBar({
  filter,
  completedSubFilter,
  onFilterChange,
  onSubFilterChange,
}) {
  return (
    <div className="flex items-end gap-2 mb-6 flex-wrap">
      {FILTERS.map((f) => (
        <button
          key={f.value}
          onClick={() => onFilterChange(f.value)}
          className={`px-5 py-2 rounded-full text-sm font-medium transition-colors ${
            filter === f.value
              ? "bg-gray-700 text-white"
              : "bg-white text-gray-500 border border-gray-200"
          }`}
        >
          {f.label}
        </button>
      ))}

      {filter === "done" && (
        <>
          <button
            onClick={() => onSubFilterChange(completedSubFilter === "passed" ? null : "passed")}
            className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium transition-colors ${
              completedSubFilter === "passed"
                ? "bg-blue-500 text-white"
                : "bg-white border border-blue-200 text-blue-500"
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full inline-block ${
                completedSubFilter === "passed" ? "bg-white" : "bg-blue-400"
              }`}
            />
            아낌
          </button>
          <button
            onClick={() => onSubFilterChange(completedSubFilter === "bought" ? null : "bought")}
            className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium transition-colors ${
              completedSubFilter === "bought"
                ? "bg-red-500 text-white"
                : "bg-white border border-red-200 text-red-500"
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full inline-block ${
                completedSubFilter === "bought" ? "bg-white" : "bg-red-400"
              }`}
            />
            구매
          </button>
        </>
      )}
    </div>
  );
}
