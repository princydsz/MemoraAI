import type { CreateMemoryData, Memory, UpdateMemoryData } from '../types/memory';
import { authenticatedFetch } from './auth';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
export const ASK_AI_TIMEOUT_MS = 25000; // 25 seconds timeout for Ask AI

/**
 * Safely parses response JSON, avoiding SyntaxError on HTML (e.g. 502/503/504) or empty responses.
 */
export async function parseJsonResponse<T>(response: Response, defaultErrorMessage: string): Promise<T> {
  let body: any = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok) {
    const errorMsg =
      body?.fieldErrors && Object.values(body.fieldErrors).length > 0
        ? Object.values(body.fieldErrors).join(', ')
        : body?.message ||
          (response.status >= 500
            ? `MemoraAI server error (HTTP ${response.status}). Please try again later.`
            : `${defaultErrorMessage} (HTTP ${response.status})`);
    throw new Error(errorMsg);
  }

  return body as T;
}

export async function getMemoriesApi(): Promise<Memory[]> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/memories`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  });

  return parseJsonResponse<Memory[]>(response, 'Failed to fetch memories');
}

export async function getMemoryApi(id: string): Promise<Memory> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/memories/${id}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  });

  return parseJsonResponse<Memory>(response, 'Memory not found');
}

export async function createMemoryApi(data: CreateMemoryData): Promise<Memory> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/memories`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(data),
  });

  return parseJsonResponse<Memory>(response, 'Failed to create memory');
}

export async function updateMemoryApi(id: string, data: UpdateMemoryData): Promise<Memory> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/memories/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(data),
  });

  return parseJsonResponse<Memory>(response, 'Failed to update memory');
}

export async function deleteMemoryApi(id: string): Promise<void> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/memories/${id}`, {
    method: 'DELETE',
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    let errorMsg = `Failed to delete memory (HTTP ${response.status})`;
    try {
      const body = await response.json();
      errorMsg = body?.message || errorMsg;
    } catch {
      // ignore json parse error on empty or HTML response
    }
    throw new Error(errorMsg);
  }
}

export async function processMemoryApi(id: string): Promise<import('../types/memory').AiProcessResult> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/memories/${id}/process`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
    },
  });

  return parseJsonResponse<import('../types/memory').AiProcessResult>(response, 'Failed to process memory with AI');
}

export async function searchMemoriesApi(query: string): Promise<import('../types/memory').MemorySearchResult[]> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/memories/search?q=${encodeURIComponent(query)}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  });

  return parseJsonResponse<import('../types/memory').MemorySearchResult[]>(response, 'Failed to perform semantic search');
}

export async function reindexMemoriesApi(): Promise<import('../types/memory').ReindexResult> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/memories/reindex`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
    },
  });

  return parseJsonResponse<import('../types/memory').ReindexResult>(response, 'Failed to re-index memories');
}

export async function askMemoryApi(
  question: string,
  options?: { signal?: AbortSignal; timeoutMs?: number }
): Promise<import('../types/rag').AskMemoryResponse> {
  const timeoutMs = options?.timeoutMs ?? ASK_AI_TIMEOUT_MS;
  const controller = new AbortController();
  const timer = setTimeout(() => {
    controller.abort();
  }, timeoutMs);

  if (options?.signal) {
    options.signal.addEventListener('abort', () => controller.abort());
  }

  let response: Response;
  try {
    response = await authenticatedFetch(`${API_BASE_URL}/api/memories/ask`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ question }),
      signal: controller.signal,
    });
  } catch (err: unknown) {
    if (err instanceof Error && (err.name === 'AbortError' || controller.signal.aborted)) {
      throw new Error(
        'Ask AI request timed out after 25 seconds. The AI companion took too long to respond. Please try asking again.'
      );
    }
    throw err;
  } finally {
    clearTimeout(timer);
  }

  return parseJsonResponse<import('../types/rag').AskMemoryResponse>(
    response,
    'Failed to answer question with Ask MemoraAI'
  );
}
