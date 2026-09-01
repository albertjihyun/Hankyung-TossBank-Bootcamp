import { query } from '@/lib/db';

// shareToken으로 공유자 통계를 조회한다.
// API 라우트와 공유 페이지 SSR 양쪽에서 사용.
// 유효하지 않은 토큰이면 null 반환. DB 에러는 호출자가 처리.
export async function getShareData(shareToken) {
  const userRows = await query(
    'SELECT id, nickname, level FROM users WHERE share_token = ?',
    [shareToken],
  );
  if (userRows.length === 0) return null;

  const user = userRows[0];

  const [statsRows, chartRows] = await Promise.all([
    query(
      `SELECT COALESCE(SUM(passed_count), 0) AS passed_count,
              COALESCE(SUM(bought_count), 0)  AS bought_count,
              COALESCE(SUM(saved_amount), 0)  AS saved_amount
       FROM user_monthly_stats
       WHERE user_id = ? AND year = YEAR(NOW()) AND month = MONTH(NOW())`,
      [user.id],
    ),
    query(
      `SELECT c.name, COUNT(*) AS count
       FROM items i JOIN categories c ON i.category_id = c.id
       WHERE i.user_id = ? AND i.status = 'passed'
         AND YEAR(i.decided_at) = YEAR(NOW()) AND MONTH(i.decided_at) = MONTH(NOW())
       GROUP BY i.category_id, c.name
       ORDER BY count DESC`,
      [user.id],
    ),
  ]);

  const stats = statsRows[0];
  const passedCount = Number(stats.passed_count);
  const boughtCount = Number(stats.bought_count);
  const savedAmount = Number(stats.saved_amount);

  const total = passedCount + boughtCount;
  const successRate =
    total === 0 ? 0 : Math.round((passedCount / total) * 1000) / 10;

  const categoryChart = chartRows.map((row) => ({
    name: row.name,
    ratio:
      passedCount === 0
        ? 0
        : Math.round((Number(row.count) / passedCount) * 1000) / 10,
  }));

  return {
    nickname: user.nickname,
    level: user.level,
    summary: {
      passed_count: passedCount,
      bought_count: boughtCount,
      saved_amount: savedAmount,
      success_rate: successRate,
    },
    categoryChart,
  };
}
