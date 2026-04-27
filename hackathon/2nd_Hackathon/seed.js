const bcrypt = require('bcrypt');
const { pool } = require('./db');

async function seed() {
  // ── 1. 유저 ──────────────────────────────────────────────
  const pw = await bcrypt.hash('1234', 10);

  await pool.query(`
    INSERT INTO users (email, password, nickname, role) VALUES
      ('admin@devq.com',   '${pw}', '관리자',    'admin'),
      ('alice@devq.com',   '${pw}', 'alice',     'user'),
      ('bob@devq.com',     '${pw}', 'bob',       'user'),
      ('charlie@devq.com', '${pw}', 'charlie',   'user')
  `);
  console.log('✅ users 삽입 완료');

  // ── 2. 질문 ──────────────────────────────────────────────
  await pool.query(`
    INSERT INTO questions (user_id, title, body, tag) VALUES
      (2, 'JavaScript에서 async/await와 Promise의 차이가 뭔가요?',
          'async/await를 쓰면 코드가 동기처럼 보이던데,\n내부적으로 Promise와 어떻게 다른지 궁금합니다.\n실무에서 어떤 걸 쓰는 게 좋을까요?',
          'javascript,async'),

      (3, 'Node.js에서 CORS 에러 해결 방법',
          'Express 서버에 프론트에서 fetch 요청을 보내면\nAccess-Control-Allow-Origin 에러가 납니다.\ncors 패키지 말고 다른 방법도 있나요?',
          'node,express,cors'),

      (2, 'MariaDB vs MySQL 실무에서 뭘 써야 하나요?',
          '개인 프로젝트에 DB를 고르는 중인데\nMariaDB와 MySQL의 차이를 잘 모르겠습니다.\n성능이나 라이센스 측면에서 어떤 게 나을까요?',
          'database,mariadb,mysql'),

      (4, 'Tailwind CSS 반응형 적용이 안 됩니다',
          'md:hidden 클래스를 붙였는데 모바일에서도 계속 보입니다.\nCDN 방식으로 쓰고 있는데 혹시 CDN에서는 안 되는 건가요?',
          'css,tailwind'),

      (3, 'JWT 토큰 만료 처리 어떻게 하시나요?',
          '로그인 후 토큰이 만료됐을 때 자동으로 로그아웃 처리를 하고 싶은데\n프론트에서 어떻게 감지하고 처리하는 게 베스트 프랙티스인가요?',
          'jwt,auth,javascript')
  `);
  console.log('✅ questions 삽입 완료');

  // ── 3. 답변 ──────────────────────────────────────────────
  await pool.query(`
    INSERT INTO answers (question_id, user_id, body) VALUES
      (1, 3, 'async/await는 Promise의 문법적 설탕(syntactic sugar)입니다.\n내부적으로는 똑같이 Promise를 사용하고요.\n가독성이 훨씬 좋아서 실무에서는 async/await를 주로 씁니다.'),

      (1, 4, 'Promise.all()처럼 병렬 처리가 필요할 때는 Promise를 직접 쓰는 게 낫고\n순차 처리는 async/await가 훨씬 읽기 편합니다.'),

      (2, 2, 'cors 패키지가 제일 간단합니다.\napp.use(cors()) 한 줄이면 해결되고\n특정 도메인만 허용하려면 origin 옵션 넣으면 됩니다.'),

      (2, 4, '직접 헤더를 설정할 수도 있습니다.\nres.setHeader("Access-Control-Allow-Origin", "*") 이렇게요.\n하지만 cors 패키지 쓰는 게 훨씬 편합니다.'),

      (3, 2, 'MariaDB는 MySQL 창시자가 만든 오픈소스 포크라 호환성이 거의 완벽합니다.\n라이센스가 자유롭고 성능도 비슷하거나 더 나은 경우도 있어서\n개인 프로젝트엔 MariaDB 추천드립니다.'),

      (5, 2, '프론트에서 fetch 응답이 401이면 localStorage 토큰을 지우고\nlogin 페이지로 리다이렉트하는 방식이 일반적입니다.\naxios 쓰면 인터셉터로 전역 처리도 가능합니다.')
  `);
  console.log('✅ answers 삽입 완료');

  console.log('\n🎉 시드 데이터 삽입 완료!');
  console.log('로그인 계정 (비밀번호 모두 1234):');
  console.log('  관리자 : admin@devq.com');
  console.log('  일반유저: alice@devq.com / bob@devq.com / charlie@devq.com');

  process.exit(0);
}

seed().catch(err => {
  console.error('❌ 시드 실패:', err.message);
  process.exit(1);
});
