import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-lg font-semibold text-gray-800">페이지를 찾을 수 없어요.</p>
      <p className="text-sm text-gray-400">주소를 다시 확인해주세요.</p>
      <Link
        href="/"
        className="rounded-xl bg-[#8FA58D] px-5 py-2 text-sm font-medium text-white hover:bg-[#7C9279] transition-colors"
      >
        홈으로
      </Link>
    </div>
  );
}
