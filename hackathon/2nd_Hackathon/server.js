const express = require('express');
const cors = require('cors');

const authRouter     = require('./routes/auth');
const questionRouter = require('./routes/questions');
const answerRouter   = require('./routes/answers');
const adminRouter    = require('./routes/admin');
const verifyToken    = require('./middleware/verifyToken');
const requireAdmin   = require('./middleware/requireAdmin');

const app = express();

app.use(cors());
app.use(express.json());

// 라우터 마운트
app.use('/auth', authRouter);
app.use('/api/questions', questionRouter);
app.use('/api/questions/:id/answers', answerRouter);   // POST 답변 작성
app.use('/api/answers', answerRouter);                  // DELETE 답변 삭제
app.use('/api/admin', verifyToken, requireAdmin, adminRouter);

// 정적 파일 서빙 (프론트엔드)
app.use(express.static('public'));

const PORT = 3000;

app.listen(PORT, () => {
  console.log(`DevQ 서버 실행 중: http://localhost:${PORT}`);
});
