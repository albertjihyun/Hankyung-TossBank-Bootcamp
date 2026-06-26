"use client";

export default function QuantityStepper({
  value,
  onChange,
  max,
  min = 1,
}: {
  value: number;
  onChange: (next: number) => void;
  max?: number;
  min?: number;
}) {
  const clamp = (n: number) => {
    let v = n;
    if (v < min) v = min;
    if (max != null && v > max) v = max;
    return v;
  };
  return (
    <div className="qty-stepper">
      <button type="button" aria-label="감소" onClick={() => onChange(clamp(value - 1))}>
        −
      </button>
      <input
        type="number"
        value={value}
        min={min}
        max={max}
        onChange={(e) => onChange(clamp(parseInt(e.target.value || "1", 10)))}
      />
      <button type="button" aria-label="증가" onClick={() => onChange(clamp(value + 1))}>
        +
      </button>
    </div>
  );
}
