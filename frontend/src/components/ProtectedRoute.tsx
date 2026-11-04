'use client';

import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '../context/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: 'USER' | 'ADMIN';
}

export default function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
  const { user, isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push('/login');
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading) {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
      }}>
        <div style={{ color: 'var(--primary-hover)', fontWeight: 600 }}>Loading session...</div>
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  if (requiredRole && user.role !== requiredRole && user.role !== 'ADMIN') {
    return (
      <div style={{
        textAlign: 'center',
        padding: '3rem',
        color: 'var(--color-error)',
      }}>
        <h2>Access Denied</h2>
        <p>You do not have permission to view this page.</p>
      </div>
    );
  }

  return <>{children}</>;
}
