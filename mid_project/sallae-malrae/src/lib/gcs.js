import { Storage } from '@google-cloud/storage';
import { randomUUID } from 'crypto';

const storage = new Storage();
const bucket = storage.bucket(process.env.GCS_BUCKET_NAME);

// sharp로 처리된 WebP buffer를 GCS에 업로드하고 공개 URL 반환.
export async function uploadImage(buffer) {
  const filename = `${randomUUID()}.webp`;
  const file = bucket.file(filename);
  await file.save(buffer, { contentType: 'image/webp' });
  return `https://storage.googleapis.com/${process.env.GCS_BUCKET_NAME}/${filename}`;
}
