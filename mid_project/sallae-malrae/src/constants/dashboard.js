export const CATEGORY_COLORS = [
  "#5F9367",
  "#8EBB96",
  "#D8B458",
  "#C9D2B6",
  "#49624F",
  "#B7C9A8",
];

export const LEVEL_LABEL = {
  1: "이제 시작",
  2: "감을 잡는 중",
  3: "흐름을 타는 중",
  4: "이미 잘하고 있어요",
  5: "전설 반열 진입중",
};

export function formatWon(value) {
  return new Intl.NumberFormat("ko-KR").format(value ?? 0);
}

export function getChartBackground(items) {
  if (items.length === 0) return "#EEF1EA";
  let current = 0;
  return `conic-gradient(${items
    .map((item, idx) => {
      const start = current;
      const end = current + item.ratio;
      current = end;
      const color = item.color ?? CATEGORY_COLORS[idx % CATEGORY_COLORS.length];
      return `${color} ${start}% ${end}%`;
    })
    .join(", ")})`;
}
