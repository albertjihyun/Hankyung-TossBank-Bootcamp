'use client';

import { useState } from 'react';
import { LoginForm } from '@/components/auth/LoginForm';
import { RegisterForm } from '@/components/auth/RegisterForm';

export default function HomePage() {
  const [mode, setMode] = useState<'login' | 'register'>('login');

  return (
    <main className="flex min-h-screen items-center justify-center px-6 py-10">
      <div className="grid w-full max-w-5xl gap-10 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="rounded-[2rem] border border-white/10 bg-white/5 p-8 shadow-2xl backdrop-blur md:p-12">
          <p className="mb-4 inline-flex rounded-full border border-sky-400/30 bg-sky-400/10 px-4 py-1 text-sm text-sky-200">
            3분 안에 끝나는 실시간 주식 배틀
          </p>
          <h1 className="text-4xl font-black tracking-tight text-white md:text-6xl">
            StockBattle
          </h1>
          <p className="mt-6 max-w-xl text-base leading-7 text-slate-300 md:text-lg">
            친구들과 같은 방에 들어와 4개의 종목을 사고팔며 수익률로 승부하세요.
            방 생성, 대기, 실시간 가격 변동, 결과 화면까지 한 번에 이어지는 게임형 투자 시뮬레이션입니다.
          </p>
          <div className="mt-10 grid gap-4 sm:grid-cols-3">
            {[
              ['실시간', '10초마다 가격 갱신'],
              ['빠른 진행', '한 판 3분'],
              ['최대 4명', '대기방 즉시 시작'],
            ].map(([title, desc]) => (
              <div key={title} className="rounded-2xl border border-white/10 bg-slate-950/60 p-4">
                <div className="text-lg font-semibold text-white">{title}</div>
                <div className="mt-1 text-sm text-slate-400">{desc}</div>
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-[2rem] border border-white/10 bg-slate-950/80 p-8 shadow-2xl backdrop-blur">
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-white">
              {mode === 'login' ? '로그인' : '회원가입'}
            </h2>
            <p className="mt-2 text-sm text-slate-400">
              {mode === 'login'
                ? '기존 계정으로 바로 로비에 입장하세요.'
                : '이메일과 닉네임으로 빠르게 시작할 수 있어요.'}
            </p>
          </div>
          {mode === 'login' ? (
            <LoginForm onSwitchToRegister={() => setMode('register')} />
          ) : (
            <RegisterForm onSwitchToLogin={() => setMode('login')} />
          )}
        </section>
      </div>
    </main>
  );
}
