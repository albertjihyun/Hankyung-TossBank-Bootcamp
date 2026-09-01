import Image from "next/image";

export default function StepCard({ step, title, subtitle, description, isActive, imageSrc, imageStyle }) {
  const bg = isActive
    ? "bg-brand-gray text-white"
    : step === 1
    ? "bg-[#c4debe] text-[#3a3a3a]"
    : "bg-[#e9f0e9] text-[#3a3a3a]";

  return (
    <div
      className={`rounded-3xl relative flex flex-col justify-end shadow-lg overflow-hidden ${bg}`}
      style={{ width: "360px", height: "440px" }}
    >
      {/* 이미지 — imageStyle로 위치/크기 자유 조절 */}
      {imageSrc && (
        <div className="absolute" style={imageStyle}>
          <Image
            src={imageSrc}
            alt=""
            fill
            sizes="360px"
            className="object-contain"
            loading="lazy"
          />
        </div>
      )}

      {/* 텍스트 영역 — 이미지 위에 */}
      <div className="relative z-10 px-8 pb-8">
        <div
          className={`w-14 h-14 rounded-full flex items-center justify-center text-xl font-bold mb-4 ${
            isActive ? "bg-white/20 text-white" : "bg-white text-[#3a3a3a] shadow-sm"
          }`}
        >
          0{step}
        </div>

        <div className="flex items-baseline gap-2 mb-2">
          <p className="font-bold text-3xl leading-tight">{title}</p>
          <p className={`text-base ${isActive ? "text-white/70" : "text-gray-400"}`}>
            {subtitle}
          </p>
        </div>

        <p className={`text-sm leading-relaxed ${isActive ? "text-white/70" : "text-gray-500"}`}>
          {description}
        </p>
      </div>
    </div>
  );
}
