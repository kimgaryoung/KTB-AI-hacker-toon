import { api } from './client';

export function fetchDashboard({ weekOf, sort } = {}) {
  const params = new URLSearchParams();
  if (weekOf) params.set('weekOf', weekOf);
  if (sort) params.set('sort', sort);
  const qs = params.toString();
  return api.get(`/dashboard${qs ? `?${qs}` : ''}`);
}
