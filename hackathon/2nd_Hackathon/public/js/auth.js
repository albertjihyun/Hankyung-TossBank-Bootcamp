// JWT 토큰 관련 공통 유틸리티

function getToken() {
  return localStorage.getItem('token');
}

function getUser() {
  const token = getToken();
  if (!token) return null;
  try {
    // JWT payload는 두 번째 파트 (base64url 인코딩)
    const payload = token.split('.')[1];
    // base64url → base64 변환 후 디코드
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64));
  } catch {
    return null;
  }
}

function authHeader() {
  return {
    'Authorization': `Bearer ${getToken()}`,
    'Content-Type': 'application/json',
  };
}

function logout() {
  localStorage.removeItem('token');
  window.location.href = '/index.html';
}

function isLoggedIn() {
  return !!getToken();
}

function isAdmin() {
  const user = getUser();
  return user && user.role === 'admin';
}

function redirectIfNoAuth() {
  if (!isLoggedIn()) {
    window.location.href = '/login.html';
  }
}

function redirectIfNoAdmin() {
  if (!isAdmin()) {
    window.location.href = '/index.html';
  }
}
