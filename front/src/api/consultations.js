import { api } from './client';

export function listConsultations() {
  return api.get('/consultations');
}

export function createConsultation(relationshipId) {
  return api.post('/consultations', { relationshipId });
}

export function getConsultation(id) {
  return api.get(`/consultations/${id}`);
}

export function deleteConsultation(id) {
  return api.del(`/consultations/${id}`);
}

export function listMessages(consultationId) {
  return api.get(`/consultations/${consultationId}/messages`);
}

export function sendMessage(consultationId, content) {
  return api.post(`/consultations/${consultationId}/messages`, { content });
}

/**
 * Opens the SSE stream for an in-flight assistant reply. streamUrl is the
 * server-provided absolute path from the send-message response.
 * Returns the EventSource so the caller can close it.
 */
export function openMessageStream(streamUrl, handlers) {
  const source = new EventSource(streamUrl, { withCredentials: true });
  if (handlers.onDelta) source.addEventListener('assistant.delta', (e) => handlers.onDelta(JSON.parse(e.data)));
  if (handlers.onCompleted) source.addEventListener('assistant.completed', (e) => handlers.onCompleted(JSON.parse(e.data)));
  if (handlers.onFailed) source.addEventListener('assistant.failed', (e) => handlers.onFailed(JSON.parse(e.data)));
  if (handlers.onError) source.addEventListener('error', handlers.onError);
  return source;
}
