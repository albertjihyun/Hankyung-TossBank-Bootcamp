import { NextRequest, NextResponse } from "next/server";

// BFF: 브라우저의 /api/* 요청을 Spring(8080)으로 그대로 중계.
// 쿠키(access_token / refresh_token / SESSION)를 양방향 전달 → 단일 도메인/단일 터널 유지.
const SPRING = process.env.API_BASE ?? "http://localhost:8080";

export const dynamic = "force-dynamic";

async function handle(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  const { path } = await ctx.params;
  const url = `${SPRING}/api/${path.join("/")}${req.nextUrl.search}`;

  const headers = new Headers();
  const ct = req.headers.get("content-type");
  if (ct) headers.set("content-type", ct);
  const cookie = req.headers.get("cookie");
  if (cookie) headers.set("cookie", cookie); // 인증 쿠키 → Spring

  const init: RequestInit = { method: req.method, headers, redirect: "manual" };
  if (req.method !== "GET" && req.method !== "HEAD") {
    init.body = await req.text();
  }

  let upstream: Response;
  try {
    upstream = await fetch(url, init);
  } catch {
    return NextResponse.json({ error: "백엔드에 연결할 수 없습니다." }, { status: 502 });
  }

  // 204/205/304 는 본문을 가질 수 없음 → null body (안 그러면 NextResponse 생성 시 throw → 500)
  const noBody = [204, 205, 304].includes(upstream.status);
  const body = noBody ? null : await upstream.arrayBuffer();
  const res = new NextResponse(body, { status: upstream.status });
  const uct = upstream.headers.get("content-type");
  if (uct && !noBody) res.headers.set("content-type", uct);

  // Spring이 내려준 Set-Cookie(로그인/로그아웃/리프레시) → 브라우저로 전달
  const setCookies = upstream.headers.getSetCookie?.() ?? [];
  for (const c of setCookies) res.headers.append("set-cookie", c);

  return res;
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
