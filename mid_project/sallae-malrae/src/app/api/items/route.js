import sharp from 'sharp';
import { query } from '@/lib/db';
import { getSessionUser } from '@/lib/auth';
import { successResponse, errorResponse } from '@/lib/response';
import { isPositiveInteger, isNonEmptyString } from '@/lib/validators';
import { uploadImage } from '@/lib/gcs';

// GET: status 파라미터로 WHERE 조건 + 정렬 결정. SQL 인젝션 방지용 whitelist.
const VALID_STATUS = new Set(['all', 'WAITING', 'BOUGHT', 'PASSED', 'EXPIRED']);

function buildListQuery(userId, statusParam) {
  const conds = ['i.user_id = ?'];
  const vals = [userId];
  let order;

  if (statusParam === 'WAITING') {
    conds.push("i.status = 'waiting'", 'i.expire_at >= NOW()');
    order = 'ORDER BY i.expire_at ASC, i.id ASC';
  } else if (statusParam === 'BOUGHT') {
    conds.push("i.status = 'bought'");
    order = 'ORDER BY i.decided_at DESC, i.id DESC';
  } else if (statusParam === 'PASSED') {
    conds.push("i.status = 'passed'");
    order = 'ORDER BY i.decided_at DESC, i.id DESC';
  } else if (statusParam === 'EXPIRED') {
    conds.push("i.status = 'waiting'", 'i.expire_at < NOW()');
    order = 'ORDER BY i.expire_at ASC, i.id ASC';
  } else {
    // 'all': 만료 미결정 → 진행 중 → 결정됨 (ADR-012)
    order = `ORDER BY
      CASE WHEN i.status = 'waiting' AND i.expire_at < NOW() THEN 0
           WHEN i.status = 'waiting' THEN 1
           ELSE 2 END ASC,
      i.expire_at ASC, i.id ASC`;
  }

  return { where: conds.join(' AND '), vals, order };
}

// GET /api/items — 목록 조회 (탭 필터 + counts + 페이지네이션)
export async function GET(request) {
  const user = getSessionUser(request);
  if (!user) return errorResponse('UNAUTHORIZED');

  const sp = new URL(request.url).searchParams;
  const statusParam = VALID_STATUS.has(sp.get('status')) ? sp.get('status') : 'all';

  const { where, vals, order } = buildListQuery(user.user_id, statusParam);

  let items, countRows;
  try {
    [items, countRows] = await Promise.all([
      query(
        `SELECT i.id AS item_id, i.name, i.price, i.category_id, c.name AS category_name,
                i.status, i.expire_at, i.decided_at, i.memo, i.impulse_score, i.image, i.created_at,
                GREATEST(0, FLOOR(TIMESTAMPDIFF(SECOND, NOW(), i.expire_at) / 86400)) AS days_left,
                CASE
                  WHEN FLOOR(TIMESTAMPDIFF(SECOND, NOW(), i.expire_at) / 86400) = 0
                    THEN 'D-day'
                  ELSE CONCAT('D-', FLOOR(TIMESTAMPDIFF(SECOND, NOW(), i.expire_at) / 86400))
                END AS time_left_label
         FROM items i JOIN categories c ON i.category_id = c.id
         WHERE ${where} ${order}`,
        vals,
      ),
      query(
        `SELECT
           COUNT(*) AS all_count,
           SUM(CASE WHEN status = 'waiting' AND expire_at >= NOW() THEN 1 ELSE 0 END) AS waiting,
           SUM(CASE WHEN status = 'bought'  THEN 1 ELSE 0 END) AS bought,
           SUM(CASE WHEN status = 'passed'  THEN 1 ELSE 0 END) AS passed,
           SUM(CASE WHEN status = 'waiting' AND expire_at < NOW() THEN 1 ELSE 0 END) AS expired
         FROM items WHERE user_id = ?`,
        [user.user_id],
      ),
    ]);
  } catch {
    return errorResponse('SERVER_ERROR');
  }

  const c = countRows[0];

  return successResponse(
    {
      items,
      counts: {
        all:     Number(c.all_count),
        waiting: Number(c.waiting),
        bought:  Number(c.bought),
        passed:  Number(c.passed),
        expired: Number(c.expired),
      },
    },
    'Items retrieved.',
  );
}

