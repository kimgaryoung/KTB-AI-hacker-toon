import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { fetchCurrentUser, kakaoLoginUrl, logout as apiLogout } from '../api/auth';
import { setCsrfToken, AuthRequiredError } from '../api/client';

const AuthContext = createContext(null);

const CONSENT_KEY = 'gwangye-onboarded';

export function AuthProvider({ children }) {
  // status: 'checking' | 'authenticated' | 'unauthenticated'
  const [status, setStatus] = useState('checking');
  const [user, setUser] = useState(null);
  const [hasOnboarded, setHasOnboarded] = useState(
    () => window.localStorage.getItem(CONSENT_KEY) === '1'
  );

  const refresh = useCallback(async () => {
    try {
      const me = await fetchCurrentUser();
      setCsrfToken(me.csrfToken);
      setUser(me);
      setStatus('authenticated');
      return me;
    } catch (err) {
      setUser(null);
      setCsrfToken(null);
      setStatus('unauthenticated');
      if (!(err instanceof AuthRequiredError)) {
        // Non-auth errors (network, 5xx) still leave us logged-out from the
        // UI's perspective, but are worth knowing about during development.
        console.warn('users/me 조회 실패', err);
      }
      return null;
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const completeOnboarding = useCallback(() => {
    window.localStorage.setItem(CONSENT_KEY, '1');
    setHasOnboarded(true);
  }, []);

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      setUser(null);
      setCsrfToken(null);
      setStatus('unauthenticated');
    }
  }, []);

  const value = useMemo(
    () => ({
      status,
      isLoggedIn: status === 'authenticated',
      user,
      hasOnboarded,
      loginUrl: kakaoLoginUrl(),
      refresh,
      completeOnboarding,
      logout,
    }),
    [status, user, hasOnboarded, refresh, completeOnboarding, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
