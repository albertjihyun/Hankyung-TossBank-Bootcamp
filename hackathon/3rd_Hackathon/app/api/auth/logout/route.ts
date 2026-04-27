export async function POST() {
  const response = Response.json({ success: true, data: { success: true } });
  response.headers.set(
    'Set-Cookie',
    `${process.env.TOKEN_COOKIE_NAME || 'token'}=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax`
  );
  return response;
}