// expire_at 유효성 검사 — 정각 단위, 현재+1h ~ +30일
function isValidExpireAt(str) {
  const d = new Date(str);
  if (isNaN(d.getTime())) return false;
  if (d.getMinutes() !== 0 || d.getSeconds() !== 0 || d.getMilliseconds() !== 0) return false;
  const now = Date.now();
  return d.getTime() >= now + 60 * 60 * 1000 && d.getTime() <= now + 30 * 24 * 60 * 60 * 1000;
}

const ALLOWED_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);
const MAX_IMG_SIZE = 5 * 1024 * 1024; // 5MB

// POST /api/items — 새 항목 등록 (multipart/form-data)
export async function POST(request) {
  const user = getSessionUser(request);
  if (!user) return errorResponse('UNAUTHORIZED');

  let formData;
  try {
    formData = await request.formData();
  } catch {
    return errorResponse('REQUIRED_FIELD');
  }

  const name         = formData.get('name');
  const priceStr     = formData.get('price');
  const catIdStr     = formData.get('category_id');
  const expireAtStr  = formData.get('expire_at');
  const impulseStr   = formData.get('impulse_score');
  const memo         = formData.get('memo') || null;
  const imageFile    = formData.get('image');

  if (!isNonEmptyString(name) || !priceStr || !catIdStr || !expireAtStr || !impulseStr) {
    return errorResponse('REQUIRED_FIELD');
  }

  if (name.length > 100) return errorResponse('REQUIRED_FIELD');
  if (memo && memo.length > 500) return errorResponse('REQUIRED_FIELD');

  const price        = Number(priceStr);
  const categoryId   = Number(catIdStr);
  const impulseScore = Number(impulseStr);

  if (!isPositiveInteger(price))                                    return errorResponse('INVALID_PRICE');
  if (!isPositiveInteger(categoryId))                               return errorResponse('INVALID_CATEGORY');
  if (!Number.isInteger(impulseScore) || impulseScore < 1 || impulseScore > 10) return errorResponse('INVALID_IMPULSE_SCORE');
  if (!isValidExpireAt(expireAtStr))                                return errorResponse('INVALID_EXPIRE_AT');

  const cats = await query('SELECT id FROM categories WHERE id = ?', [categoryId]);
  if (cats.length === 0) return errorResponse('INVALID_CATEGORY');

  // 이미지 — sharp 정규화 후 GCS 업로드
  let imagePath = null;
  if (imageFile && imageFile.size > 0) {
    if (!ALLOWED_MIME_TYPES.has(imageFile.type) || imageFile.size > MAX_IMG_SIZE) {
      return errorResponse('INVALID_IMAGE');
    }
    const raw = Buffer.from(await imageFile.arrayBuffer());
    let processed;
    try {
      processed = await sharp(raw)
        .resize({ width: 1024, height: 1024, fit: 'inside', withoutEnlargement: true })
        .webp({ quality: 80 })
        .toBuffer();
    } catch {
      return errorResponse('INVALID_IMAGE');
    }
    try {
      imagePath = await uploadImage(processed);
    } catch {
      return errorResponse('INVALID_IMAGE');
    }
  }

  const expireDate = new Date(expireAtStr);

  const result = await query(
    `INSERT INTO items (user_id, category_id, name, price, image, memo, impulse_score, expire_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [user.user_id, categoryId, name, price, imagePath, memo, impulseScore, expireDate],
  );

  const [newItem] = await query(
    `SELECT i.id AS item_id, i.name, i.price, i.category_id, c.name AS category_name,
            i.status, i.expire_at, i.decided_at, i.memo, i.impulse_score, i.image, i.created_at
     FROM items i JOIN categories c ON i.category_id = c.id
     WHERE i.id = ?`,
    [result.insertId],
  );

  return successResponse(newItem, 'Item created.', 201);
}
