const express = require('express');
const { pool } = require('../db');
const verifyToken = require('../middleware/verifyToken');
const checkOwnerOrAdmin = require('../middleware/checkOwnerOrAdmin');

const router = express.Router({ mergeParams: true }); // :id (question_id) 접근을 위해

// POST /api/questions/:id/answers - 답변 작성
router.post('/', verifyToken, async (req, res) => {
  const { body } = req.body;
  const questionId = req.params.id;

  if (!body) {
    return res.status(400).json({ message: '답변 내용을 입력해주세요.' });
  }

  try {
    // 질문 존재 확인
    const [qRows] = await pool.query('SELECT id FROM questions WHERE id = ?', [questionId]);
    if (qRows.length === 0) {
      return res.status(404).json({ message: '질문을 찾을 수 없습니다.' });
    }

    await pool.query(
      'INSERT INTO answers (question_id, user_id, body) VALUES (?, ?, ?)',
      [questionId, req.user.id, body]
    );
    res.status(201).json({ message: '답변이 등록되었습니다.' });
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

// DELETE /api/answers/:id - 답변 삭제
router.delete('/:id', verifyToken, checkOwnerOrAdmin('answer'), async (req, res) => {
  try {
    await pool.query('DELETE FROM answers WHERE id = ?', [req.params.id]);
    res.json({ message: '답변이 삭제되었습니다.' });
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

module.exports = router;
