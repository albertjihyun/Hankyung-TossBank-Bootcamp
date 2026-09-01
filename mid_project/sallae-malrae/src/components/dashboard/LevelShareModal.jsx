"use client";

import { useEffect, useState } from "react";

import { getLevelMeta } from "@/lib/level";

function formatSavedAmount(value) {
  const v = value ?? 0;
  if (v >= 10000) {
    const man = Math.floor(v / 10000);
    return `₩${man}만`;
  }
  return `₩${new Intl.NumberFormat("ko-KR").format(v)}`;
}

export default function LevelShareModal({ open, onClose, user, summary }) {
  const [copyState, setCopyState] = useState("idle"); // idle | loading | done | error
  const [copyMessage, setCopyMessage] = useState("");

  const handleClose = () => {
    setCopyState("idle");
    setCopyMessage("");
    onClose?.();
  };

  useEffect(() => {
    if (!open) return;

    const onKey = (e) => {
      if (e.key === "Escape") {
        setCopyState("idle");
        setCopyMessage("");
        onClose?.();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  const level = user?.level ?? 1;
  const { name: title, description, image } = getLevelMeta(level);

  const passedCount = summary?.passed_count ?? 0;
  const successRate = summary?.success_rate ?? 0;
  const savedAmount = summary?.saved_amount ?? 0;

  const handleShare = async () => {
    setCopyState("loading");
    setCopyMessage("");

    try {
      const res = await fetch("/api/share/token", { method: "GET" });
      const body = await res.json();

      if (!body.success) {
        setCopyState("error");
        setCopyMessage("공유 링크를 만들지 못했어요.");
        return;
      }

      const url = body.data.shareUrl;
      await navigator.clipboard.writeText(url);
      setCopyState("done");
      setCopyMessage("공유 링크를 복사했어요!");
    } catch {
      setCopyState("error");
      setCopyMessage("공유 링크를 복사하지 못했어요.");
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      onClick={handleClose}
    >
      <div
        className="relative w-full max-w-[360px] overflow-hidden rounded-3xl bg-white shadow-[0_24px_60px_rgba(33,70,56,0.25)]"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="레벨 공유"
      >
        <button
          type="button"
          aria-label="닫기"
          onClick={handleClose}
          className="absolute right-4 top-4 z-10 flex h-7 w-7 items-center justify-center rounded-full text-[#6B766F] transition hover:bg-[#EEF1EA]"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="h-4 w-4"
          >
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>

        <div className="flex flex-col items-center bg-gradient-to-b from-[#D8EBDD] to-[#EEF6EE] px-6 pt-10 pb-7">
          <span className="rounded-full bg-[#4A8A72] px-3 py-1 text-xs font-semibold text-white">
            Lv.{level}
          </span>

          <div className="mt-4 flex h-24 w-24 items-center justify-center rounded-full bg-white shadow-[0_8px_20px_rgba(33,70,56,0.10)]">
            <img src={image} alt={title} className="h-16 w-16 rounded-full object-cover" />
          </div>

          <h2 className="mt-4 text-lg font-bold text-[#1D2A21]">{title}</h2>
          <p className="mt-1 whitespace-pre-line text-center text-sm text-[#55655A]">
            {description}
          </p>

          {user?.nickname && (
            <span className="mt-3 rounded-full bg-[#C9E0CE] px-3 py-1 text-xs font-medium text-[#3F6A4D]">
              @{user.nickname}의 기록
            </span>
          )}
        </div>

        <div className="px-6 pt-5 pb-6">
          <div className="grid grid-cols-3 gap-2">
            <StatCell value={passedCount.toLocaleString("ko-KR")} label="참은 횟수" />
            <StatCell
              value={`${successRate}%`}
              label="성공률"
              valueClassName="text-[#4A8A72]"
            />
            <StatCell
              value={formatSavedAmount(savedAmount)}
              label="절약 금액"
            />
          </div>

          <button
            type="button"
            onClick={handleShare}
            disabled={copyState === "loading"}
            className="mt-4 flex w-full items-center justify-center gap-2 rounded-2xl border border-[#D5DBC9] bg-white py-3 text-sm font-medium text-[#314639] transition hover:bg-[#F4F7F0] disabled:opacity-60"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-4 w-4"
              aria-hidden="true"
            >
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
            </svg>
            {copyState === "loading" ? "링크 만드는 중..." : "내 짠돌이 레벨 공유하기"}
          </button>

          {copyMessage && (
            <p
              className={`mt-2 text-center text-xs ${
                copyState === "error" ? "text-[#D86A6A]" : "text-[#4A8A72]"
              }`}
            >
              {copyMessage}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

function StatCell({ value, label, valueClassName = "" }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-[#EEF1EA] bg-[#F7F8F2] py-3">
      <p className={`text-base font-bold text-[#1D2A21] ${valueClassName}`}>
        {value}
      </p>
      <p className="mt-0.5 text-[11px] text-[#9BA59D]">{label}</p>
    </div>
  );
}
