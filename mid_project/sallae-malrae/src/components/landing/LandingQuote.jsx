export default function LandingQuote() {
  return (
    <section
      id="quote"
      className="landing-section snap-section relative min-h-screen flex items-center justify-center overflow-hidden"
    >
      <div className="section-bg absolute inset-0" />
      <div className="section-content relative z-10 text-white text-center px-4 md:px-6 mx-4 md:mx-auto bg-brand-dark w-[1440px] rounded-3xl py-10 md:py-16">
        <p className="text-[12px] md:text-4xl font-bold leading-relaxed mb-6">
          &ldquo;
          <span className="underline decoration-dotted decoration-1.5 md:decoration-4 underline-offset-6 md:underline-offset-14">
            사고 싶은 마음
          </span>
          은 순간이지만,&nbsp;{" "}
          <span className="underline decoration-dotted decoration-1.5 md:decoration-4 underline-offset-6 md:underline-offset-14">
            텅 빈 잔고
          </span>
          는 한 달을 갑니다.&rdquo;
        </p>
        <p className="text-[12px] md:text-4xl font-bold leading-relaxed">
          당신의 내일을 지키는 냉정하고 차가운 시간,&nbsp;
          <span className="underline decoration-dotted decoration-1.5 md:decoration-4 underline-offset-6 md:underline-offset-14">
            쿨링오프
          </span>
          .
        </p>
      </div>
    </section>
  );
}
