'use client';

interface SparklineProps {
  points: number[];
  stroke: string;
  gradientId: string;
  height?: number;
}

export function Sparkline({ points, stroke, gradientId, height = 36 }: SparklineProps) {
  if (points.length < 2) {
    return <div style={{ height }} className="w-full" />;
  }

  const W = 200;
  const H = height;
  const pad = 3;
  const max = Math.max(...points);
  const min = Math.min(...points);
  const range = Math.max(1, max - min);

  const coords = points.map((p, i) => ({
    x: (i / (points.length - 1)) * W,
    y: H - pad - ((p - min) / range) * (H - pad * 2),
  }));

  const polyline = coords.map(({ x, y }) => `${x},${y}`).join(' ');
  const fill = [
    `M ${coords[0].x},${H}`,
    ...coords.map(({ x, y }) => `L ${x},${y}`),
    `L ${coords[coords.length - 1].x},${H} Z`,
  ].join(' ');
  const last = coords[coords.length - 1];

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      style={{ height }}
      className="w-full overflow-visible"
      preserveAspectRatio="none"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={stroke} stopOpacity="0.3" />
          <stop offset="100%" stopColor={stroke} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={fill} fill={`url(#${gradientId})`} />
      <polyline
        points={polyline}
        fill="none"
        stroke={stroke}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={last.x} cy={last.y} r="2.5" fill={stroke} />
    </svg>
  );
}
