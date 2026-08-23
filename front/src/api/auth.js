import { api } from './client';

export function kakaoLoginUrl() {
  const redirectUri = encodeURIComponent(window.location.origin);
  return `/api/v1/auth/kakao/authorize?redirectUri=${redirectUri}`;
}

export function fetchCurrentUser() {
  return api.get('/users/me');
}

export function logout() {
  return api.post('/auth/logout');
}
