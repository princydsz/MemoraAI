import type { AudioValidationResult, TranscribeResponse } from '../types/voice';

export const MAX_AUDIO_SIZE_BYTES = 25 * 1024 * 1024; // 25 MiB
export const MAX_AUDIO_DURATION_SECONDS = 300; // 5 minutes (300 seconds)

export const ALLOWED_AUDIO_MIME_TYPES = [
  'audio/webm',
  'audio/wav',
  'audio/wave',
  'audio/x-wav',
  'audio/mpeg',
  'audio/mp3',
  'audio/ogg',
  'audio/mp4',
  'audio/x-m4a',
  'audio/aac',
];

export const ALLOWED_AUDIO_EXTENSIONS = ['.wav', '.mp3', '.ogg', '.m4a', '.webm'];

/**
 * Checks whether the current browser supports voice recording via MediaRecorder and getUserMedia.
 */
export function isMediaRecorderSupported(): boolean {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return false;
  }
  return !!(
    navigator.mediaDevices &&
    typeof navigator.mediaDevices.getUserMedia === 'function' &&
    typeof window.MediaRecorder === 'function'
  );
}

/**
 * Detects the optimal supported audio MIME type for MediaRecorder in the current browser.
 */
export function getPreferredAudioMimeType(): string {
  if (typeof window === 'undefined' || typeof window.MediaRecorder === 'undefined') {
    return 'audio/webm';
  }
  const candidateTypes = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/ogg;codecs=opus',
    'audio/mp4',
    'audio/wav',
  ];
  if (typeof window.MediaRecorder.isTypeSupported === 'function') {
    for (const type of candidateTypes) {
      if (window.MediaRecorder.isTypeSupported(type)) {
        return type;
      }
    }
  }
  return 'audio/webm';
}

/**
 * Validates audio blob / file size, duration, and format against AI service constraints.
 */
export function validateAudioBlob(
  blob: Blob | { size: number; type?: string; name?: string },
  durationSeconds?: number
): AudioValidationResult {
  if (!blob || blob.size === 0) {
    return { valid: false, error: 'Audio recording is empty or missing.' };
  }

  if (blob.size > MAX_AUDIO_SIZE_BYTES) {
    return {
      valid: false,
      error: `Audio recording exceeds maximum allowed size of 25 MB (${(blob.size / (1024 * 1024)).toFixed(1)} MB).`,
    };
  }

  if (durationSeconds !== undefined) {
    if (durationSeconds <= 0) {
      return { valid: false, error: 'Audio recording duration must be greater than 0 seconds.' };
    }
    if (durationSeconds > MAX_AUDIO_DURATION_SECONDS) {
      return {
        valid: false,
        error: `Audio duration (${durationSeconds.toFixed(1)}s) exceeds maximum allowed limit of 5 minutes (300 seconds).`,
      };
    }
  }

  if (blob.type) {
    const baseMime = blob.type.split(';')[0].toLowerCase().trim();
    if (baseMime && !ALLOWED_AUDIO_MIME_TYPES.includes(baseMime)) {
      const filename = (blob as { name?: string }).name?.toLowerCase();
      const hasValidExt = filename && ALLOWED_AUDIO_EXTENSIONS.some((ext) => filename.endsWith(ext));
      if (!hasValidExt) {
        return {
          valid: false,
          error: `Unsupported audio format "${blob.type}". Supported formats are WAV, MP3, M4A, OGG, and WEBM.`,
        };
      }
    }
  }

  return { valid: true };
}

/**
 * Formats duration in seconds into human-readable MM:SS format.
 */
export function formatDuration(seconds: number): string {
  if (!seconds || seconds <= 0 || isNaN(seconds)) return '0:00';
  const totalSec = Math.floor(seconds);
  const mins = Math.floor(totalSec / 60);
  const secs = totalSec % 60;
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

/**
 * Uploads a recorded audio file to the AI service /transcribe endpoint using multipart/form-data with field name 'file'.
 * Never exposes secrets or API keys in frontend code.
 */
export async function transcribeAudioApi(
  fileOrBlob: Blob | File,
  filename?: string,
  language?: string
): Promise<TranscribeResponse> {
  const AI_BASE_URL = process.env.NEXT_PUBLIC_AI_SERVICE_URL || 'http://localhost:8000';

  const validation = validateAudioBlob(fileOrBlob);
  if (!validation.valid) {
    throw new Error(validation.error || 'Invalid audio file.');
  }

  const defaultExt = fileOrBlob.type?.includes('mp4') ? 'm4a' : 'webm';
  const resolvedFilename =
    filename ||
    (typeof File !== 'undefined' && fileOrBlob instanceof File && fileOrBlob.name
      ? fileOrBlob.name
      : `voice_recording.${defaultExt}`);

  const formData = new FormData();
  formData.append('file', fileOrBlob, resolvedFilename);
  if (language && language.trim() && language.trim().toLowerCase() !== 'auto') {
    formData.append('language', language.trim().toLowerCase());
  }

  let response: Response;
  try {
    response = await fetch(`${AI_BASE_URL}/transcribe`, {
      method: 'POST',
      body: formData,
    });
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Network request failed';
    throw new Error(`Failed to connect to speech transcription service at ${AI_BASE_URL}: ${errorMsg}`);
  }

  if (response.status === 413) {
    throw new Error('Audio recording exceeds the maximum allowed upload limit of 25 MB.');
  }

  if (response.status === 415) {
    throw new Error('Unsupported audio format. The AI service supports WAV, MP3, M4A, OGG, and WEBM.');
  }

  if (response.status === 503) {
    throw new Error('Speech transcription service is currently busy. Please wait a moment and try again.');
  }

  let body: any = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok) {
    const detail =
      body?.detail || body?.message || `Speech transcription failed with HTTP status ${response.status}.`;
    throw new Error(detail);
  }

  if (!body || typeof body.transcript !== 'string') {
    throw new Error('Unexpected response format from speech transcription service.');
  }

  return {
    transcript: body.transcript,
    detectedLanguage: body.detectedLanguage || 'en',
    durationSeconds: typeof body.durationSeconds === 'number' ? body.durationSeconds : 0,
    provider: body.provider || 'unknown',
    isSimulated: Boolean(body.isSimulated),
  };
}
