import type { MediaValidationResult, MemoryMedia } from '../types/media';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const TOKEN_KEY = 'memora_auth_token';

export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export const MAX_MEDIA_PER_MEMORY = 5;
export const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MiB (default/image limit)
export const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MiB
export const MAX_AUDIO_SIZE_BYTES = 25 * 1024 * 1024; // 25 MiB

export const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const ALLOWED_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp'];

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

export const ALL_MEDIA_MIME_TYPES = [...ALLOWED_MIME_TYPES, ...ALLOWED_AUDIO_MIME_TYPES];
export const ALL_MEDIA_EXTENSIONS = [...ALLOWED_EXTENSIONS, ...ALLOWED_AUDIO_EXTENSIONS];

export function isAudioMedia(media: MemoryMedia | { contentType?: string }): boolean {
  return !!media.contentType?.toLowerCase().startsWith('audio/');
}

export function isImageMedia(media: MemoryMedia | { contentType?: string }): boolean {
  return !!media.contentType?.toLowerCase().startsWith('image/');
}

function getAuthHeaders(includeJson: boolean = true): HeadersInit {
  const token = getAuthToken();
  const headers: Record<string, string> = {};
  if (includeJson) {
    headers['Accept'] = 'application/json';
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

/**
 * Validates candidate media files (images and voice audio) against system limits before uploading.
 */
export function validateMediaFiles(files: File[], currentCount: number): MediaValidationResult {
  if (!files || files.length === 0) {
    return { valid: false, error: 'No image files selected for upload.' };
  }

  if (currentCount + files.length > MAX_MEDIA_PER_MEMORY) {
    return {
      valid: false,
      error: `Upload exceeds maximum limit of ${MAX_MEDIA_PER_MEMORY} images per memory. Current: ${currentCount}, Attempted: ${files.length}.`,
    };
  }

  for (const file of files) {
    const lowerName = file.name.toLowerCase();
    const isAudio =
      file.type.toLowerCase().startsWith('audio/') ||
      ALLOWED_AUDIO_EXTENSIONS.some((ext) => lowerName.endsWith(ext));

    if (isAudio) {
      if (file.size > MAX_AUDIO_SIZE_BYTES) {
        return {
          valid: false,
          error: `File "${file.name}" exceeds the maximum audio size limit of 25 MB.`,
        };
      }
    } else {
      if (file.size > MAX_FILE_SIZE_BYTES) {
        return {
          valid: false,
          error: `File "${file.name}" exceeds the maximum size limit of 10 MB.`,
        };
      }
    }

    const hasValidExt = ALL_MEDIA_EXTENSIONS.some((ext) => lowerName.endsWith(ext));
    const hasValidMime =
      ALL_MEDIA_MIME_TYPES.includes(file.type.toLowerCase()) ||
      ALLOWED_AUDIO_MIME_TYPES.some((m) => file.type.toLowerCase().startsWith(m));

    if (!hasValidExt && !hasValidMime) {
      return {
        valid: false,
        error: `File "${file.name}" has an unsupported format. Only JPEG, PNG, and WEBP images and voice audio are supported.`,
      };
    }
  }

  return { valid: true };
}

/**
 * Formats byte counts into human-readable strings.
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

/**
 * Fetches the list of attached media metadata for a specific memory.
 */
export async function getMemoryMediaApi(memoryId: string): Promise<MemoryMedia[]> {
  const response = await fetch(`${API_BASE_URL}/api/memories/${memoryId}/media`, {
    method: 'GET',
    headers: getAuthHeaders(true),
  });

  if (response.status === 401) {
    throw new Error('Unauthorized: Session has expired. Please log in again.');
  }

  if (response.status === 404) {
    throw new Error('Memory not found or you do not have permission to view its media.');
  }

  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.message || body.error || 'Failed to fetch memory media attachments.');
  }

  return body as MemoryMedia[];
}

/**
 * Uploads a batch of images to the backend media vault.
 */
export async function uploadMemoryMediaApi(memoryId: string, files: File[]): Promise<MemoryMedia[]> {
  const token = getAuthToken();
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file);
  }

  const response = await fetch(`${API_BASE_URL}/api/memories/${memoryId}/media`, {
    method: 'POST',
    headers,
    body: formData,
  });

  if (response.status === 401) {
    throw new Error('Unauthorized: Session has expired. Please log in again.');
  }

  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.message || body.error || 'Failed to upload image(s).');
  }

  return body as MemoryMedia[];
}

/**
 * Fetches the binary image stream from the authenticated download endpoint.
 */
export async function fetchMediaBlobApi(memoryId: string, mediaId: string): Promise<Blob> {
  const token = getAuthToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/memories/${memoryId}/media/${mediaId}`, {
    method: 'GET',
    headers,
  });

  if (response.status === 401) {
    throw new Error('Unauthorized: Session has expired.');
  }

  if (!response.ok) {
    throw new Error(`Failed to download image (HTTP ${response.status}).`);
  }

  return await response.blob();
}

/**
 * Deletes an image attachment from MinIO and PostgreSQL.
 */
export async function deleteMemoryMediaApi(memoryId: string, mediaId: string): Promise<void> {
  const token = getAuthToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/memories/${memoryId}/media/${mediaId}`, {
    method: 'DELETE',
    headers,
  });

  if (response.status === 401) {
    throw new Error('Unauthorized: Session has expired.');
  }

  if (!response.ok) {
    let errorMsg = 'Failed to delete image attachment.';
    try {
      const body = await response.json();
      errorMsg = body.message || body.error || errorMsg;
    } catch {
      // ignore json parse failure on non-JSON response
    }
    throw new Error(errorMsg);
  }
}
