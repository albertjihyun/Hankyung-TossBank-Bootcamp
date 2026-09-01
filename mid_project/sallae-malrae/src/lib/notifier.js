import { query } from './db.js';
import { sendMail } from './mailer.js';

// 현재 시각을 정각으로 내림 (분/초/ms = 0)
function floorToHour(date) {
  const d = new Date(date);
  d.setMinutes(0, 0, 0);
  return d;
}

function formatKoreanDatetime(date) {
  return new Date(date).toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

async function sendAndLog(itemId, type, emailFn) {
  let status = 'success';
  let errorMsg = null;
  try {
    await emailFn();
  } catch (err) {
    status = 'fail';
    errorMsg = err.message?.slice(0, 500) ?? 'Unknown error';
  }
  // INSERT IGNORE: UNIQUE(item_id, type) 충돌 시 조용히 스킵 (이미 발송된 것)
  // DB 장애로 로그 저장 실패해도 다음 항목 발송은 계속 진행.
  try {
    await query(
      `INSERT IGNORE INTO email_logs (item_id, type, status, error_msg)
       VALUES (?, ?, ?, ?)`,
      [itemId, type, status, errorMsg],
    );
  } catch (dbErr) {
    console.error(`[notifier] email_logs INSERT 실패 (item_id=${itemId}, type=${type}):`, dbErr.message);
  }
}

export async function sendDueNotifications(now) {
  const grid = floorToHour(now);
  const baseUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:3000';

  // before_24h: 24시간 후 만료되는 항목 중 미발송 + 컷오프 조건
  // 컷오프: expire_at - 24h > created_at + 1h (등록 직후 알림 방지, ADR-006)
  const before24hRows = await query(
    `SELECT i.id, i.name, i.expire_at, u.email, u.nickname
     FROM items i
     JOIN users u ON i.user_id = u.id
     LEFT JOIN email_logs el ON el.item_id = i.id AND el.type = 'before_24h'
     WHERE i.status = 'waiting'
       AND i.expire_at - INTERVAL 24 HOUR = ?
       AND el.id IS NULL
       AND i.expire_at - INTERVAL 24 HOUR > i.created_at + INTERVAL 1 HOUR`,
    [grid],
  );

  for (const row of before24hRows) {
    await sendAndLog(row.id, 'before_24h', () =>
      sendMail({
        to: row.email,
        subject: '[살래말래] 구매 결정 시간이 24시간 남았어요',
        text: `안녕하세요, ${row.nickname}님!\n\n등록하신 항목 "${row.name}"의 쿨링오프 시간이 24시간 후인 ${formatKoreanDatetime(row.expire_at)}에 끝납니다.\n\n아래 링크에서 최종 결정을 내려주세요.\n${baseUrl}/coolingoff`,
      }),
    );
  }

  // expire: 지금 만료되는 항목 중 미발송
  const expireRows = await query(
    `SELECT i.id, i.name, i.expire_at, u.email, u.nickname
     FROM items i
     JOIN users u ON i.user_id = u.id
     LEFT JOIN email_logs el ON el.item_id = i.id AND el.type = 'expire'
     WHERE i.status = 'waiting'
       AND i.expire_at = ?
       AND el.id IS NULL`,
    [grid],
  );

  for (const row of expireRows) {
    await sendAndLog(row.id, 'expire', () =>
      sendMail({
        to: row.email,
        subject: '[살래말래] 쿨링오프 시간이 끝났어요',
        text: `안녕하세요, ${row.nickname}님!\n\n등록하신 항목 "${row.name}"의 쿨링오프 시간이 끝났습니다.\n\n아래 링크에서 최종 결정(살래 / 말래)을 내려주세요.\n${baseUrl}/coolingoff`,
      }),
    );
  }

  return {
    before24h: before24hRows.length,
    expire: expireRows.length,
  };
}
