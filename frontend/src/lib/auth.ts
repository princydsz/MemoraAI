import type { AuthResponse, LoginData, RegisterData, User } from '../types/auth';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const TOKEN_KEY = 'memora_auth_token';
const USER_KEY = 'memora_auth_user';

/**
 * Custom error representing a 401 Unauthorized response from the API.
 * Never exposes the raw JWT token or authorization header in message or stack.
 */
export class UnauthorizedError extends Error {
  readonly status = 401;

  constructor(message = 'Unauthorized: Session has expired. Please log in again.') {
    super(message);
    this.name = 'UnauthorizedError';
    Object.setPrototypeOf(this, UnauthorizedError.prototype);
  }
}

/**
 * Type guard to check if an error is an UnauthorizedError (401).
 */
export function isUnauthorizedError(error: unknown): error is UnauthorizedError {
  if (error instanceof UnauthorizedError) return true;
  if (typeof error === 'object' && error !== null) {
    const err = error as { status?: number; message?: string };
    if (err.status === 401) return true;
    if (typeof err.message === 'string' && /unauthorized|session has expired/i.test(err.message)) {
      return true;
    }
  }
  return false;
}

// In-memory subscribers for centralized 401 notifications (e.g., AuthContext)
type UnauthorizedCallback = () => void;
const unauthorizedListeners = new Set<UnauthorizedCallback>();

export function onUnauthorized(callback: UnauthorizedCallback): () => void {
  unauthorizedListeners.add(callback);
  return () => {
    unauthorizedListeners.delete(callback);
  };
}

// Concurrency lock to prevent duplicate logout/redirect operations from concurrent 401s
let isHandlingUnauthorized = false;

/**
 * Centrally handles 401 Unauthorized responses:
 * 1. Deduplicates concurrent calls.
 * 2. Safely clears expired/invalid session from localStorage.
 * 3. Notifies in-memory subscribers (e.g. AuthContext).
 * 4. Redirects to /login while avoiding redirect loops on login/register pages.
 */
export function handleUnauthorized(options?: { skipRedirect?: boolean; expectedToken?: string }): void {
  // If an expected token was provided, ensure it still matches the active token in storage
  if (options?.expectedToken) {
    const currentToken = getAuthToken();
    if (currentToken && currentToken !== options.expectedToken) {
      // Stale request! Token has changed; ignore for logout purposes.
      return;
    }
  }

  if (isHandlingUnauthorized) {
    return;
  }
  isHandlingUnauthorized = true;

  // 1. Safely clear persisted session credentials
  clearAuth();

  // 2. Notify subscribers to update React state
  unauthorizedListeners.forEach((callback) => {
    try {
      callback();
    } catch {
      // Prevent individual subscriber errors from blocking others
    }
  });

  // 3. Safe redirect to /login avoiding redirect loops
  if (typeof window !== 'undefined' && !options?.skipRedirect) {
    try {
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
      // Safe fallback in environments without full window.location support
    }
  }

  // Reset concurrency lock after debounce window
  setTimeout(() => {
    isHandlingUnauthorized = false;
  }, 1500);
}

/**
 * Resets the concurrency lock for testing purposes.
 */
export function _resetUnauthorizedForTesting(): void {
  isHandlingUnauthorized = false;
}

// Register global handler on window for standalone media/voice clients in browser
if (typeof window !== 'undefined') {
  (window as unknown as { __memora_handle_unauthorized?: typeof handleUnauthorized }).__memora_handle_unauthorized =
    handleUnauthorized;
}

/**
 * Token management helpers
 */
export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setAuthToken(token: string): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeAuthToken(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
}

export function getStoredUser(): User | null {
  if (typeof window === 'undefined') return null;
  const userJson = localStorage.getItem(USER_KEY);
  if (!userJson) return null;
  try {
    return JSON.parse(userJson) as User;
  } catch {
    return null;
  }
}

export function setStoredUser(user: User): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuth(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

/**
 * Centralized authenticated fetch wrapper.
 * Automatically injects the Authorization Bearer header if a token exists.
 * Intercepts HTTP 401 centrally, clears session, and redirects to /login.
 * Preserves session for ordinary network errors and 500 server errors.
 */
export async function authenticatedFetch(
  input: RequestInfo | URL,
  init?: RequestInit
): Promise<Response> {
  const requestToken = getAuthToken();

  // If no token exists in storage, throw UnauthorizedError without triggering global logout cascade
  if (!requestToken) {
    throw new UnauthorizedError('Unauthorized: No authentication token found.');
  }

  const headers = new Headers(init?.headers || {});

  if (!headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${requestToken}`);
  }

  let response: Response;
  try {
    response = await fetch(input, {
      ...init,
      headers,
    });
  } catch (netErr: unknown) {
    if (netErr instanceof Error && netErr.name === 'AbortError') {
      throw netErr;
    }
    // Ordinary network error (offline, DNS, connection refused):
    // DO NOT clear session or redirect.
    throw new Error('Unable to connect to MemoraAI server. Please check your network connection.');
  }

  // Centrally detect 401 Unauthorized
  if (response.status === 401) {
    // Only trigger global logout if the failed request used the current active token
    const currentToken = getAuthToken();
    if (currentToken && currentToken === requestToken) {
      handleUnauthorized({ expectedToken: requestToken });
    }
    throw new UnauthorizedError('Unauthorized: Session has expired. Please log in again.');
  }

  // 500 server error or other responses: return for caller to handle; do NOT clear session.
  return response;
}

/**
 * Auth API clients
 */
export async function registerApi(data: RegisterData): Promise<AuthResponse> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/auth/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify(data),
    });
  } catch {
    throw new Error('Unable to connect to MemoraAI server. Please check your network connection.');
  }

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
            ? 'Server error during registration. Please try again later.'
            : `Registration failed (HTTP ${response.status}).`);
    throw new Error(errorMsg);
  }

  return body as AuthResponse;
}

export async function loginApi(data: LoginData): Promise<AuthResponse> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify(data),
    });
  } catch {
    throw new Error('Unable to connect to MemoraAI server. Please check your network connection.');
  }

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
            ? 'Server error during login. Please try again later.'
            : 'Login failed. Please verify your credentials.');
    throw new Error(errorMsg);
  }

  return body as AuthResponse;
}

export async function getMeApi(token: string): Promise<User> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
    });
  } catch {
    // Ordinary network error: throw without clearing auth
    throw new Error('Unable to connect to MemoraAI server. Please check your network connection.');
  }

  if (response.status === 401) {
    // Throw typed UnauthorizedError without triggering global logout cascade.
    // AuthContext decides whether the failed token is still the active session before clearing.
    throw new UnauthorizedError('Unauthorized: Session has expired. Please log in again.');
  }

  let body: any = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok) {
    throw new Error(body?.message || `Failed to fetch user profile (HTTP ${response.status}).`);
  }

  return body as User;
}

export async function updateTimezoneApi(timezone: string): Promise<User> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/auth/me/timezone`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ timezone }),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(body?.message || body?.error || `Failed to update timezone (HTTP ${response.status}).`);
  }
  return body as User;
}
