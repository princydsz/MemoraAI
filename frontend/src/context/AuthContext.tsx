'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { AuthResponse, LoginData, RegisterData, User } from '../types/auth';
import {
  clearAuth,
  getAuthToken,
  getMeApi,
  getStoredUser,
  isUnauthorizedError,
  loginApi,
  onUnauthorized,
  registerApi,
  setAuthToken,
  setStoredUser,
} from '../lib/auth';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginData) => Promise<void>;
  register: (data: RegisterData) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Subscribe to centralized 401 Unauthorized notifications
  useEffect(() => {
    const unsubscribe = onUnauthorized(() => {
      setToken(null);
      setUser(null);
    });
    return unsubscribe;
  }, []);

  useEffect(() => {
    const initializeAuth = async () => {
      const savedToken = getAuthToken();
      const savedUser = getStoredUser();

      if (savedToken && savedUser) {
        setToken(savedToken);
        setUser(savedUser);

        // Verify session in background
        try {
          const freshUser = await getMeApi(savedToken);
          // Only update user state if the verified token is STILL the active token in localStorage
          if (getAuthToken() === savedToken) {
            setUser(freshUser);
            setStoredUser(freshUser);
          }
        } catch (err: unknown) {
          if (isUnauthorizedError(err)) {
            // ONLY clear session if the failed token is STILL the active token in localStorage.
            // Never allow a stale verification request to clear a newly established login session.
            if (getAuthToken() === savedToken) {
              clearAuth();
              setToken(null);
              setUser(null);
            }
          } else {
            // Ordinary network error (offline) or server error (500):
            // DO NOT clear auth session; allow user to continue with cached session
            console.warn(
              'Could not verify session with backend, continuing with stored session:',
              err instanceof Error ? err.message : 'Unknown error'
            );
          }
        }
      }
      setIsLoading(false);
    };

    initializeAuth();
  }, []);

  const login = async (data: LoginData) => {
    setIsLoading(true);
    try {
      const res: AuthResponse = await loginApi(data);
      setToken(res.token);
      setUser(res.user);
      setAuthToken(res.token);
      setStoredUser(res.user);
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (data: RegisterData) => {
    setIsLoading(true);
    try {
      const res: AuthResponse = await registerApi(data);
      setToken(res.token);
      setUser(res.user);
      setAuthToken(res.token);
      setStoredUser(res.user);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    clearAuth();
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
