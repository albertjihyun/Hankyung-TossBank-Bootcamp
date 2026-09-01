import { useState, useEffect, useCallback } from "react";

export default function Toast({ message, type = "error", onDismiss }) {
  const [isLeaving, setIsLeaving] = useState(false);

  const dismiss = useCallback(() => {
    setIsLeaving(true);
    setTimeout(() => {
      onDismiss();
      setIsLeaving(false);
    }, 280);
  }, [onDismiss]);

  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(dismiss, 1500);
    return () => clearTimeout(timer);
  }, [message, dismiss]);

  if (!message) return null;

  const isSuccess = type === "success";

  return (
    <div
      className={`fixed top-6 inset-x-0 mx-auto w-fit z-[70] flex items-center gap-3 rounded-2xl px-5 py-3 shadow-lg ${
        isSuccess ? "bg-green-50 border border-green-200" : "bg-red-50 border border-red-200"
      }`}
      style={{ animation: isLeaving ? "slideUp 0.28s ease-in forwards" : "slideDown 0.3s ease-out" }}
    >
      <p className={`text-sm font-medium whitespace-nowrap ${isSuccess ? "text-green-600" : "text-red-500"}`}>
        {message}
      </p>
      <button
        onClick={dismiss}
        className={`text-lg leading-none ${isSuccess ? "text-green-300 hover:text-green-500" : "text-red-300 hover:text-red-500"}`}
      >
        ×
      </button>
    </div>
  );
}
