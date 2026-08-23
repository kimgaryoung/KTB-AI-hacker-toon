import { api } from './client';

export function fetchSupportResources({ region = 'KR', category = 'MENTAL_HEALTH_COUNSELING' } = {}) {
  return api.get(`/support-resources?region=${encodeURIComponent(region)}&category=${encodeURIComponent(category)}`);
}
