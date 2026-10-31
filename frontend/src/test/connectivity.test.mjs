import test, { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import {
  parseJsonResponse,
  ASK_AI_TIMEOUT_MS,
  getMemoriesApi,
  getMemoryApi,
  createMemoryApi,
  searchMemoriesApi,
  askMemoryApi,
} from '../lib/memory.ts';

import {
  setAuthToken,
  getAuthToken,
  clearAuth,
  _resetUnauthorizedForTesting,
} from '../lib/auth.ts';

describe('Backend Connectivity & Safe Error Handling', () => {
  let localStorageStore;

  beforeEach(() => {
    _resetUnauthorizedForTesting();
    localStorageStore = new Map();

    globalThis.localStorage = {
      getItem: (key) => (localStorageStore.has(key) ? localStorageStore.get(key) : null),
      setItem: (key, val) => localStorageStore.set(key, String(val)),
      removeItem: (key) => localStorageStore.delete(key),
      clear: () => localStorageStore.clear(),
    };

    globalThis.window = {
      localStorage: globalThis.localStorage,
      location: {
        pathname: '/memories',
        assign: () => {},
      },
    };

    setAuthToken('valid-connectivity-test-token');
  });

  describe('parseJsonResponse safe error parser', () => {
    it('successfully parses valid JSON response on HTTP 200', async () => {
      const mockResponse = {
        ok: true,
        status: 200,
        json: async () => [{ id: '1', title: 'Test Memory', content: 'Details' }],
      };

      const result = await parseJsonResponse(mockResponse, 'Failed to fetch');
      assert.deepEqual(result, [{ id: '1', title: 'Test Memory', content: 'Details' }]);
    });

    it('safely handles 502/503/504 HTML error page without crashing with SyntaxError', async () => {
      const html502Response = {
        ok: false,
        status: 502,
        json: async () => {
          throw new SyntaxError("Unexpected token '<', <html><body>502 Bad Gateway</body></html> is not valid JSON");
        },
      };

      await assert.rejects(
        () => parseJsonResponse(html502Response, 'Failed to fetch memories'),
        (err) => {
          assert.equal(err instanceof Error, true);
          assert.match(err.message, /MemoraAI server error \(HTTP 502\)/i);
          return true;
        }
      );
    });

    it('safely extracts validation field errors from backend error body', async () => {
      const errorResponse = {
        ok: false,
        status: 400,
        json: async () => ({
          fieldErrors: {
            title: 'Title is required',
            content: 'Content cannot be empty',
          },
        }),
      };

      await assert.rejects(
        () => parseJsonResponse(errorResponse, 'Failed to create memory'),
        (err) => {
          assert.equal(err instanceof Error, true);
          assert.match(err.message, /Title is required/i);
          return true;
        }
      );
    });

    it('falls back to defaultErrorMessage when body has no message and status < 500', async () => {
      const emptyErrorResponse = {
        ok: false,
        status: 404,
        json: async () => {
          throw new Error('empty body');
        },
      };

      await assert.rejects(
        () => parseJsonResponse(emptyErrorResponse, 'Memory not found'),
        (err) => {
          assert.equal(err instanceof Error, true);
          assert.match(err.message, /Memory not found \(HTTP 404\)/i);
          return true;
        }
      );
    });
  });

  describe('API error handling with network offline & backend downtime', () => {
    it('converts network TypeError to user-friendly message and keeps auth session intact', async () => {
      globalThis.fetch = async () => {
        throw new TypeError('Failed to fetch');
      };

      await assert.rejects(
        () => getMemoriesApi(),
        (err) => {
          assert.equal(err instanceof Error, true);
          assert.match(err.message, /Unable to connect to MemoraAI server/i);
          return true;
        }
      );

      // Auth token must NOT be cleared by a network failure
      assert.equal(getAuthToken(), 'valid-connectivity-test-token');
    });

    it('preserves auth token when backend returns HTTP 500', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 500,
        json: async () => ({ message: 'Internal Database Connection Error' }),
      });

      await assert.rejects(
        () => getMemoriesApi(),
        (err) => {
          assert.equal(err instanceof Error, true);
          assert.match(err.message, /Internal Database Connection Error/i);
          return true;
        }
      );

      // Session MUST be preserved
      assert.equal(getAuthToken(), 'valid-connectivity-test-token');
    });

    it('searchMemoriesApi preserves auth and safely reports search errors', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 503,
        json: async () => {
          throw new SyntaxError('<html>Service Unavailable</html>');
        },
      });

      await assert.rejects(
        () => searchMemoriesApi('gardening'),
        (err) => {
          assert.match(err.message, /MemoraAI server error \(HTTP 503\)/i);
          return true;
        }
      );

      assert.equal(getAuthToken(), 'valid-connectivity-test-token');
    });
  });

  describe('Ask AI 25s timeout and abort handling', () => {
    it('enforces ASK_AI_TIMEOUT_MS constant of 25000 milliseconds', () => {
      assert.equal(ASK_AI_TIMEOUT_MS, 25000);
    });

    it('returns answer when Ask AI completes within timeout', async () => {
      globalThis.fetch = async (url, options) => {
        assert.match(url, /\/api\/memories\/ask/);
        const body = JSON.parse(options.body);
        assert.equal(body.question, 'What did I plant last spring?');

        return {
          ok: true,
          status: 200,
          json: async () => ({
            answer: 'You planted red roses and sunflowers in your front garden.',
            sources: [{ memoryId: 'm-1', title: 'Spring Gardening', score: 0.95 }],
          }),
        };
      };

      const response = await askMemoryApi('What did I plant last spring?');
      assert.match(response.answer, /You planted red roses/i);
      assert.equal(response.sources.length, 1);
    });

    it('triggers timeout when request exceeds timeoutMs and provides clear error message', async () => {
      globalThis.fetch = async (url, options) => {
        const signal = options.signal;
        return new Promise((resolve, reject) => {
          if (signal?.aborted) {
            const err = new Error('The operation was aborted');
            err.name = 'AbortError';
            return reject(err);
          }
          signal?.addEventListener('abort', () => {
            const err = new Error('The operation was aborted');
            err.name = 'AbortError';
            reject(err);
          });
        });
      };

      // Use a fast timeout for testing (e.g. 20ms)
      await assert.rejects(
        () => askMemoryApi('Who is my granddaughter?', { timeoutMs: 20 }),
        (err) => {
          assert.equal(err instanceof Error, true);
          assert.match(err.message, /Ask AI request timed out after 25 seconds/i);
          assert.match(err.message, /The AI companion took too long to respond/i);
          return true;
        }
      );

      // Session MUST NOT be cleared
      assert.equal(getAuthToken(), 'valid-connectivity-test-token');
    });

    it('allows caller to cancel Ask AI request using external AbortSignal', async () => {
      const externalController = new AbortController();

      globalThis.fetch = async (url, options) => {
        const signal = options.signal;
        return new Promise((resolve, reject) => {
          signal?.addEventListener('abort', () => {
            const err = new Error('The operation was aborted');
            err.name = 'AbortError';
            reject(err);
          });
        });
      };

      const askPromise = askMemoryApi('Tell me a story', {
        signal: externalController.signal,
        timeoutMs: 10000,
      });

      // Abort externally
      externalController.abort();

      await assert.rejects(askPromise, (err) => {
        assert.match(err.message, /Ask AI request timed out/i);
        return true;
      });
    });
  });
});

