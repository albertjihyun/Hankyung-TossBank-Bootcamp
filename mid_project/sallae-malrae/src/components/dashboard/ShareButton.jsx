"use client";

import { useState } from "react";

import LevelShareModal from "./LevelShareModal";

export default function ShareButton({ user, summary }) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        aria-label="레벨 공유하기"
        onClick={() => setOpen(true)}
        className="flex h-7 w-7 items-center justify-center rounded-full text-[#5D7A62] transition hover:bg-[#E8F1E9] hover:text-[#2E7D5B]"
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
          <circle cx="18" cy="5" r="3" />
          <circle cx="6" cy="12" r="3" />
          <circle cx="18" cy="19" r="3" />
          <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
          <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
        </svg>
      </button>

      <LevelShareModal
        open={open}
        onClose={() => setOpen(false)}
        user={user}
        summary={summary}
      />
    </>
  );
}
