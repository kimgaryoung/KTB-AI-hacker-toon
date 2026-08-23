import { api } from './client';

export function startAnalysis(relationshipId, { conversationFileId, checkInId }) {
  return api.post(`/relationships/${relationshipId}/analyses`, { conversationFileId, checkInId });
}

export function getAnalysisJob(jobId) {
  return api.get(`/analysis-jobs/${jobId}`);
}

const TERMINAL_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELED']);

/**
 * Polls an analysis job following the API spec's recommended cadence
 * (1s for the first 10s, 2s after) until it reaches a terminal status.
 * Calls onUpdate with every poll result. Returns the final job.
 */
export function pollAnalysisJob(jobId, { onUpdate, signal } = {}) {
  return new Promise((resolve, reject) => {
    const startedAt = Date.now();

    async function tick() {
      if (signal?.aborted) {
        reject(new DOMException('Aborted', 'AbortError'));
        return;
      }
      try {
        const job = await getAnalysisJob(jobId);
        onUpdate?.(job);
        if (TERMINAL_STATUSES.has(job.status)) {
          resolve(job);
          return;
        }
        const interval = Date.now() - startedAt < 10_000 ? 1000 : 2000;
        setTimeout(tick, interval);
      } catch (err) {
        reject(err);
      }
    }

    tick();
  });
}
