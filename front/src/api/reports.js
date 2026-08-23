import { api } from './client';

export function fetchReport(relationshipId, { weeks = 8 } = {}) {
  return api.get(`/relationships/${relationshipId}/report?weeks=${weeks}`);
}
