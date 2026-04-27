const express = require('express');
const path = require('path');

const app = express();
const PORT = 3000;


// =============================
// 1. static 폴더 연결
// =============================

app.use('/static', express.static(path.join(__dirname, 'static')));


// =============================
// 2. HTML 라우팅
// =============================

// 홈
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'templates', 'index.html'));
});

// 투자 주체
app.get('/investors', (req, res) => {
  res.sendFile(path.join(__dirname, 'templates', 'investors.html'));
});

// 뉴스
app.get('/news', (req, res) => {
  res.sendFile(path.join(__dirname, 'templates', 'news.html'));
});

// 종목 TOP10
app.get('/themes', (req, res) => {
  res.sendFile(path.join(__dirname, 'templates', 'themes.html'));
});

// MBTI 검사
app.get('/mbti', (req, res) => {
  res.sendFile(path.join(__dirname, 'templates', 'mbti.html'));
});

// MBTI 결과
app.get('/diagnosis', (req, res) => {
  res.sendFile(path.join(__dirname, 'templates', 'diagnosis.html'));
});

// 맞춤 추천
app.get('/recommend', (req, res) => {
  res.sendFile(path.join(__dirname, 'templates', 'recommend.html'));
});

// 인기 상품
app.get('/popular', (req, res) => {
  res.sendFile(path.join(__dirname, 'templates', 'popular.html'));
});


// =============================
// 3. 서버 실행
// =============================

app.listen(PORT, () => {
  console.log("=======================================");
  console.log("   토스증권 투자 서비스 서버 실행");
  console.log("   URL: http://localhost:" + PORT);
  console.log("   static: /static");
  console.log("=======================================");
});