const store = new Map();

setInterval(() => {
  const now = Date.now();
  for (const [key, record] of store.entries()) {
    if (now > record.resetAt) store.delete(key);
  }
}, 60 * 1000);

// true = 허용, false = 차단
export function checkRateLimit(ip, key, limit, windowMs) {
  const now = Date.now();
  const storeKey = `${key}:${ip}`;
  const record = store.get(storeKey);
  if (!record || now > record.resetAt) {
    store.set(storeKey, { count: 1, resetAt: now + windowMs });
    return true;
  }
  if (record.count >= limit) return false;
  record.count++;
  return true;
}
