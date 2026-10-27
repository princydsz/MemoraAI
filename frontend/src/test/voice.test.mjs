import test, { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

import {
  MAX_AUDIO_SIZE_BYTES,
  MAX_AUDIO_DURATION_SECONDS,
  ALLOWED_AUDIO_MIME_TYPES,
  ALLOWED_AUDIO_EXTENSIONS,
  formatDuration,
  validateAudioBlob,
  isMediaRecorderSupported,
  getPreferredAudioMimeType,
  transcribeAudioApi,
} from '../lib/voice.ts';

describe('Voice Audio Constants & Constraints', () => {
  it('should enforce 25 MiB max size constant matching AI service', () => {
    assert.equal(MAX_AUDIO_SIZE_BYTES, 25 * 1024 * 1024);
  });

  it('should enforce 300 seconds (5 minutes) max duration constant', () => {
    assert.equal(MAX_AUDIO_DURATION_SECONDS, 300);
  });

  it('should include common browser recording containers (webm, ogg, mp4, wav)', () => {
    assert.ok(ALLOWED_AUDIO_MIME_TYPES.includes('audio/webm'));
    assert.ok(ALLOWED_AUDIO_MIME_TYPES.includes('audio/wav'));
    assert.ok(ALLOWED_AUDIO_MIME_TYPES.includes('audio/ogg'));
    assert.ok(ALLOWED_AUDIO_MIME_TYPES.includes('audio/mp4'));
    assert.ok(ALLOWED_AUDIO_EXTENSIONS.includes('.webm'));
    assert.ok(ALLOWED_AUDIO_EXTENSIONS.includes('.wav'));
    assert.ok(ALLOWED_AUDIO_EXTENSIONS.includes('.mp3'));
    assert.ok(ALLOWED_AUDIO_EXTENSIONS.includes('.ogg'));
    assert.ok(ALLOWED_AUDIO_EXTENSIONS.includes('.m4a'));
  });
});

describe('formatDuration helper', () => {
  it('should format 0 or negative seconds as 0:00', () => {
    assert.equal(formatDuration(0), '0:00');
    assert.equal(formatDuration(-5), '0:00');
  });

  it('should format single digit seconds with leading zero', () => {
    assert.equal(formatDuration(7), '0:07');
    assert.equal(formatDuration(9), '0:09');
  });

  it('should format double digit seconds correctly', () => {
    assert.equal(formatDuration(45), '0:45');
    assert.equal(formatDuration(59), '0:59');
  });

  it('should format minutes and seconds correctly', () => {
    assert.equal(formatDuration(60), '1:00');
    assert.equal(formatDuration(65), '1:05');
    assert.equal(formatDuration(125), '2:05');
    assert.equal(formatDuration(300), '5:00');
  });
});

describe('validateAudioBlob helper', () => {
  it('should reject empty or null blobs', () => {
    const resNull = validateAudioBlob(null);
    assert.equal(resNull.valid, false);
    assert.match(resNull.error, /empty or missing/i);

    const resEmpty = validateAudioBlob({ size: 0, type: 'audio/webm' });
    assert.equal(resEmpty.valid, false);
    assert.match(resEmpty.error, /empty or missing/i);
  });

  it('should reject recordings exceeding 25 MiB', () => {
    const oversized = { size: 25 * 1024 * 1024 + 10, type: 'audio/webm' };
    const res = validateAudioBlob(oversized);
    assert.equal(res.valid, false);
    assert.match(res.error, /exceeds maximum allowed size of 25 MB/i);
  });

  it('should reject recordings with duration <= 0', () => {
    const validSize = { size: 1000, type: 'audio/webm' };
    const res = validateAudioBlob(validSize, 0);
    assert.equal(res.valid, false);
    assert.match(res.error, /greater than 0 seconds/i);
  });

  it('should reject recordings exceeding 300 seconds', () => {
    const validSize = { size: 1000, type: 'audio/webm' };
    const res = validateAudioBlob(validSize, 301);
    assert.equal(res.valid, false);
    assert.match(res.error, /exceeds maximum allowed limit of 5 minutes/i);
  });

  it('should allow valid recording on the 300 seconds boundary', () => {
    const validSize = { size: 1000, type: 'audio/webm' };
    const res = validateAudioBlob(validSize, 300);
    assert.equal(res.valid, true);
  });

  it('should reject unsupported audio types if no valid extension', () => {
    const invalid = { size: 1000, type: 'application/pdf', name: 'doc.pdf' };
    const res = validateAudioBlob(invalid);
    assert.equal(res.valid, false);
    assert.match(res.error, /unsupported audio format/i);
  });

  it('should accept valid browser audio types with codecs parameters', () => {
    const webmWithCodecs = { size: 5000, type: 'audio/webm;codecs=opus' };
    const res = validateAudioBlob(webmWithCodecs, 45);
    assert.equal(res.valid, true);
  });
});

describe('Browser Capability Detection', () => {
  const originalNavigator = globalThis.navigator;
  const originalWindow = globalThis.window;

  it('isMediaRecorderSupported returns false when window/navigator are missing', () => {
    delete globalThis.window;
    delete globalThis.navigator;
    assert.equal(isMediaRecorderSupported(), false);
    globalThis.navigator = originalNavigator;
    globalThis.window = originalWindow;
  });

  it('isMediaRecorderSupported returns true when mediaDevices and MediaRecorder are present', () => {
    globalThis.navigator = {
      mediaDevices: {
        getUserMedia: async () => ({}),
      },
    };
    globalThis.window = {
      MediaRecorder: class MockMediaRecorder {},
    };
    assert.equal(isMediaRecorderSupported(), true);
    globalThis.navigator = originalNavigator;
    globalThis.window = originalWindow;
  });

  it('getPreferredAudioMimeType selects supported type when available', () => {
    globalThis.window = {
      MediaRecorder: class MockMediaRecorder {
        static isTypeSupported(mime) {
          return mime.startsWith('audio/webm');
        }
      },
    };
    const preferred = getPreferredAudioMimeType();
    assert.match(preferred, /audio\/webm/);
    globalThis.window = originalWindow;
  });
});

describe('transcribeAudioApi Client', () => {
  const originalFetch = globalThis.fetch;

  it('successfully uploads audio file and receives transcript with provider metadata', async () => {
    let capturedUrl = '';
    let capturedBody = null;
    let capturedMethod = '';

    const mockResponse = {
      transcript: 'This is my audio reflection from today.',
      detectedLanguage: 'en',
      durationSeconds: 12.5,
      provider: 'whisper-local',
      isSimulated: false,
    };

    globalThis.fetch = async (url, options) => {
      capturedUrl = url;
      capturedMethod = options.method;
      capturedBody = options.body;
      return {
        status: 200,
        ok: true,
        json: async () => mockResponse,
      };
    };

    const fakeBlob = new Blob(['synthetic-audio-bytes'], { type: 'audio/webm' });
    const result = await transcribeAudioApi(fakeBlob, 'test_recording.webm');

    assert.equal(result.transcript, 'This is my audio reflection from today.');
    assert.equal(result.provider, 'whisper-local');
    assert.equal(result.isSimulated, false);
    assert.equal(result.durationSeconds, 12.5);
    assert.equal(capturedMethod, 'POST');
    assert.match(capturedUrl, /\/transcribe$/);
    assert.ok(capturedBody instanceof FormData);
    assert.ok(capturedBody.get('file'));
    assert.equal(capturedBody.get('language'), null, 'Language field should not be attached when omitted');
  });

  it('attaches explicit language parameter when provided and omits when auto', async () => {
    let capturedBody = null;
    globalThis.fetch = async (url, options) => {
      capturedBody = options.body;
      return {
        status: 200,
        ok: true,
        json: async () => ({
          transcript: 'नमस्ते मेमोरा',
          detectedLanguage: 'hi',
          durationSeconds: 3.5,
          provider: 'whisper-local',
          isSimulated: false,
        }),
      };
    };

    const fakeBlob = new Blob(['bytes'], { type: 'audio/webm' });
    // Explicit language 'hi'
    const resHindi = await transcribeAudioApi(fakeBlob, 'test.webm', 'hi');
    assert.equal(resHindi.detectedLanguage, 'hi');
    assert.equal(capturedBody.get('language'), 'hi');

    // 'auto' language option
    await transcribeAudioApi(fakeBlob, 'test.webm', 'auto');
    assert.equal(capturedBody.get('language'), null);
  });

  it('accurately identifies simulated mock provider output', async () => {
    const mockSimulatedResponse = {
      transcript: '[Simulated Speech Transcription]: Audio recording processed successfully (4.0s).',
      detectedLanguage: 'en',
      durationSeconds: 4.0,
      provider: 'mock',
      isSimulated: true,
    };

    globalThis.fetch = async () => ({
      status: 200,
      ok: true,
      json: async () => mockSimulatedResponse,
    });

    const fakeBlob = new Blob(['bytes'], { type: 'audio/wav' });
    const result = await transcribeAudioApi(fakeBlob, 'mock_sample.wav');

    assert.equal(result.isSimulated, true);
    assert.equal(result.provider, 'mock');
    assert.match(result.transcript, /Simulated Speech Transcription/);
  });

  it('handles 413 Payload Too Large error', async () => {
    globalThis.fetch = async () => ({
      status: 413,
      ok: false,
      json: async () => ({ detail: 'Audio file size exceeds limit.' }),
    });

    const fakeBlob = new Blob(['bytes'], { type: 'audio/webm' });
    await assert.rejects(
      async () => await transcribeAudioApi(fakeBlob),
      /exceeds the maximum allowed upload limit of 25 MB/i
    );
  });

  it('handles 415 Unsupported Media Type error', async () => {
    globalThis.fetch = async () => ({
      status: 415,
      ok: false,
      json: async () => ({ detail: 'Unsupported audio format.' }),
    });

    const fakeBlob = new Blob(['bytes'], { type: 'audio/webm' });
    await assert.rejects(
      async () => await transcribeAudioApi(fakeBlob),
      /unsupported audio format/i
    );
  });

  it('handles 422 Unprocessable Entity error (e.g. silent audio)', async () => {
    globalThis.fetch = async () => ({
      status: 422,
      ok: false,
      json: async () => ({ detail: 'No audible speech detected in the audio file.' }),
    });

    const fakeBlob = new Blob(['bytes'], { type: 'audio/webm' });
    await assert.rejects(
      async () => await transcribeAudioApi(fakeBlob),
      /no audible speech detected in the audio file/i
    );
  });

  it('handles 503 Service Unavailable when transcription queue is busy', async () => {
    globalThis.fetch = async () => ({
      status: 503,
      ok: false,
      json: async () => ({ detail: 'Transcription service is busy.' }),
    });

    const fakeBlob = new Blob(['bytes'], { type: 'audio/webm' });
    await assert.rejects(
      async () => await transcribeAudioApi(fakeBlob),
      /service is currently busy/i
    );
  });

  it('cleanup mocks', () => {
    globalThis.fetch = originalFetch;
  });
});
