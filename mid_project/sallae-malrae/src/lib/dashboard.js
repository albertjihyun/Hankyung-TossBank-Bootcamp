import { query } from '@/lib/db';

export async function getDashboardData(userId) {
  const [userRows, statsRows, recentItems, expiredRows, chartRows] = await Promise.all([
    query('SELECT nickname, level FROM users WHERE id = ?', [userId]),
    query(
      `SELECT COALESCE(SUM(passed_count), 0) AS passed_count,
              COALESCE(SUM(bought_count), 0)  AS bought_count,
              COALESCE(SUM(saved_amount), 0)  AS saved_amount
       FROM user_monthly_stats
       WHERE user_id = ? AND year = YEAR(NOW()) AND month = MONTH(NOW())`,
      [userId],
    ),
    query(
      `SELECT i.id AS item_id, i.name, i.price, i.expire_at, i.image,
              i.category_id, c.name AS category_name,
              GREATEST(0, FLOOR(TIMESTAMPDIFF(SECOND, NOW(), i.expire_at) / 86400)) AS days_left
       FROM items i JOIN categories c ON i.category_id = c.id
       WHERE i.user_id = ? AND i.status = 'waiting' AND i.expire_at >= NOW()
       ORDER BY i.expire_at ASC LIMIT 3`,
      [userId],
    ),
    query(
      `SELECT COUNT(*) AS expired_count FROM items
       WHERE user_id = ? AND status = 'waiting' AND expire_at < NOW()`,
      [userId],
    ),
    query(
      `SELECT c.name, COUNT(*) AS count
       FROM items i JOIN categories c ON i.category_id = c.id
       WHERE i.user_id = ? AND i.status = 'passed'
         AND YEAR(i.decided_at) = YEAR(NOW()) AND MONTH(i.decided_at) = MONTH(NOW())
       GROUP BY i.category_id, c.name
       ORDER BY count DESC`,
      [userId],
    ),
  ]);

  const { nickname, level } = userRows[0];
  const stats = statsRows[0];
  const passedCount = Number(stats.passed_count);
  const boughtCount = Number(stats.bought_count);
  const savedAmount = Number(stats.saved_amount);

  const total = passedCount + boughtCount;
  const successRate = total === 0 ? 0 : Math.round((passedCount / total) * 1000) / 10;

  const categoryChart = chartRows.map((row) => ({
    name: row.name,
    count: Number(row.count),
    ratio: passedCount === 0 ? 0 : Math.round((Number(row.count) / passedCount) * 1000) / 10,
  }));

  return {
    user: { nickname, level },
    summary: { passed_count: passedCount, bought_count: boughtCount, saved_amount: savedAmount, success_rate: successRate },
    recentItems,
    expired_count: Number(expiredRows[0].expired_count),
    categoryChart,
  };
}
