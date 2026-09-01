import { NextResponse } from 'next/server';
import { verifyAccessTokenEdge } from '@/lib/jwt';

const PROTECTED = ['/dashboard', '/coolingoff'];
const AUTH_ONLY = ['/auth/login', '/auth/signup'];

export async function middleware(request) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get('accessToken')?.value;

  const isProtected = PROTECTED.some((p) => pathname.startsWith(p));
  const isAuthOnly = AUTH_ONLY.some((p) => pathname.startsWith(p));
  const isRoot = pathname === '/';

  if (isProtected) {
    if (!token) {
      return NextResponse.redirect(new URL('/auth/login', request.url));
    }
    try {
      await verifyAccessTokenEdge(token);
    } catch {
      const refreshRes = await fetch(new URL('/api/auth/refresh', request.url), {
        method: 'POST',
        headers: { Cookie: request.headers.get('Cookie') || '' },
      });

      if (!refreshRes.ok) {
        return NextResponse.redirect(new URL('/auth/login', request.url));
      }

      const res = NextResponse.redirect(request.url);
      refreshRes.headers.getSetCookie().forEach((cookie) => {
        res.headers.append('Set-Cookie', cookie);
      });
      return res;
    }
  }

  if (isAuthOnly && token) {
    try {
      await verifyAccessTokenEdge(token);
      return NextResponse.redirect(new URL('/dashboard', request.url));
    } catch {
      // 만료된 토큰이면 로그인 페이지 그대로 진행
    }
  }

  // 루트(/): 로그인 상태면 대시보드, 비로그인이면 랜딩 페이지 그대로 노출
  if (isRoot && token) {
    try {
      await verifyAccessTokenEdge(token);
      return NextResponse.redirect(new URL('/dashboard', request.url));
    } catch {
      // 만료된 토큰이면 랜딩 페이지 그대로 진행
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/', '/dashboard/:path*', '/coolingoff/:path*', '/auth/login', '/auth/signup'],
};
