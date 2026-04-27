const { pool } = require('../db');

// resourceType: 'question' | 'answer'
function checkOwnerOrAdmin(resourceType) {
  return async (req, res, next) => {
    if (req.user.role === 'admin') return next();

    try {
      const id = req.params.id;
      let table, idColumn;

      if (resourceType === 'question') {
        table = 'questions';
        idColumn = 'id';
      } else {
        table = 'answers';
        idColumn = 'id';
      }

      const [rows] = await pool.query(
        `SELECT user_id FROM ${table} WHERE ${idColumn} = ?`,
        [id]
      );

      if (rows.length === 0) {
        return res.status(404).json({ message: '리소스를 찾을 수 없습니다.' });
      }

      if (rows[0].user_id !== req.user.id) {
        return res.status(403).json({ message: '권한이 없습니다.' });
      }

      next();
    } catch (err) {
      res.status(500).json({ message: '서버 오류' });
    }
  };
}

module.exports = checkOwnerOrAdmin;
