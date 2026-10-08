'use client';

import React from 'react';
import Link from 'next/link';
import { useAuth } from '../context/AuthContext';

export default function Home() {
  const { user, isAuthenticated, isLoading, logout } = useAuth();

  return (
    <main style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 'calc(100vh - 70px)',
      padding: '2rem 1rem',
      textAlign: 'center',
    }}>
      <div style={{
        backgroundColor: '#ffffff',
        padding: '2.5rem 3rem',
        borderRadius: '16px',
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.01)',
        border: '1px solid #e2e8f0',
        maxWidth: '560px',
        width: '100%',
      }}>
        <div style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: '56px',
          height: '56px',
          borderRadius: '14px',
          background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
          color: '#ffffff',
          fontWeight: 700,
          fontSize: '1.75rem',
          marginBottom: '1rem',
        }}>
          M
        </div>

        <h1 style={{ fontSize: '2.25rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.5rem' }}>
          MemoraAI
        </h1>
        <p style={{ fontSize: '1.125rem', color: '#0284c7', fontWeight: 600, marginBottom: '1.75rem' }}>
          Preserving Memories, Strengthening Connections
        </p>

        {isLoading ? (
          <div style={{ padding: '1.5rem', color: '#64748b', fontSize: '0.95rem' }}>
            Checking authentication session...
          </div>
        ) : isAuthenticated && user ? (
          <div style={{
            backgroundColor: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            padding: '1.5rem',
            textAlign: 'left',
            marginBottom: '1.5rem',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
              <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Authenticated Session
              </span>
              <span style={{
                fontSize: '0.75rem',
                fontWeight: 700,
                padding: '0.2rem 0.6rem',
                borderRadius: '9999px',
                backgroundColor: user.role === 'ADMIN' ? '#e0e7ff' : '#dcfce7',
                color: user.role === 'ADMIN' ? '#4338ca' : '#15803d',
              }}>
                {user.role}
              </span>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.95rem' }}>
              <div>
                <strong style={{ color: '#334155' }}>Name:</strong>{' '}
                <span style={{ color: '#0f172a' }}>{user.name}</span>
              </div>
              <div>
                <strong style={{ color: '#334155' }}>Email:</strong>{' '}
                <span style={{ color: '#0f172a' }}>{user.email}</span>
              </div>
              <div>
                <strong style={{ color: '#334155' }}>User ID:</strong>{' '}
                <code style={{ fontSize: '0.8rem', backgroundColor: '#e2e8f0', padding: '0.1rem 0.35rem', borderRadius: '4px' }}>
                  {user.id}
                </code>
              </div>
            </div>

            <div style={{ marginTop: '1.25rem', display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
              <Link
                href="/dashboard"
                style={{
                  backgroundColor: '#0284c7',
                  color: '#ffffff',
                  textDecoration: 'none',
                  padding: '0.5rem 1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                }}
              >
                Go to Dashboard
              </Link>
              <Link
                href="/profile"
                style={{
                  backgroundColor: '#f1f5f9',
                  color: '#334155',
                  textDecoration: 'none',
                  border: '1px solid #cbd5e1',
                  padding: '0.5rem 1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                }}
              >
                View Profile
              </Link>
              <button
                onClick={logout}
                style={{
                  backgroundColor: '#ffffff',
                  color: '#ef4444',
                  border: '1px solid #fca5a5',
                  padding: '0.5rem 1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: 'pointer',
                  transition: 'background-color 0.15s ease',
                }}
              >
                Sign out
              </button>
            </div>
          </div>
        ) : (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '1rem',
            marginBottom: '1.75rem',
          }}>
            <p style={{ fontSize: '0.95rem', color: '#64748b', margin: 0 }}>
              Phase 2 Authentication is ready. Sign in with an existing account or register a new user.
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '1rem', marginTop: '0.5rem' }}>
              <Link
                href="/login"
                style={{
                  backgroundColor: '#0284c7',
                  color: '#ffffff',
                  textDecoration: 'none',
                  fontWeight: 600,
                  fontSize: '0.95rem',
                  padding: '0.65rem 1.5rem',
                  borderRadius: '8px',
                  boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                }}
              >
                Sign In
              </Link>
              <Link
                href="/register"
                style={{
                  backgroundColor: '#ffffff',
                  color: '#0f172a',
                  border: '1px solid #cbd5e1',
                  textDecoration: 'none',
                  fontWeight: 600,
                  fontSize: '0.95rem',
                  padding: '0.65rem 1.5rem',
                  borderRadius: '8px',
                }}
              >
                Create Account
              </Link>
            </div>
          </div>
        )}

        <div style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.5rem',
          backgroundColor: '#f0fdf4',
          color: '#166534',
          padding: '0.5rem 1.25rem',
          borderRadius: '9999px',
          fontWeight: 600,
          fontSize: '0.875rem',
          border: '1px solid #bbf7d0',
        }}>
          <span style={{ height: '10px', width: '10px', backgroundColor: '#22c55e', borderRadius: '50%', display: 'inline-block' }}></span>
          MemoraAI Frontend &amp; Phase 2 Auth Active
        </div>
      </div>
    </main>
  );
}
