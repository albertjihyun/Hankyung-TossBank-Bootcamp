import { useState, useEffect, useRef } from "react";
import Image from 'next/image';
import Button from "@/components/ui/Button";

const CELEBRATION_CONFIG = {
  passed: {
    image: '/images/cooling_off/wallet.png',
    message: '지갑 수비 성공! 통장을 지켜냈어요.',
    width: 300,
    height: 300,
  },
  bought: {
    image: '/images/cooling_off/piggy_bank.png',
    message: '텅장 경보 발생! 하지만 소비는 확실한 행복이죠.',
    width: 200,
    height: 200,
  },
};

export default function CoolingOffDetailPanel({
  item,
  isOpen,
  onClose,
  onStatusChange,
  onDelete,
  onCelebrationEnd,
}) {
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [celebration, setCelebration] = useState(null);
  const timeLeftLabel = item?.time_left_label ?? 'D-day';
  const isDecided = item?.status === "passed" || item?.status === "bought";
  const isExpired = !isDecided && item?.expire_at != null && new Date(item.expire_at) <= new Date();
  const onCelebrationEndRef = useRef(onCelebrationEnd);
  useEffect(() => { onCelebrationEndRef.current = onCelebrationEnd; });

  const prevCelebrationRef = useRef(null);
  useEffect(() => {
    if (prevCelebrationRef.current && !celebration) {
      onCelebrationEndRef.current?.();
    }
    prevCelebrationRef.current = celebration;
  }, [celebration]);

  return (
    <>
      {celebration && (
        <>
          <style>{`
            @keyframes celebrationPop {
              0%   { transform: scale(0.3) rotate(-8deg); opacity: 0; }
              55%  { transform: scale(1.12) rotate(2deg); opacity: 1; }
              75%  { transform: scale(0.96) rotate(-1deg); }
              100% { transform: scale(1) rotate(0deg); opacity: 1; }
            }
          `}</style>
          <div
            className="fixed inset-0 z-[100] flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm cursor-pointer"
            onClick={() => setCelebration(null)}
          >
          <div style={{ animation: 'celebrationPop 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) forwards' }}>
            <Image
              src={CELEBRATION_CONFIG[celebration].image}
              alt="celebration"
              width={CELEBRATION_CONFIG[celebration].width}
              height={CELEBRATION_CONFIG[celebration].height}
              className="object-contain"
              style={{ height: "auto" }}
            />
          </div>
          <p className="mt-6 text-white text-xl font-bold text-center px-8 leading-relaxed">
            {CELEBRATION_CONFIG[celebration].message}
          </p>
          <p className="mt-4 text-white/50 text-sm">탭하여 닫기</p>
          </div>
        </>
      )}

      <div>
        <div
          className={`fixed inset-0 z-50 bg-black/20 backdrop-blur-[1px] transition-opacity duration-300 ${
            isOpen ? "opacity-100" : "opacity-0 pointer-events-none"
          }`}
          onClick={onClose}
        />

        <div
          className={`fixed z-[60] bg-white shadow-2xl flex flex-col transform transition-transform duration-300 ease-out
            bottom-0 left-0 right-0 max-h-[90vh] rounded-t-3xl
            md:top-0 md:right-0 md:left-auto md:bottom-auto md:h-screen md:max-h-none md:w-[480px] md:rounded-t-none md:rounded-l-3xl
            ${isOpen ? "translate-y-0" : "translate-y-full md:translate-y-0 md:translate-x-full"}
          `}
        >
          {/* 삭제 확인 모달 */}
          {isDeleteConfirmOpen && (
            <>
              <div
                className="absolute inset-0 z-10 bg-black/30 backdrop-blur-[1px] rounded-t-3xl md:rounded-t-none md:rounded-l-3xl"
                onClick={() => setIsDeleteConfirmOpen(false)}
              />
              <div className="absolute inset-0 z-20 flex items-center justify-center px-6">
                <div className="w-full max-w-[320px] bg-white rounded-2xl shadow-xl p-6 flex flex-col gap-4">
                  <div>
                    <p className="font-bold text-gray-900 text-base">항목을 삭제할까요?</p>
                    <p className="text-sm text-gray-400 mt-1">삭제하면 복구할 수 없어요.</p>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="secondary" fullWidth onClick={() => setIsDeleteConfirmOpen(false)}>
                      취소
                    </Button>
                    <Button
                      variant="neutral"
                      fullWidth
                      className="!bg-red-50 !text-red-400 hover:!bg-red-100"
                      onClick={() => { setIsDeleteConfirmOpen(false); onDelete(item.item_id); }}
                    >
                      삭제
                    </Button>
                  </div>
                </div>
              </div>
            </>
          )}

          {item && (
            <>
              <div className="px-7 pt-7 pb-4 flex items-stretch justify-between flex-shrink-0">
                <div>
                  <h2 className="text-xl font-bold text-gray-900">{item.name}</h2>
                  <p className="text-sm text-gray-400 mt-0.5">
                    {item.category_name}
                  </p>
                  <p className="font-bold text-[15px] text-gray-900 mt-1">
                    ₩{item.price.toLocaleString()}
                  </p>
                </div>
                <div className="flex flex-col items-end flex-shrink-0 ml-4">
                  <div className="flex items-center gap-2">
                    {isDecided ? (
                      <span className="bg-pink-100 text-pink-400 text-xs font-bold px-3 py-1 rounded-full">
                        완료
                      </span>
                    ) : isExpired ? (
                      <span className="bg-orange-100 text-orange-500 text-xs font-bold px-3 py-1 rounded-full whitespace-nowrap">
                        대기
                      </span>
                    ) : (
                      <span className="bg-green-100 text-green-600 text-xs font-bold px-3 py-1 rounded-full whitespace-nowrap">
                        {timeLeftLabel}
                      </span>
                    )}
                    <button
                      onClick={onClose}
                      className="text-gray-400 hover:text-gray-600 text-xl leading-none ml-1"
                    >
                      ×
                    </button>
                  </div>
                  {!isDecided && (
                    <button
                      type="button"
                      onClick={() => setIsDeleteConfirmOpen(true)}
                      className="mt-auto p-1.5 rounded-xl text-gray-400 hover:text-red-400 hover:bg-red-50 transition-colors"
                      aria-label="삭제"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="3 6 5 6 21 6" />
                        <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
                        <path d="M10 11v6M14 11v6" />
                        <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
                      </svg>
                    </button>
                  )}
                </div>
              </div>

              <div className="flex-1 overflow-y-auto px-7 min-h-0">
                <div className="flex flex-col gap-6">
                {!isDecided && (
                  <div className="rounded-xl bg-[#F8F1E4] px-4 py-3">
                    <p className="text-center text-sm text-[#7E6438]">
                      <span className="font-semibold text-[#5C4827]">
                        {new Date(item.expire_at).toLocaleString('ko-KR', {
                          timeZone: 'Asia/Seoul',
                          month: 'long',
                          day: 'numeric',
                          hour: 'numeric',
                          hour12: true,
                        })}
                      </span>
                      까지 멈춰볼게요
                    </p>
                  </div>
                )}
                <div className="relative w-full h-[220px] bg-white rounded-2xl overflow-hidden">
                  {item.image ? (
                    <Image
                      src={item.image}
                      alt={item.name}
                      fill
                      sizes="(max-width: 768px) 100vw, 500px"
                      className="object-contain"
                    />
                  ) : (
                    <div className="w-full h-full bg-gray-100 rounded-2xl" />
                  )}
                </div>

                <div className="flex flex-col gap-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium text-gray-700">이만큼 사고싶어요!</span>
                    <span className="text-gray-500">{item.impulse_score}/10</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-3 overflow-hidden">
                    <div
                      className="bg-[#5cbf7e] h-full rounded-full transition-all duration-500"
                      style={{ width: `${item.impulse_score * 10}%` }}
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-2 pb-6">
                  <p className="text-sm font-medium text-gray-700">등록 당시 메모</p>
                  <div className="bg-gray-100 rounded-xl px-4 py-3 min-h-[80px]">
                    <p className="text-sm text-gray-600 leading-relaxed">
                      {item.memo || "메모가 없습니다."}
                    </p>
                  </div>
                </div>
                </div>
              </div>

              {!isDecided && (
                <div className="px-7 py-5 flex gap-3 flex-shrink-0">
                  <Button
                    variant="primary"
                    fullWidth
                    onClick={() => { onStatusChange(item.item_id, 'PASSED'); setCelebration('passed'); }}
                  >
                    참았어요
                  </Button>
                  <Button
                    variant="neutral"
                    fullWidth
                    onClick={() => { onStatusChange(item.item_id, 'BOUGHT'); setCelebration('bought'); }}
                  >
                    샀어요
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </>
  );
}
