const express = require('express');
const { pool } = require('../db');
const verifyToken = require('../middleware/verifyToken');
const checkOwnerOrAdmin = require('../middleware/checkOwnerOrAdmin');

const router = express.Router();

// GET /api/questions - 질문 목록 (최신순)
router.get('/', async (req, res) => {
  try {
    const [rows] = await pool.query(`
      SELECT q.id, q.title, q.tag, q.created_at, u.nickname
      FROM questions q
      JOIN users u ON q.user_id = u.id
      ORDER BY q.created_at DESC
    `);
    res.json(rows);
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

// GET /api/questions/:id - 질문 상세 + 답변 목록
router.get('/:id', async (req, res) => {
  try {
    const { id } = req.params;

    const [qRows] = await pool.query(
      'SELECT q.*, u.nickname FROM questions q JOIN users u ON q.user_id = u.id WHERE q.id = ?',
      [id]
    );
    if (qRows.length === 0) {
      return res.status(404).json({ message: '질문을 찾을 수 없습니다.' });
    }

    const [aRows] = await pool.query(
      'SELECT a.*, u.nickname FROM answers a JOIN users u ON a.user_id = u.id WHERE a.question_id = ? ORDER BY a.created_at ASC',
      [id]
    );

    res.json({ question: qRows[0], answers: aRows });
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

// POST /api/questions - 질문 작성
router.post('/', verifyToken, async (req, res) => {
  const { title, body, tag } = req.body;
  if (!title || !body) {
    return res.status(400).json({ message: '제목과 본문을 입력해주세요.' });
  }

  try {
    const [result] = await pool.query(
      'INSERT INTO questions (user_id, title, body, tag) VALUES (?, ?, ?, ?)',
      [req.user.id, title, body, tag || null]
    );
    res.status(201).json({ id: result.insertId });
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

// PUT /api/questions/:id - 질문 수정
router.put('/:id', verifyToken, checkOwnerOrAdmin('question'), async (req, res) => {
  const { title, body, tag } = req.body;
  if (!title || !body) {
    return res.status(400).json({ message: '제목과 본문을 입력해주세요.' });
  }

  try {
    await pool.query(
      'UPDATE questions SET title = ?, body = ?, tag = ? WHERE id = ?',
      [title, body, tag || null, req.params.id]
    );
    res.json({ message: '질문이 수정되었습니다.' });
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

// DELETE /api/questions/:id - 질문 삭제 (CASCADE로 답변도 삭제)
router.delete('/:id', verifyToken, checkOwnerOrAdmin('question'), async (req, res) => {
  try {
    await pool.query('DELETE FROM questions WHERE id = ?', [req.params.id]);
    res.json({ message: '질문이 삭제되었습니다.' });
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

module.exports = router;
