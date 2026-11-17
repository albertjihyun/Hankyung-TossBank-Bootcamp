"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { withdraw } from "@/shared/api/auth";
import { ApiError } from "@/shared/api/client";
import { useAuthStore } from "@/shared/stores/authStore";
import { PageTitle } from "@/shared/ui/PageState";
import { Button } from "@/shared/ui/button";
import { Input } from "@/shared/ui/input";
import { Label } from "@/shared/ui/label";

/**
 * 회원 탈퇴 (M-17). 되돌릴 수 없으므로 무엇이 지워지고 무엇이 남는지 먼저 보여주고,
 * 비밀번호와 확인 체크를 둘 다 받아야 버튼이 켜진다.
 */
export default function WithdrawPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const [password, setPassword] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPending(true);
    setError(null);
    try {
      await withdraw(password);
      // 로그아웃과 같은 정리 — 이전 계정의 캐시가 다음 사용자에게 보이지 않게
      clearAuth();
      queryClient.clear();
      router.push("/");
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.displayMessage
          : "탈퇴를 처리하지 못했어요. 잠시 후 다시 시도해주세요.",
      );
      setPending(false);
    }
  };

  return (
    <div>
      <PageTitle>회원 탈퇴</PageTitle>

      <section className="mt-5 rounded-sm border bg-background p-5 text-sm leading-relaxed">
        <p className="font-semibold">탈퇴하면 다음 정보가 지워져요.</p>
        <ul className="mt-2 list-disc pl-5 text-muted-foreground">
          <li>장바구니, 찜, 배송지</li>
          <li>이메일, 닉네임 등 회원 정보</li>
          <li>AI 취향 프로필과 대화 기록</li>
        </ul>
        <p className="mt-4 font-semibold">다음 정보는 남아요.</p>
        <ul className="mt-2 list-disc pl-5 text-muted-foreground">
          <li>주문·취소·반품 기록 (전자상거래법에 따른 보존)</li>
          <li>작성한 리뷰 (작성자는 &quot;탈퇴회원&quot;으로 표시)</li>
        </ul>
        <p className="mt-4 text-muted-foreground">
          배송 중이거나 취소·반품을 처리 중인 주문이 있으면 탈퇴할 수 없어요.
          탈퇴 후에도 같은 이메일로 다시 가입할 수 있어요.
        </p>
      </section>

      <form onSubmit={handleSubmit} className="mt-5 flex max-w-sm flex-col gap-4">
        <div className="flex flex-col gap-2">
          <Label htmlFor="withdraw-password">비밀번호 확인</Label>
          <Input
            id="withdraw-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
          />
          위 내용을 확인했고, 탈퇴를 되돌릴 수 없다는 것에 동의해요.
        </label>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button
          type="submit"
          variant="destructive"
          disabled={!password || !agreed || pending}
        >
          {pending ? "처리 중..." : "탈퇴하기"}
        </Button>
      </form>
    </div>
  );
}
