import { api } from './client';

export function uploadConversationFile(relationshipId, file, selfParticipantName) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('source', 'KAKAO_TALK');
  formData.append('selfParticipantName', selfParticipantName);
  return api.postForm(`/relationships/${relationshipId}/conversation-files`, formData);
}

export function getConversationFile(fileId) {
  return api.get(`/conversation-files/${fileId}`);
}
