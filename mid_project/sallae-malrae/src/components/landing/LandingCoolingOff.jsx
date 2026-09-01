import Image from "next/image";

export default function LandingCoolingOff() {
  return (
    <section
      id="cooling-off-section"
      className="landing-section snap-section relative min-h-screen flex items-center justify-center overflow-hidden"
    >
      <div className="section-bg absolute inset-0" />
      <div className="section-content relative z-10 w-full max-w-4xl mx-auto px-6">
        <div className="cooling-card relative bg-white rounded-3xl px-8 py-10 border border-blue-100 shadow-2xl">
          {/* 아이스큐브 데코 */}
          <div
            aria-hidden="true"
            className="cooling-deco absolute -top-10 -right-9 md:-top-25 md:-right-25 pointer-events-none w-[120px] h-[120px] md:w-[230px] md:h-[230px]"
          >
            <Image
              src="/images/landing_page/ice_cube.png"
              alt=""
              fill
              className="object-contain"
              sizes="(max-width: 768px) 110px, 230px"
            />
          </div>
          {/* 온도계 데코 */}
          <div
            aria-hidden="true"
            className="cooling-deco absolute -bottom-8 -left-10 md:-bottom-25 md:-left-30 pointer-events-none rotate-[-15deg] w-[110px] h-[110px] md:w-[250px] md:h-[250px]"
          >
            <Image
              src="/images/landing_page/thermometer.png"
              alt=""
              fill
              className="object-contain"
              sizes="(max-width: 768px) 110px, 250px"
            />
          </div>

          <p className="cooling-text text-xs text-gray-400 mb-5 font-mono tracking-widest">
            [ cooling - off ]
          </p>
          <h2 className="cooling-text text-xl md:text-4xl font-bold mb-1 leading-snug">
            사고 나서 후회하지 말고,
          </h2>
          <h2 className="cooling-text text-xl md:text-4xl font-bold mb-8 leading-snug">
            사기 전에&nbsp;
            <span className="text-[#2f80e0]">식히세요.</span>
          </h2>

          <div className="cooling-text w-full px-6 py-5 bg-gray-100 rounded-xl text-center space-y-3">
            <div>
              <p>
                <span className="font-bold text-gray-800 text-xl tracking-tight">cool off</span>
                <span className="text-gray-300 mx-2">|</span>
                <span className="italic text-gray-600 text-base">&ldquo;차분해지다, 덜 흥분하다&rdquo;</span>
              </p>
              <p className="text-xs text-gray-400 mt-1">— Oxford Learner&apos;s Dictionaries</p>
            </div>
            <hr className="border-gray-200" />
            <p className="text-xs font-medium text-gray-400 leading-relaxed">
              살래말래의 쿨링오프는 구매 욕구가 강하게 올라왔을 때 즉시 결제하지 않고,<br className="hidden sm:block" />
              감정이 가라앉은 뒤 다시 판단하는 과정입니다.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
