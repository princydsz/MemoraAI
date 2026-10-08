'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { AuthResponse, LoginData, RegisterData, User } from '../types/auth';
import {
  clearAuth,
  getAuthToken,
  getMeApi,
  getStoredUser,
  loginApi,
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
          setUser(freshUser);
          setStoredUser(freshUser);
        } catch {
          // Token expired or invalid
          clearAuth();
          setToken(null);
          setUser(null);
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
