import { authenticatedFetch, isUnauthorizedError } from './auth';

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export async function apiFetch<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  const response = await authenticatedFetch(`${API_BASE_URL}${path}`, init);
  return parseApiResponse<T>(response);
}

export async function apiFetchVoid(
  path: string,
  init?: RequestInit
): Promise<void> {
  const response = await authenticatedFetch(`${API_BASE_URL}${path}`, init);
  if (!response.ok) {
    await throwApiError(response, 'Request failed');
  }
}

export async function parseApiResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    await throwApiError(response, 'Request failed');
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

async function throwApiError(response: Response, fallback: string): Promise<never> {
  let message = fallback;
  let body: { message?: string; fieldErrors?: Record<string, string>; error?: string } | null = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
    message = Object.values(body.fieldErrors).join(', ');
  } else if (body?.message) {
    message = body.message;
  } else if (body?.error) {
    message = body.error;
  } else if (response.status === 403) {
    message = 'You do not have permission to perform this action.';
  } else if (response.status === 404) {
    message = 'The requested patient or resource was not found.';
  } else if (response.status >= 500) {
    message = 'The MemoraAI server is temporarily unavailable.';
  }
  if (isUnauthorizedError(response.status === 401 ? { status: 401 } : null)) {
    throw new Error(message);
  }
  throw new Error(`${message} (HTTP ${response.status})`);
}
