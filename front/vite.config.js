import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Backend (Spring) runs on :8080 and issues session cookies + Kakao OAuth
// redirects. We proxy /api and /oauth2 through the Vite dev server (instead
// of calling :8080 directly) so the browser only ever talks to :5173 — that
// keeps the session cookie and the OAuth redirect_uri on a single origin.
// changeOrigin is intentionally left off: Spring resolves "{baseUrl}" for the
// Kakao redirect_uri from the incoming Host header, so keeping it as
// localhost:5173 makes Spring build the callback URL as
// http://localhost:5173/api/v1/auth/kakao/callback — that exact value must be
// registered as the Redirect URI in the Kakao Developers console.
//
// BACKEND_ORIGIN은 컨테이너로 띄울 때 http://backend:8080 을 가리킨다.
// 호스트에서 그냥 `npm run dev` 하면 기존처럼 localhost:8080이 기본값이다.
const backendOrigin = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080'

const backendProxy = {
  target: backendOrigin,
  changeOrigin: false,
}

export default defineConfig({
  plugins: [react()],
  server: {
    // 컨테이너 안에서는 0.0.0.0에 바인딩해야 호스트에서 접근된다.
    host: true,
    port: 5173,
    // Docker Desktop의 bind mount는 파일 변경 이벤트가 전달되지 않아
    // HMR을 쓰려면 폴링이 필요하다.
    watch: process.env.VITE_USE_POLLING === 'true' ? { usePolling: true, interval: 300 } : undefined,
    proxy: {
      '/api': backendProxy,
      '/oauth2': backendProxy,
    },
  },
})
