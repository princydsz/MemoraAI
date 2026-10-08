'use client';

import React from 'react';
import Link from 'next/link';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();

  return (
    <nav style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '1rem 2rem',
      backgroundColor: '#ffffff',
      borderBottom: '1px solid #e2e8f0',
      position: 'sticky',
      top: 0,
      zIndex: 50,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <Link href="/" style={{
          textDecoration: 'none',
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem',
        }}>
          <div style={{
            width: '32px',
            height: '32px',
            borderRadius: '8px',
            background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#ffffff',
            fontWeight: 700,
            fontSize: '1rem',
          }}>
            M
          </div>
          <span style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a' }}>
            MemoraAI
          </span>
        </Link>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        {isAuthenticated && user ? (
          <>
            <Link
              href="/dashboard"
              style={{
                color: '#334155',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.9rem',
                padding: '0.45rem 0.75rem',
                borderRadius: '8px',
              }}
            >
              Dashboard
            </Link>
            <Link
              href="/memories"
              style={{
                color: '#334155',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.9rem',
                padding: '0.45rem 0.75rem',
                borderRadius: '8px',
              }}
            >
              Memory Vault
            </Link>
            <Link
              href="/profile"
              style={{
                color: '#334155',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.9rem',
                padding: '0.45rem 0.75rem',
                borderRadius: '8px',
              }}
            >
              Profile
            </Link>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              backgroundColor: '#f1f5f9',
              padding: '0.35rem 0.75rem',
              borderRadius: '9999px',
              fontSize: '0.875rem',
            }}>
              <span style={{ fontWeight: 600, color: '#334155' }}>{user.name}</span>
              <span style={{
                fontSize: '0.75rem',
                padding: '0.1rem 0.45rem',
                backgroundColor: user.role === 'ADMIN' ? '#e0e7ff' : '#e0f2fe',
                color: user.role === 'ADMIN' ? '#3730a3' : '#0369a1',
                borderRadius: '4px',
                fontWeight: 600,
              }}>
                {user.role}
              </span>
            </div>
            <button
              onClick={logout}
              style={{
                backgroundColor: '#ffffff',
                color: '#ef4444',
                border: '1px solid #fecaca',
                padding: '0.45rem 1rem',
                borderRadius: '8px',
                fontWeight: 600,
                fontSize: '0.875rem',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              Sign out
            </button>
          </>
        ) : (
          <>
            <Link
              href="/login"
              style={{
                color: '#334155',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.9rem',
                padding: '0.45rem 0.9rem',
                borderRadius: '8px',
              }}
            >
              Sign in
            </Link>
            <Link
              href="/register"
              style={{
                backgroundColor: '#0284c7',
                color: '#ffffff',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.9rem',
                padding: '0.45rem 1.1rem',
                borderRadius: '8px',
                boxShadow: '0 1px 3px rgba(2, 132, 199, 0.3)',
              }}
            >
              Get Started
            </Link>
          </>
        )}
      </div>
    </nav>
  );
}
