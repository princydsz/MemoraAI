'use client';

import React from 'react';
import Link from 'next/link';
import ProtectedRoute from '../../components/ProtectedRoute';
import { useAuth } from '../../context/AuthContext';

export default function ProfilePage() {
  const { user, logout } = useAuth();

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return 'Not available';
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <ProtectedRoute>
      <div style={{
        minHeight: 'calc(100vh - 70px)',
        backgroundColor: '#f8fafc',
        padding: '2.5rem 1rem',
        display: 'flex',
        justifyContent: 'center',
      }}>
        <div style={{
          width: '100%',
          maxWidth: '680px',
          display: 'flex',
          flexDirection: 'column',
          gap: '1.5rem',
        }}>
          {/* Header Card */}
          <div style={{
            backgroundColor: '#ffffff',
            padding: '2rem',
            borderRadius: '16px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
              <div style={{
                width: '64px',
                height: '64px',
                borderRadius: '16px',
                background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
                color: '#ffffff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: '1.75rem',
              }}>
                {user?.name ? user.name.charAt(0).toUpperCase() : 'U'}
              </div>
              <div>
                <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a', margin: '0 0 0.25rem 0' }}>
                  {user?.name}
                </h1>
                <p style={{ fontSize: '0.875rem', color: '#64748b', margin: 0 }}>
                  MemoraAI Account Profile
                </p>
              </div>
            </div>

            <span style={{
              fontSize: '0.85rem',
              fontWeight: 700,
              padding: '0.35rem 0.85rem',
              borderRadius: '9999px',
              backgroundColor: user?.role === 'ADMIN' ? '#e0e7ff' : '#dcfce7',
              color: user?.role === 'ADMIN' ? '#4338ca' : '#15803d',
            }}>
              {user?.role}
            </span>
          </div>

          {/* Profile Details Card */}
          <div style={{
            backgroundColor: '#ffffff',
            padding: '2rem',
            borderRadius: '16px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 2px 4px rgba(0, 0, 0, 0.02)',
          }}>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#1e293b', marginTop: 0, marginBottom: '1.5rem' }}>
              User Information
            </h2>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: '1rem',
                borderBottom: '1px solid #f1f5f9',
              }}>
                <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>Full Name</span>
                <span style={{ fontSize: '0.95rem', fontWeight: 600, color: '#0f172a' }}>{user?.name}</span>
              </div>

              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: '1rem',
                borderBottom: '1px solid #f1f5f9',
              }}>
                <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>Email Address</span>
                <span style={{ fontSize: '0.95rem', fontWeight: 600, color: '#0f172a' }}>{user?.email}</span>
              </div>

              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: '1rem',
                borderBottom: '1px solid #f1f5f9',
              }}>
                <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>User ID</span>
                <code style={{
                  fontSize: '0.85rem',
                  backgroundColor: '#f1f5f9',
                  color: '#334155',
                  padding: '0.25rem 0.6rem',
                  borderRadius: '6px',
                  fontWeight: 600,
                }}>
                  {user?.id}
                </code>
              </div>

              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingBottom: '1rem',
                borderBottom: '1px solid #f1f5f9',
              }}>
                <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>Account Role</span>
                <span style={{
                  fontSize: '0.8rem',
                  fontWeight: 700,
                  padding: '0.2rem 0.6rem',
                  borderRadius: '4px',
                  backgroundColor: user?.role === 'ADMIN' ? '#e0e7ff' : '#e0f2fe',
                  color: user?.role === 'ADMIN' ? '#3730a3' : '#0369a1',
                }}>
                  {user?.role}
                </span>
              </div>

              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}>
                <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>Member Since</span>
                <span style={{ fontSize: '0.95rem', fontWeight: 600, color: '#334155' }}>
                  {formatDate(user?.createdAt)}
                </span>
              </div>
            </div>

            <div style={{
              marginTop: '2rem',
              paddingTop: '1.5rem',
              borderTop: '1px solid #e2e8f0',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}>
              <Link
                href="/dashboard"
                style={{
                  backgroundColor: '#0284c7',
                  color: '#ffffff',
                  textDecoration: 'none',
                  padding: '0.6rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                }}
              >
                ← Back to Dashboard
              </Link>
              <button
                onClick={logout}
                style={{
                  backgroundColor: '#ffffff',
                  color: '#ef4444',
                  border: '1px solid #fca5a5',
                  padding: '0.6rem 1.25rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  cursor: 'pointer',
                }}
              >
                Sign out
              </button>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
