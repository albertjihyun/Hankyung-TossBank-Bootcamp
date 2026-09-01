"use client";

import { useEffect } from "react";
import Button from "@/components/ui/Button";

export default function RegisterSuccessModal({ open, onConfirm }) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e) => {
      if (e.key === "Enter" || e.key === "Escape") {
        e.preventDefault();
        onConfirm?.();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onConfirm]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="register-success-title"
    >
      <div className="w-full max-w-[360px] overflow-hidden rounded-3xl bg-white shadow-[0_24px_60px_rgba(33,70,56,0.25)]">
        <div className="flex flex-col items-center bg-gradient-to-b from-[#D8EBDD] to-[#EEF6EE] px-6 pt-9 pb-7 text-center">
          <span
            aria-hidden="true"
            className="flex h-16 w-16 items-center justify-center rounded-full bg-white text-3xl shadow-[0_8px_20px_rgba(33,70,56,0.10)]"
          >
            🌱
          </span>
          <h2
            id="register-success-title"
            className="mt-4 text-xl font-bold text-[#1D2A21]"
          >
            등록했어요!
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-[#55655A]">
            조금만 기다렸다 다시 생각해봐요.
            <br />
            함께 꾹 참아봅시다.
          </p>
        </div>
        <div className="px-6 pt-5 pb-6">
          <Button onClick={onConfirm} fullWidth size="lg">
            확인
          </Button>
        </div>
      </div>
    </div>
  );
}
