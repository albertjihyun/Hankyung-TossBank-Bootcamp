"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

import Header from "@/components/layout/Header";
import Button from "@/components/ui/Button";
import ErrorAlert from "@/components/ui/ErrorAlert";
import Input from "@/components/ui/Input";
import PasswordInput from "@/components/ui/PasswordInput";
import { ERROR_MESSAGES } from "@/constants/errors";

export default function SignupPage() {
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (isSubmitting) return;

    setErrorMessage("");

    if (!email || !nickname || !password || !passwordConfirm) {
      setErrorMessage(ERROR_MESSAGES.REQUIRED_FIELD);
      return;
    }
    if (password.length < 8) {
      setErrorMessage(ERROR_MESSAGES.PASSWORD_TOO_SHORT);
      return;
    }
    if (password !== passwordConfirm) {
      setErrorMessage("비밀번호가 일치하지 않습니다.");
      return;
    }

    setIsSubmitting(true);

    try {
      const res = await fetch("/api/auth/signup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, nickname, password }),
      });
      const body = await res.json();

      if (!body.success) {
        setErrorMessage(
          ERROR_MESSAGES[body.code] ?? "회원가입에 실패했습니다.",
        );
        return;
      }

      router.push("/auth/login?signup=success");
    } catch {
      setErrorMessage("네트워크 오류가 발생했습니다. 다시 시도해주세요.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center px-4 pt-20 pb-8">
      <Header variant="auth" />

      <div className="grid w-full max-w-3xl grid-cols-1 overflow-hidden lg:grid-cols-2 lg:rounded-2xl lg:bg-white lg:shadow-sm">
        <aside className="px-2 pb-2 lg:flex lg:flex-col lg:gap-8 lg:bg-gradient-to-br lg:from-[#EAF1EA] lg:to-[#D9E7DB] lg:px-10 lg:py-12 lg:pb-12">
          <div className="lg:mt-8">
            <p className="mb-2 text-xs font-semibold tracking-wide text-[#5D7A62] lg:mb-3 lg:text-sm">
              회원가입
            </p>
            <h2 className="text-xl font-bold leading-snug text-gray-900 lg:text-[26px]">
              잠깐 멈춤이,
              <br />
              의미있는 절약이 됩니다.
            </h2>
            <p className="mt-2 text-sm leading-relaxed text-gray-500 lg:mt-3 lg:text-gray-600">
              살래말래가 당신의 소비 습관을 함께 지켜드릴게요.
            </p>
          </div>

          <ul className="mt-0 hidden flex-col gap-2 text-sm text-[#3F5E4A] lg:flex">
            <li className="flex items-center gap-2">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[#8FA58D] text-xs text-white">
                ✓
              </span>
              충동구매 잠깐 멈추기
            </li>
            <li className="flex items-center gap-2">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[#8FA58D] text-xs text-white">
                ✓
              </span>
              진짜 필요한 건지 다시 보기
            </li>
          </ul>
        </aside>

        <div className="flex flex-col justify-center px-2 py-6 lg:px-10 lg:py-10">
          <form onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-3">
              <Input
                id="email"
                name="email"
                type="email"
                label="이메일"
                placeholder="hello@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
                autoFocus
              />

              <Input
                id="nickname"
                name="nickname"
                label="닉네임"
                placeholder="절약왕 (최대 20자)"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                required
                maxLength={20}
                autoComplete="nickname"
              />

              <PasswordInput
                id="password"
                name="password"
                label="비밀번호"
                placeholder="8자 이상"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
                autoComplete="new-password"
              />

              <PasswordInput
                id="passwordConfirm"
                name="passwordConfirm"
                label="비밀번호 확인"
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                required
                autoComplete="new-password"
              />
            </div>

            <div className="mt-3 min-h-[52px]">
              <ErrorAlert message={errorMessage} />
            </div>

            <Button
              type="submit"
              className="mt-6"
              fullWidth
              size="lg"
              disabled={isSubmitting}
            >
              {isSubmitting ? "가입 중..." : "가입하고 시작하기"}
            </Button>
          </form>

          <div className="mt-4 mb-2 flex items-center justify-center gap-2 text-xs">
            <span className="text-gray-500">이미 계정이 있으신가요?</span>

            <Link
              href="/auth/login"
              className="font-medium text-[#8FA58D] hover:underline"
            >
              로그인
            </Link>
          </div>
        </div>
      </div>
    </main>
  );
}
