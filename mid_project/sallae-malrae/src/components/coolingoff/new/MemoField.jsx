"use client";

import { useState } from "react";
import FieldLabel from "./FieldLabel";

const MEMO_MAX_LENGTH = 500;

export default function MemoField() {
  const [memo, setMemo] = useState("");
  const length = memo.length;
  const isNearLimit = length >= MEMO_MAX_LENGTH * 0.9;
  const isAtLimit = length >= MEMO_MAX_LENGTH;

  return (
    <div className="mt-6">
      <FieldLabel>이걸 사고 싶은 이유가 있나요?</FieldLabel>
      <div className="relative">
        <textarea
          id="memo"
          name="memo"
          rows="4"
          maxLength={MEMO_MAX_LENGTH}
          value={memo}
          onChange={(event) => setMemo(event.target.value)}
          placeholder="지금 당장 필요한 이유를 적어보세요."
          aria-describedby="memo-counter"
          className="w-full resize-none rounded-2xl border border-gray-300 px-5 py-4 pb-9 text-sm outline-none transition-colors placeholder:text-gray-400 focus:border-[#8EAA92]"
        />
        <p
          id="memo-counter"
          aria-live="polite"
          className={`pointer-events-none absolute bottom-3 right-4 text-xs tabular-nums ${
            isAtLimit
              ? "text-[#D96C6C] font-medium"
              : isNearLimit
                ? "text-[#A8763A]"
                : "text-[#9AA49A]"
          }`}
        >
          {length}/{MEMO_MAX_LENGTH}
        </p>
      </div>
    </div>
  );
}
