const express = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const { pool } = require('../db');

const router = express.Router();

// POST /auth/register
router.post('/register', async (req, res) => {
  const { email, password, nickname, role } = req.body;
  if (!email || !password || !nickname) {
    return res.status(400).json({ message: '필수 항목을 입력해주세요.' });
  }

  const allowedRoles = ['user', 'admin'];
  const userRole = allowedRoles.includes(role) ? role : 'user';

  try {
    const hashedPassword = await bcrypt.hash(password, 10);
    await pool.query(
      'INSERT INTO users (email, password, nickname, role) VALUES (?, ?, ?, ?)',
      [email, hashedPassword, nickname, userRole]
    );
    res.status(201).json({ message: '회원가입 성공' });
  } catch (err) {
    if (err.code === 'ER_DUP_ENTRY') {
      return res.status(409).json({ message: '이미 사용 중인 이메일입니다.' });
    }
    res.status(500).json({ message: '서버 오류' });
  }
});

// POST /auth/login
router.post('/login', async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ message: '이메일과 비밀번호를 입력해주세요.' });
  }

  try {
    const [rows] = await pool.query('SELECT * FROM users WHERE email = ?', [email]);
    if (rows.length === 0) {
      return res.status(401).json({ message: '이메일 또는 비밀번호가 올바르지 않습니다.' });
    }

    const user = rows[0];
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.status(401).json({ message: '이메일 또는 비밀번호가 올바르지 않습니다.' });
    }

    const token = jwt.sign(
      { id: user.id, nickname: user.nickname, role: user.role },
      'devq_jwt_secret',
      { expiresIn: '7d' }
    );

    res.json({ token, user: { id: user.id, nickname: user.nickname, role: user.role } });
  } catch (err) {
    res.status(500).json({ message: '서버 오류' });
  }
});

module.exports = router;
