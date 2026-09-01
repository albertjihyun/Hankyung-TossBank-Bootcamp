export default function ErrorAlert({ message, className = "" }) {
  if (!message) return null;

  return (
    <div
      role="alert"
      className={`flex items-start gap-2 rounded-xl border border-[#F2C2C2] bg-[#FDECEC] px-4 py-3 ${className}`}
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="mt-0.5 h-4 w-4 shrink-0 text-[#D96C6C]"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <p className="text-sm text-[#B84A4A]">{message}</p>
    </div>
  );
}
