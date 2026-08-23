// All calls are relative (/api/v1/...) so they go through the Vite dev
// proxy in vite.config.js and stay same-origin with the session cookie.
const API_ROOT = '/api/v1';

let csrfToken = null;

export function setCsrfToken(token) {
  csrfToken = token;
}

export class ApiError extends Error {
  constructor({ code, message, status, fields, requestId }) {
    super(message || code || 'API_ERROR');
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.fields = fields || [];
    this.requestId = requestId;
  }
}

// Thrown when a request hits an endpoint that requires a session and none is
// present. Spring Security's oauth2Login() registers a redirect entry point
// for unauthenticated requests (302 -> /oauth2/authorization/kakao) instead
// of a plain 401 JSON body, so we fetch with redirect:'manual' and treat the
// resulting opaque redirect as "not logged in".
export class AuthRequiredError extends Error {
  constructor() {
    super('AUTH_REQUIRED');
    this.name = 'AuthRequiredError';
    this.code = 'AUTH_REQUIRED';
  }
}

const UNSAFE_METHODS = new Set(['POST', 'PATCH', 'PUT', 'DELETE']);

async function request(path, { method = 'GET', body, isForm = false } = {}) {
  const headers = {};
  if (!isForm && body !== undefined) headers['Content-Type'] = 'application/json';
  if (UNSAFE_METHODS.has(method) && csrfToken) headers['X-CSRF-Token'] = csrfToken;

  const res = await fetch(`${API_ROOT}${path}`, {
    method,
    headers,
    credentials: 'include',
    redirect: 'manual',
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
  });

  // opaqueredirect: fetch refused to follow the 302 to the Kakao login
  // entry point. status is 0 and the body is unreadable by design.
  if (res.type === 'opaqueredirect' || res.status === 0) {
    throw new AuthRequiredError();
  }

  if (res.status === 204 || res.status === 202) {
    if (res.status === 202) {
      const json = await safeJson(res);
      return json?.data ?? null;
    }
    return null;
  }

  const json = await safeJson(res);

  if (!res.ok) {
    if (res.status === 401) throw new AuthRequiredError();
    const err = json?.error || {};
    throw new ApiError({
      code: err.code || `HTTP_${res.status}`,
      message: err.message || '요청을 처리하지 못했어요.',
      status: res.status,
      fields: err.fields,
      requestId: err.requestId,
    });
  }

  return json?.data ?? json;
}

async function safeJson(res) {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export const api = {
  get: (path) => request(path, { method: 'GET' }),
  post: (path, body) => request(path, { method: 'POST', body: body ?? {} }),
  patch: (path, body) => request(path, { method: 'PATCH', body: body ?? {} }),
  del: (path) => request(path, { method: 'DELETE' }),
  postForm: (path, formData) => request(path, { method: 'POST', body: formData, isForm: true }),
};
