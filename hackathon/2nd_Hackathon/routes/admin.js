const express = require('express');
const { pool } = require('../db');

const router = express.Router();

// GET /api/admin/users - 전체 회원 목록 (password 제외)
router.get('/users', async (req, res) => {
  try {
    const [rows] = await pool.query(
      'SELECT id, email, nickname, role, created_at FROM users ORDER BY id ASC'
    );
    res.json(rows);
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

// DELETE /api/admin/users/:id - 회원 강제 삭제
router.delete('/users/:id', async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT id FROM users WHERE id = ?', [req.params.id]);
    if (rows.length === 0) {
      return res.status(404).json({ message: '회원을 찾을 수 없습니다.' });
    }
    await pool.query('DELETE FROM users WHERE id = ?', [req.params.id]);
    res.json({ message: '회원이 삭제되었습니다.' });
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

// GET /api/admin/questions - 전체 질문 목록
router.get('/questions', async (req, res) => {
  try {
    const [rows] = await pool.query(`
      SELECT q.*, u.nickname
      FROM questions q
      JOIN users u ON q.user_id = u.id
      ORDER BY q.id ASC
    `);
    res.json(rows);
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

module.exports = router;
