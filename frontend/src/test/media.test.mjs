import test, { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import {
  ALLOWED_EXTENSIONS,
  ALLOWED_MIME_TYPES,
  formatFileSize,
  MAX_FILE_SIZE_BYTES,
  MAX_MEDIA_PER_MEMORY,
  validateMediaFiles,
  getMemoryMediaApi,
  uploadMemoryMediaApi,
  fetchMediaBlobApi,
  deleteMemoryMediaApi,
} from '../lib/media.ts';

describe('Media Constants & Validation', () => {
  it('should enforce 5 images max limit constant', () => {
    assert.equal(MAX_MEDIA_PER_MEMORY, 5);
  });

  it('should enforce 10 MiB max size constant', () => {
    assert.equal(MAX_FILE_SIZE_BYTES, 10 * 1024 * 1024);
  });

  it('should support exactly JPEG, PNG, and WEBP formats', () => {
    assert.deepEqual(ALLOWED_MIME_TYPES, ['image/jpeg', 'image/png', 'image/webp']);
    assert.deepEqual(ALLOWED_EXTENSIONS, ['.jpg', '.jpeg', '.png', '.webp']);
  });

  it('should reject empty or null files array', () => {
    const resEmpty = validateMediaFiles([], 0);
    assert.equal(resEmpty.valid, false);
    assert.match(resEmpty.error, /no image files/i);

    const resNull = validateMediaFiles(null, 0);
    assert.equal(resNull.valid, false);
    assert.match(resNull.error, /no image files/i);
  });

  it('should validate allowed image formats within count and size limits', () => {
    const fakeJpeg = { name: 'vacation.jpg', size: 1024 * 1024, type: 'image/jpeg' };
    const fakePng = { name: 'screenshot.PNG', size: 2 * 1024 * 1024, type: 'image/png' };
    const fakeWebp = { name: 'art.webp', size: 500 * 1024, type: 'image/webp' };

    const res = validateMediaFiles([fakeJpeg, fakePng, fakeWebp], 0);
    assert.equal(res.valid, true);
    assert.equal(res.error, undefined);
  });

  it('should reject files when currentCount + files.length exceeds 5', () => {
    const fakeFiles = [
      { name: '1.jpg', size: 100, type: 'image/jpeg' },
      { name: '2.jpg', size: 100, type: 'image/jpeg' },
      { name: '3.jpg', size: 100, type: 'image/jpeg' },
    ];

    // Current count 3 + 3 attempted = 6 > 5
    const res = validateMediaFiles(fakeFiles, 3);
    assert.equal(res.valid, false);
    assert.match(res.error, /exceeds maximum limit of 5/i);
  });

  it('should allow exactly 5 files on the boundary', () => {
    const fakeFiles = [
      { name: '1.jpg', size: 100, type: 'image/jpeg' },
      { name: '2.jpg', size: 100, type: 'image/jpeg' },
    ];

    // Current count 3 + 2 attempted = 5 -> exact boundary
    const res = validateMediaFiles(fakeFiles, 3);
    assert.equal(res.valid, true);
  });

  it('should reject any file exceeding 10 MiB', () => {
    const oversized = {
      name: 'large_photo.jpg',
      size: 10 * 1024 * 1024 + 1, // 10 MiB + 1 byte
      type: 'image/jpeg',
    };

    const res = validateMediaFiles([oversized], 0);
    assert.equal(res.valid, false);
    assert.match(res.error, /exceeds the maximum size limit of 10 MB/i);
  });

  it('should reject unsupported file types and extensions', () => {
    const pdf = { name: 'document.pdf', size: 500, type: 'application/pdf' };
    const gif = { name: 'animation.gif', size: 500, type: 'image/gif' };
    const exe = { name: 'malware.exe', size: 500, type: 'application/octet-stream' };

    for (const invalid of [pdf, gif, exe]) {
      const res = validateMediaFiles([invalid], 0);
      assert.equal(res.valid, false);
      assert.match(res.error, /unsupported format/i);
    }
  });
});

describe('formatFileSize helper', () => {
  it('should correctly format 0 bytes', () => {
    assert.equal(formatFileSize(0), '0 B');
  });

  it('should correctly format bytes', () => {
    assert.equal(formatFileSize(512), '512 B');
  });

  it('should correctly format kilobytes', () => {
    assert.equal(formatFileSize(1024), '1 KB');
    assert.equal(formatFileSize(2048), '2 KB');
  });

  it('should correctly format megabytes', () => {
    assert.equal(formatFileSize(1024 * 1024), '1 MB');
    assert.equal(formatFileSize(2.5 * 1024 * 1024), '2.5 MB');
  });
});

describe('Media API Client functions', () => {
  const originalFetch = globalThis.fetch;
  const originalLocalStorage = globalThis.localStorage;

  beforeEach(() => {
    // Mock localStorage and window
    const store = new Map();
    store.set('memora_auth_token', 'test-jwt-token-xyz');
    globalThis.localStorage = {
      getItem: (k) => store.get(k) || null,
      setItem: (k, v) => store.set(k, String(v)),
      removeItem: (k) => store.delete(k),
      clear: () => store.clear(),
    };
    globalThis.window = globalThis;
  });

  test('getMemoryMediaApi makes authenticated GET request and returns media list', async () => {
    const mockMedia = [
      {
        id: 'media-1',
        memoryId: 'mem-1',
        fileName: 'img1.jpg',
        contentType: 'image/jpeg',
        fileSize: 1024,
        createdAt: '2026-10-16T12:00:00Z',
      },
    ];

    let capturedUrl = '';
    let capturedHeaders = null;

    globalThis.fetch = async (url, options) => {
      capturedUrl = url;
      capturedHeaders = options.headers;
      return {
        status: 200,
        ok: true,
        json: async () => mockMedia,
      };
    };

    const result = await getMemoryMediaApi('mem-1');
    assert.deepEqual(result, mockMedia);
    assert.match(capturedUrl, /\/api\/memories\/mem-1\/media$/);
    assert.equal(capturedHeaders['Authorization'], 'Bearer test-jwt-token-xyz');
    assert.equal(capturedHeaders['Accept'], 'application/json');
  });

  test('getMemoryMediaApi throws on 401 Unauthorized', async () => {
    globalThis.fetch = async () => ({
      status: 401,
      ok: false,
      json: async () => ({ message: 'Unauthorized' }),
    });

    await assert.rejects(
      async () => await getMemoryMediaApi('mem-1'),
      /Session has expired/i
    );
  });

  test('getMemoryMediaApi throws on 404 Not Found', async () => {
    globalThis.fetch = async () => ({
      status: 404,
      ok: false,
      json: async () => ({ message: 'Not found' }),
    });

    await assert.rejects(
      async () => await getMemoryMediaApi('mem-1'),
      /Memory not found or you do not have permission/i
    );
  });

  test('uploadMemoryMediaApi packages files into FormData and posts to endpoint', async () => {
    let capturedMethod = '';
    let capturedBody = null;
    let capturedHeaders = null;

    const mockResponse = [
      {
        id: 'new-media-id',
        memoryId: 'mem-1',
        fileName: 'uploaded.png',
        contentType: 'image/png',
        fileSize: 2048,
        createdAt: '2026-10-16T12:01:00Z',
      },
    ];

    globalThis.fetch = async (url, options) => {
      capturedMethod = options.method;
      capturedBody = options.body;
      capturedHeaders = options.headers;
      return {
        status: 201,
        ok: true,
        json: async () => mockResponse,
      };
    };

    const fakeFile = new Blob(['sample-bytes'], { type: 'image/png' });
    fakeFile.name = 'uploaded.png';

    const result = await uploadMemoryMediaApi('mem-1', [fakeFile]);
    assert.deepEqual(result, mockResponse);
    assert.equal(capturedMethod, 'POST');
    assert.equal(capturedHeaders['Authorization'], 'Bearer test-jwt-token-xyz');
    // Crucial: Content-Type header must NOT be manually set so boundary is preserved
    assert.equal(capturedHeaders['Content-Type'], undefined);
    assert.ok(capturedBody instanceof FormData);
  });

  test('deleteMemoryMediaApi sends DELETE request and succeeds on 204', async () => {
    let capturedUrl = '';
    let capturedMethod = '';
    let capturedHeaders = null;

    globalThis.fetch = async (url, options) => {
      capturedUrl = url;
      capturedMethod = options.method;
      capturedHeaders = options.headers;
      return {
        status: 204,
        ok: true,
      };
    };

    await deleteMemoryMediaApi('mem-1', 'media-99');
    assert.match(capturedUrl, /\/api\/memories\/mem-1\/media\/media-99$/);
    assert.equal(capturedMethod, 'DELETE');
    assert.equal(capturedHeaders['Authorization'], 'Bearer test-jwt-token-xyz');
  });

  test('deleteMemoryMediaApi throws server error message on failure', async () => {
    globalThis.fetch = async () => ({
      status: 500,
      ok: false,
      json: async () => ({ message: 'MinIO storage deletion failed' }),
    });

    await assert.rejects(
      async () => await deleteMemoryMediaApi('mem-1', 'media-99'),
      /MinIO storage deletion failed/i
    );
  });

  test('fetchMediaBlobApi requests binary stream and returns blob', async () => {
    let capturedUrl = '';
    const mockBlob = new Blob(['fake-image-bytes'], { type: 'image/jpeg' });

    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        status: 200,
        ok: true,
        blob: async () => mockBlob,
      };
    };

    const blob = await fetchMediaBlobApi('mem-1', 'media-88');
    assert.equal(blob, mockBlob);
    assert.match(capturedUrl, /\/api\/memories\/mem-1\/media\/media-88$/);
  });

  test('create-memory attachment validation allows up to 5 images from 0 initial count', () => {
    const fiveFiles = [
      { name: '1.jpg', size: 1000, type: 'image/jpeg' },
      { name: '2.png', size: 2000, type: 'image/png' },
      { name: '3.webp', size: 3000, type: 'image/webp' },
      { name: '4.jpeg', size: 4000, type: 'image/jpeg' },
      { name: '5.png', size: 5000, type: 'image/png' },
    ];
    const res = validateMediaFiles(fiveFiles, 0);
    assert.equal(res.valid, true);
  });

  test('create-memory attachment validation rejects 6 images from 0 initial count', () => {
    const sixFiles = [
      { name: '1.jpg', size: 100, type: 'image/jpeg' },
      { name: '2.jpg', size: 100, type: 'image/jpeg' },
      { name: '3.jpg', size: 100, type: 'image/jpeg' },
      { name: '4.jpg', size: 100, type: 'image/jpeg' },
      { name: '5.jpg', size: 100, type: 'image/jpeg' },
      { name: '6.jpg', size: 100, type: 'image/jpeg' },
    ];
    const res = validateMediaFiles(sixFiles, 0);
    assert.equal(res.valid, false);
    assert.match(res.error, /exceeds maximum limit of 5/i);
  });

  test('uploadMemoryMediaApi accurately targets new memory ID returned from memory creation', async () => {
    const newlyCreatedMemoryId = 'new-uuid-999';
    let capturedUrl = '';

    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        status: 201,
        ok: true,
        json: async () => [{ id: 'media-new-1', memoryId: newlyCreatedMemoryId }],
      };
    };

    const sampleFile = new Blob(['bytes'], { type: 'image/jpeg' });
    sampleFile.name = 'attachment.jpg';

    const result = await uploadMemoryMediaApi(newlyCreatedMemoryId, [sampleFile]);
    assert.equal(result.length, 1);
    assert.match(capturedUrl, /\/api\/memories\/new-uuid-999\/media$/);
  });

  // Restore globals after tests
  test('cleanup mocks', () => {
    globalThis.fetch = originalFetch;
    globalThis.localStorage = originalLocalStorage;
    delete globalThis.window;
  });
});
