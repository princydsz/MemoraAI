import type { MediaValidationResult, MemoryMedia } from '../types/media';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const TOKEN_KEY = 'memora_auth_token';
const USER_KEY = 'memora_auth_user';

export class UnauthorizedError extends Error {
  readonly status = 401;

  constructor(message = 'Unauthorized: Session has expired. Please log in again.') {
    super(message);
    this.name = 'UnauthorizedError';
    Object.setPrototypeOf(this, UnauthorizedError.prototype);
  }
}

export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

function notifyUnauthorized(requestToken?: string | null): void {
  // If a requestToken was specified, only trigger logout if it matches the current active token
  if (requestToken) {
    const currentToken = getAuthToken();
    if (!currentToken || currentToken !== requestToken) {
      // Stale request from a previous token; ignore for logout purposes.
      return;
    }
  }

  if (typeof window !== 'undefined') {
    const customHandler = (window as unknown as {
      __memora_handle_unauthorized?: (options?: { expectedToken?: string }) => void;
    }).__memora_handle_unauthorized;

    if (typeof customHandler === 'function') {
      try {
        customHandler(requestToken ? { expectedToken: requestToken } : undefined);
        return;
      } catch {
        // Fall back to direct clearance
      }
    }

    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      if (window.location && typeof window.location.assign === 'function') {
        const pathname = window.location.pathname || '';
        const isAuthPage =
          pathname === '/login' ||
          pathname === '/register' ||
          pathname.startsWith('/login') ||
          pathname.startsWith('/register');
        if (!isAuthPage) {
          window.location.assign('/login');
        }
      }
    } catch {
      // Safe fallback
    }
  }
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
  const requestToken = getAuthToken();
  if (!requestToken) {
    throw new UnauthorizedError('Unauthorized: No authentication token found.');
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/memories/${memoryId}/media`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${requestToken}`,
      },
    });
  } catch {
    throw new Error('Unable to connect to media storage. Please check your network connection.');
  }

  if (response.status === 401) {
    notifyUnauthorized(requestToken);
    throw new UnauthorizedError('Unauthorized: Session has expired. Please log in again.');
  }

  if (response.status === 404) {
    throw new Error('Memory not found or you do not have permission to view its media.');
  }

  let body: any = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok) {
    throw new Error(body?.message || body?.error || `Failed to fetch memory media attachments (HTTP ${response.status}).`);
  }

  return body as MemoryMedia[];
}

/**
 * Uploads a batch of images to the backend media vault.
 */
export async function uploadMemoryMediaApi(memoryId: string, files: File[]): Promise<MemoryMedia[]> {
  const requestToken = getAuthToken();
  if (!requestToken) {
    throw new UnauthorizedError('Unauthorized: No authentication token found.');
  }

  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/memories/${memoryId}/media`, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${requestToken}`,
      },
      body: formData,
    });
  } catch {
    throw new Error('Unable to connect to media storage. Please check your network connection.');
  }

  if (response.status === 401) {
    notifyUnauthorized(requestToken);
    throw new UnauthorizedError('Unauthorized: Session has expired. Please log in again.');
  }

  let body: any = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok) {
    throw new Error(body?.message || body?.error || `Failed to upload image(s) (HTTP ${response.status}).`);
  }

  return body as MemoryMedia[];
}

/**
 * Fetches the binary image stream from the authenticated download endpoint.
 */
export async function fetchMediaBlobApi(memoryId: string, mediaId: string): Promise<Blob> {
  const requestToken = getAuthToken();
  if (!requestToken) {
    throw new UnauthorizedError('Unauthorized: No authentication token found.');
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/memories/${memoryId}/media/${mediaId}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${requestToken}`,
      },
    });
  } catch {
    throw new Error('Unable to download media file. Please check your network connection.');
  }

  if (response.status === 401) {
    notifyUnauthorized(requestToken);
    throw new UnauthorizedError('Unauthorized: Session has expired.');
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
  const requestToken = getAuthToken();
  if (!requestToken) {
    throw new UnauthorizedError('Unauthorized: No authentication token found.');
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/memories/${memoryId}/media/${mediaId}`, {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${requestToken}`,
      },
    });
  } catch {
    throw new Error('Unable to connect to media storage. Please check your network connection.');
  }

  if (response.status === 401) {
    notifyUnauthorized(requestToken);
    throw new UnauthorizedError('Unauthorized: Session has expired.');
  }

  if (!response.ok) {
    let errorMsg = `Failed to delete image attachment (HTTP ${response.status}).`;
    try {
      const body = await response.json();
      errorMsg = body?.message || body?.error || errorMsg;
    } catch {
      // ignore json parse failure on non-JSON response
    }
    throw new Error(errorMsg);
  }
}
