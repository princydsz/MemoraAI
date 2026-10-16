import { CreateMemoryData, Memory, UpdateMemoryData } from '../types/memory';
import { getAuthToken } from './auth';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

function getHeaders(): HeadersInit {
  const token = getAuthToken();
  return {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function getMemoriesApi(): Promise<Memory[]> {
  const response = await fetch(`${API_BASE_URL}/api/memories`, {
    method: 'GET',
    headers: getHeaders(),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message || 'Failed to fetch memories');
  }

  return body as Memory[];
}

export async function getMemoryApi(id: string): Promise<Memory> {
  const response = await fetch(`${API_BASE_URL}/api/memories/${id}`, {
    method: 'GET',
    headers: getHeaders(),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message || 'Memory not found');
  }

  return body as Memory;
}

export async function createMemoryApi(data: CreateMemoryData): Promise<Memory> {
  const response = await fetch(`${API_BASE_URL}/api/memories`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify(data),
  });

  const body = await response.json();

  if (!response.ok) {
    const errorMsg =
      body.fieldErrors && Object.values(body.fieldErrors).length > 0
        ? Object.values(body.fieldErrors).join(', ')
        : body.message || 'Failed to create memory';
    throw new Error(errorMsg);
  }

  return body as Memory;
}

export async function updateMemoryApi(id: string, data: UpdateMemoryData): Promise<Memory> {
  const response = await fetch(`${API_BASE_URL}/api/memories/${id}`, {
    method: 'PUT',
    headers: getHeaders(),
    body: JSON.stringify(data),
  });

  const body = await response.json();

  if (!response.ok) {
    const errorMsg =
      body.fieldErrors && Object.values(body.fieldErrors).length > 0
        ? Object.values(body.fieldErrors).join(', ')
        : body.message || 'Failed to update memory';
    throw new Error(errorMsg);
  }

  return body as Memory;
}

export async function deleteMemoryApi(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/memories/${id}`, {
    method: 'DELETE',
    headers: getHeaders(),
  });

  if (!response.ok) {
    let errorMsg = 'Failed to delete memory';
    try {
      const body = await response.json();
      errorMsg = body.message || errorMsg;
    } catch {
      // ignore json parse error on empty response
    }
    throw new Error(errorMsg);
  }
}

export async function processMemoryApi(id: string): Promise<import('../types/memory').AiProcessResult> {
  const response = await fetch(`${API_BASE_URL}/api/memories/${id}/process`, {
    method: 'POST',
    headers: getHeaders(),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message || 'Failed to process memory with AI');
  }

  return body as import('../types/memory').AiProcessResult;
}

export async function searchMemoriesApi(query: string): Promise<import('../types/memory').MemorySearchResult[]> {
  const response = await fetch(`${API_BASE_URL}/api/memories/search?q=${encodeURIComponent(query)}`, {
    method: 'GET',
    headers: getHeaders(),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message || 'Failed to perform semantic search');
  }

  return body as import('../types/memory').MemorySearchResult[];
}

export async function reindexMemoriesApi(): Promise<import('../types/memory').ReindexResult> {
  const response = await fetch(`${API_BASE_URL}/api/memories/reindex`, {
    method: 'POST',
    headers: getHeaders(),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message || 'Failed to re-index memories');
  }

  return body as import('../types/memory').ReindexResult;
}

export async function askMemoryApi(question: string): Promise<import('../types/rag').AskMemoryResponse> {
  const response = await fetch(`${API_BASE_URL}/api/memories/ask`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify({ question }),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body.message || 'Failed to answer question with Ask MemoraAI');
  }

  return body as import('../types/rag').AskMemoryResponse;
}

