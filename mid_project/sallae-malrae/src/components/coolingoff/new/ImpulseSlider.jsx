"use client";

import { useState } from "react";

const IMPULSE_LEVEL_OPTIONS = Array.from({ length: 10 }, (_, index) => index + 1);

export default function ImpulseSlider() {
  const [impulseLevel, setImpulseLevel] = useState(3);
  const progress = ((impulseLevel - 1) / (IMPULSE_LEVEL_OPTIONS.length - 1)) * 100;

  return (
    <div className="rounded-xl border border-[#E2E8E0] bg-white px-4 py-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs text-[#8C968A]">
          1은 가벼운 관심, 10은 바로 사고 싶은 상태예요.
        </p>
        <span className="text-sm font-semibold text-[#5B655D]">{impulseLevel}/10</span>
      </div>
      <div className="mt-4">
        <input
          id="impulse_score"
          name="impulse_score"
          type="range"
          min="1"
          max="10"
          step="1"
          value={impulseLevel}
          onChange={(event) => setImpulseLevel(Number(event.target.value))}
          style={{
            background: `linear-gradient(to right, #7F987E 0%, #7F987E ${progress}%, #E3E9E1 ${progress}%, #E3E9E1 100%)`,
          }}
          className="
            h-1.5 w-full cursor-pointer appearance-none rounded-full bg-transparent
            [&::-webkit-slider-runnable-track]:h-1.5 [&::-webkit-slider-runnable-track]:rounded-full
            [&::-webkit-slider-thumb]:mt-[-5px] [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:w-4
            [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full
            [&::-webkit-slider-thumb]:border-[3px] [&::-webkit-slider-thumb]:border-white
            [&::-webkit-slider-thumb]:bg-[#6D876D] [&::-webkit-slider-thumb]:shadow-[0_2px_8px_rgba(93,122,98,0.22)]
            [&::-moz-range-track]:h-1.5 [&::-moz-range-track]:rounded-full [&::-moz-range-track]:bg-transparent
            [&::-moz-range-thumb]:h-4 [&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:rounded-full
            [&::-moz-range-thumb]:border-[3px] [&::-moz-range-thumb]:border-white
            [&::-moz-range-thumb]:bg-[#6D876D] [&::-moz-range-thumb]:shadow-[0_2px_8px_rgba(93,122,98,0.22)]
          "
        />
        <div className="mt-2 grid grid-cols-10 gap-1.5 px-1">
          {IMPULSE_LEVEL_OPTIONS.map((level) => {
            const isSelected = level === impulseLevel;
            return (
              <button
                key={level}
                type="button"
                onClick={() => setImpulseLevel(level)}
                aria-pressed={isSelected}
                aria-label={`${level}점 선택`}
                className="flex items-center justify-center py-1"
              >
                <span
                  className={`rounded-full transition-all ${
                    isSelected ? "h-2 w-2 bg-[#6D876D]" : "h-1.5 w-1.5 bg-[#D6DDD4]"
                  }`}
                />
              </button>
            );
          })}
        </div>
        <div className="mt-2.5 flex items-center justify-between text-[11px] text-[#97A096]">
          <span>애매하긴 해</span>
          <span>너무나도 사고 싶어</span>
        </div>
      </div>
    </div>
  );
}
