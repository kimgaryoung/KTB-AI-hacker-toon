import { api } from './client';

export function listRelationships({ search } = {}) {
  const params = new URLSearchParams();
  if (search) params.set('search', search);
  const qs = params.toString();
  return api.get(`/relationships${qs ? `?${qs}` : ''}`);
}

export function getRelationship(id) {
  return api.get(`/relationships/${id}`);
}

export function createRelationship({ name, relationshipType }) {
  return api.post('/relationships', { name, relationshipType });
}

export function updateRelationship(id, { name, relationshipType }) {
  return api.patch(`/relationships/${id}`, { name, relationshipType });
}

export function deleteRelationship(id) {
  return api.del(`/relationships/${id}`);
}
