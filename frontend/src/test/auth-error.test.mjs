import test, { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import {
  UnauthorizedError,
  isUnauthorizedError,
  onUnauthorized,
  handleUnauthorized,
  authenticatedFetch,
  getMeApi,
  getAuthToken,
  setAuthToken,
  getStoredUser,
  setStoredUser,
  clearAuth,
  _resetUnauthorizedForTesting,
} from '../lib/auth.ts';

describe('Centralized Authentication Error Handling & Race Condition Prevention', () => {
  let localStorageStore;
  let assignedUrls;
  let currentPath;

  beforeEach(() => {
    _resetUnauthorizedForTesting();
    localStorageStore = new Map();
    assignedUrls = [];
    currentPath = '/dashboard';

    globalThis.localStorage = {
      getItem: (key) => (localStorageStore.has(key) ? localStorageStore.get(key) : null),
      setItem: (key, val) => localStorageStore.set(key, String(val)),
      removeItem: (key) => localStorageStore.delete(key),
      clear: () => localStorageStore.clear(),
    };

    globalThis.window = {
      localStorage: globalThis.localStorage,
      location: {
        get pathname() {
          return currentPath;
        },
        assign: (url) => {
          assignedUrls.push(url);
        },
      },
    };
  });

  describe('UnauthorizedError & isUnauthorizedError', () => {
    it('creates an UnauthorizedError with status 401 and default message', () => {
      const err = new UnauthorizedError();
      assert.equal(err.status, 401);
      assert.equal(err.name, 'UnauthorizedError');
      assert.match(err.message, /Session has expired/i);
    });

    it('creates an UnauthorizedError with custom message', () => {
      const err = new UnauthorizedError('Custom 401 message');
      assert.equal(err.status, 401);
      assert.equal(err.message, 'Custom 401 message');
    });

    it('correctly identifies 401 errors using isUnauthorizedError', () => {
      assert.equal(isUnauthorizedError(new UnauthorizedError()), true);
      assert.equal(isUnauthorizedError({ status: 401, message: 'Unauthorized' }), true);
      assert.equal(isUnauthorizedError({ status: 401 }), true);
      assert.equal(isUnauthorizedError(new Error('Session has expired')), true);
    });

    it('correctly rejects non-401 errors using isUnauthorizedError', () => {
      assert.equal(isUnauthorizedError(new Error('Internal Server Error')), false);
      assert.equal(isUnauthorizedError({ status: 500, message: 'Server error' }), false);
      assert.equal(isUnauthorizedError(new TypeError('Failed to fetch')), false);
      assert.equal(isUnauthorizedError(null), false);
      assert.equal(isUnauthorizedError(undefined), false);
    });
  });

  describe('handleUnauthorized concurrency & safe redirection', () => {
    it('safely clears localStorage credentials upon 401', () => {
      setAuthToken('sample-jwt-token-abc');
      setStoredUser({ id: 'u1', name: 'John Doe', email: 'john@example.com', role: 'USER' });

      assert.equal(getAuthToken(), 'sample-jwt-token-abc');
      assert.notEqual(getStoredUser(), null);

      handleUnauthorized({ skipRedirect: true });

      assert.equal(getAuthToken(), null);
      assert.equal(getStoredUser(), null);
      assert.equal(localStorageStore.has('memora_auth_token'), false);
      assert.equal(localStorageStore.has('memora_auth_user'), false);
    });

    it('notifies registered onUnauthorized subscribers and allows unsubscribing', () => {
      let notifiedCount = 0;
      const unsubscribe = onUnauthorized(() => {
        notifiedCount++;
      });

      handleUnauthorized({ skipRedirect: true });
      assert.equal(notifiedCount, 1);

      unsubscribe();
    });

    it('deduplicates concurrent 401 calls to prevent repeated logout cascades', () => {
      let notificationCount = 0;
      const unsubscribe = onUnauthorized(() => {
        notificationCount++;
      });

      handleUnauthorized({ skipRedirect: true });
      handleUnauthorized({ skipRedirect: true });
      handleUnauthorized({ skipRedirect: true });
      handleUnauthorized({ skipRedirect: true });
      handleUnauthorized({ skipRedirect: true });

      assert.equal(notificationCount, 1);

      unsubscribe();
    });

    it('redirects protected page (/dashboard) to /login on 401', () => {
      currentPath = '/dashboard';

      handleUnauthorized();

      assert.equal(assignedUrls.length, 1);
      assert.equal(assignedUrls[0], '/login');
    });

    it('avoids redirect loop when already on /login or /register', () => {
      currentPath = '/login';
      handleUnauthorized();
      assert.equal(assignedUrls.length, 0);

      _resetUnauthorizedForTesting();

      currentPath = '/register';
      handleUnauthorized();
      assert.equal(assignedUrls.length, 0);
    });
  });

  describe('Race Condition & Stale Token Protection', () => {
    it('stale request with old token returning 401 does not clear fresh login token or redirect', async () => {
      const oldToken = 'old-expired-token-123';
      setAuthToken(oldToken);

      let trigger401Response;
      const inFlightPromise = new Promise((resolve) => {
        trigger401Response = resolve;
      });

      globalThis.fetch = async (url, options) => {
        const authHeader = options?.headers?.get ? options.headers.get('Authorization') : options?.headers?.['Authorization'];
        if (authHeader === `Bearer ${oldToken}`) {
          await inFlightPromise;
          return {
            status: 401,
            ok: false,
            json: async () => ({ message: 'JWT expired' }),
          };
        }
        return { status: 200, ok: true, json: async () => ({ success: true }) };
      };

      // 1. Stale request initiated with oldToken
      const staleRequestPromise = authenticatedFetch('http://localhost:8080/api/memories');

      // 2. User successfully logs in with a new fresh token
      const newToken = 'brand-new-fresh-valid-token-456';
      setAuthToken(newToken);
      setStoredUser({ id: 'u2', name: 'Jane Doe', email: 'jane@example.com', role: 'USER' });

      // 3. Stale request completes with 401
      trigger401Response();

      // The stale request throws UnauthorizedError
      await assert.rejects(staleRequestPromise, UnauthorizedError);

      // The NEW token and user MUST remain completely intact!
      assert.equal(getAuthToken(), newToken);
      assert.equal(getStoredUser()?.name, 'Jane Doe');
      assert.equal(assignedUrls.length, 0); // No redirect occurred
    });

    it('current active token returning 401 clears the session and triggers redirect', async () => {
      const activeToken = 'active-token-789';
      setAuthToken(activeToken);

      globalThis.fetch = async () => ({
        status: 401,
        ok: false,
        json: async () => ({ message: 'Session expired' }),
      });

      await assert.rejects(
        () => authenticatedFetch('http://localhost:8080/api/memories'),
        UnauthorizedError
      );

      assert.equal(getAuthToken(), null);
      assert.equal(assignedUrls.length, 1);
      assert.equal(assignedUrls[0], '/login');
    });

    it('tokenless requests throw UnauthorizedError without triggering global logout or redirect', async () => {
      clearAuth();
      assert.equal(getAuthToken(), null);

      let fetchCalled = false;
      globalThis.fetch = async () => {
        fetchCalled = true;
        return { status: 401, ok: false, json: async () => ({}) };
      };

      let notified = false;
      const unsub = onUnauthorized(() => {
        notified = true;
      });

      await assert.rejects(
        () => authenticatedFetch('http://localhost:8080/api/memories'),
        (err) => {
          assert.equal(err instanceof UnauthorizedError, true);
          assert.match(err.message, /no authentication token/i);
          return true;
        }
      );

      unsub();
      assert.equal(fetchCalled, false); // Request is never sent to backend
      assert.equal(notified, false); // Does not notify logout subscribers
      assert.equal(assignedUrls.length, 0); // Does not redirect
    });

    it('concurrent stale 401 responses do not clear a newly established session', async () => {
      const oldToken = 'stale-token-555';
      setAuthToken(oldToken);

      let triggerAll401;
      const delay = new Promise((resolve) => {
        triggerAll401 = resolve;
      });

      globalThis.fetch = async () => {
        await delay;
        return { status: 401, ok: false, json: async () => ({ message: 'Old token expired' }) };
      };

      // Fire 3 concurrent requests with oldToken
      const req1 = authenticatedFetch('http://localhost:8080/api/memories');
      const req2 = authenticatedFetch('http://localhost:8080/api/memories/search');
      const req3 = authenticatedFetch('http://localhost:8080/api/memories/graph');

      // User logs in with new token
      const newToken = 'super-new-token-999';
      setAuthToken(newToken);

      // Release all 3 responses
      triggerAll401();

      await Promise.allSettled([req1, req2, req3]);

      // Session MUST remain the new token!
      assert.equal(getAuthToken(), newToken);
      assert.equal(assignedUrls.length, 0);
    });

    it('getMeApi throws UnauthorizedError on 401 without directly calling handleUnauthorized', async () => {
      setAuthToken('token-under-test');

      globalThis.fetch = async () => ({
        status: 401,
        ok: false,
        json: async () => ({ message: 'Unauthorized' }),
      });

      let notified = false;
      const unsub = onUnauthorized(() => {
        notified = true;
      });

      await assert.rejects(
        () => getMeApi('token-under-test'),
        UnauthorizedError
      );

      unsub();
      // Token MUST remain stored (getMeApi leaves decision to AuthContext)
      assert.equal(getAuthToken(), 'token-under-test');
      assert.equal(notified, false);
      assert.equal(assignedUrls.length, 0);
    });
  });

  describe('authenticatedFetch wrapper standard behaviour', () => {
    beforeEach(() => {
      setAuthToken('valid-user-bearer-token');
    });

    it('injects Authorization Bearer header into outgoing requests', async () => {
      let capturedHeaders = null;
      globalThis.fetch = async (url, options) => {
        capturedHeaders = options.headers;
        return {
          status: 200,
          ok: true,
          json: async () => ({ success: true }),
        };
      };

      const res = await authenticatedFetch('http://localhost:8080/api/memories');
      assert.equal(res.status, 200);
      assert.equal(capturedHeaders.get('Authorization'), 'Bearer valid-user-bearer-token');
    });

    it('does NOT clear session or redirect for 500 internal server errors', async () => {
      setAuthToken('persisted-token-123');

      globalThis.fetch = async () => ({
        status: 500,
        ok: false,
        json: async () => ({ message: 'Internal Server Error' }),
      });

      const res = await authenticatedFetch('http://localhost:8080/api/memories');
      assert.equal(res.status, 500);

      // Session MUST be preserved
      assert.equal(getAuthToken(), 'persisted-token-123');
      assert.equal(assignedUrls.length, 0);
    });

    it('does NOT clear session for ordinary network errors (e.g. offline)', async () => {
      setAuthToken('persisted-token-456');

      globalThis.fetch = async () => {
        throw new TypeError('Failed to fetch (network offline)');
      };

      await assert.rejects(
        async () => {
          await authenticatedFetch('http://localhost:8080/api/memories');
        },
        /Unable to connect to MemoraAI server/
      );

      // Session MUST be preserved
      assert.equal(getAuthToken(), 'persisted-token-456');
      assert.equal(assignedUrls.length, 0);
    });
  });
});
