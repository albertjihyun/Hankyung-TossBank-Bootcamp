import * as mariadb from 'mariadb';

if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL 환경변수가 설정되지 않았습니다.');
}

// Next.js dev 모드 hot reload 시 풀이 매 모듈 재로드마다 새로 생성되어
// connection 누수가 발생하는 것을 방지. production에서는 캐싱하지 않음.
const globalForDb = globalThis;

const url = new URL(process.env.DATABASE_URL);

const pool =
  globalForDb.__mariadbPool ??
  mariadb.createPool({
    host: url.hostname,
    port: Number(url.port) || 3306,
    user: decodeURIComponent(url.username),
    password: decodeURIComponent(url.password),
    database: url.pathname.slice(1),
    connectionLimit: 10,
    // BIGINT 컬럼·LAST_INSERT_ID를 Number로 반환 — NextResponse.json이 BigInt 직렬화에서
    // TypeError 내는 것을 회피. saved_amount/spent_amount 누적은 Number.MAX_SAFE_INTEGER 미만.
    bigIntAsNumber: true,
    insertIdAsNumber: true,
  });

if (process.env.NODE_ENV !== 'production') {
  globalForDb.__mariadbPool = pool;
}

// 단일 쿼리 — 풀에서 커넥션 받아 실행 후 자동 반납.
export async function query(sql, params) {
  const conn = await pool.getConnection();
  try {
    return await conn.query(sql, params);
  } finally {
    conn.release();
  }
}

// 트랜잭션이 필요한 경우 (ADR-003 결정 트랜잭션 등) — 호출 측에서
// conn.beginTransaction / commit / rollback / release를 직접 제어.
export async function getConnection() {
  return pool.getConnection();
}

export { pool };

