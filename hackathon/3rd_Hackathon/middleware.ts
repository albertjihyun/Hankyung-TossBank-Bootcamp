import { NextRequest, NextResponse } from 'next/server';
import { verifyToken } from '@/lib/auth';

const PROTECTED_PATHS = ['/lobby', '/room', '/battle', '/result'];
const COOKIE_NAME = process.env.TOKEN_COOKIE_NAME || 'token';

export async function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;

  if (pathname.startsWith('/api/')) return NextResponse.next();

  const token = req.cookies.get(COOKIE_NAME)?.value ?? null;
  const payload = token ? await verifyToken(token) : null;

  const isProtected = PROTECTED_PATHS.some((p) => pathname.startsWith(p));

  if (isProtected && !payload) {
    return NextResponse.redirect(new URL('/', req.url));
  }

  if (pathname === '/' && payload) {
    return NextResponse.redirect(new URL('/lobby', req.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
};
