"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";

export default function SignupPage() {
  const { signup } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (password.length < 6) {
      setError("비밀번호는 6자 이상이어야 합니다.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await signup(email, password, name);
      router.push("/");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "회원가입에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <h1 className="serif auth-title">회원가입</h1>
        <form onSubmit={onSubmit} className="auth-form">
          <label>
            이메일
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
          </label>
          <label>
            이름
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} required maxLength={50} />
          </label>
          <label>
            비밀번호 (6자 이상)
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="new-password" />
          </label>
          {error && <p className="form-error">{error}</p>}
          <button type="submit" className="btn primary full" disabled={busy}>
            {busy ? "가입 중…" : "가입하고 시작하기"}
          </button>
        </form>
        <p className="auth-alt">
          이미 계정이 있으신가요? <Link href="/login">로그인</Link>
        </p>
      </div>
    </main>
  );
}
