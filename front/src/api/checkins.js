import { api } from './client';

export function submitCheckIn(relationshipId, { feeling, comfort }) {
  return api.post(`/relationships/${relationshipId}/check-ins`, {
    answers: [
      { questionCode: 'RELATIONSHIP_FEELING', score: feeling },
      { questionCode: 'CONVERSATION_COMFORT', score: comfort },
    ],
  });
}
