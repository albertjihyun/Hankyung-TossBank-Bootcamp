function requireAdmin(req, res, next) {
  if (req.user && req.user.role === 'admin') {
    return next();
  }
  res.status(403).json({ message: '관리자 권한이 필요합니다.' });
}

module.exports = requireAdmin;
