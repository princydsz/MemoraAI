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
      <div className="profile-shell page-shell">
        <section className="profile-card panel">
          <div className="profile-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
              <div className="profile-avatar">{user?.name ? user.name.charAt(0).toUpperCase() : 'U'}</div>
              <div>
                <h1 className="profile-name">{user?.name}</h1>
                <p className="profile-meta">MemoraAI account profile</p>
              </div>
            </div>

            <span className="status-badge">{user?.role}</span>
          </div>

          <div className="detail-list">
            <div className="detail-row">
              <span>Full name</span>
              <span>{user?.name}</span>
            </div>

            <div className="detail-row">
              <span>Email address</span>
              <span>{user?.email}</span>
            </div>

            <div className="detail-row">
              <span>User ID</span>
              <span>{user?.id}</span>
            </div>

            <div className="detail-row">
              <span>Account role</span>
              <span>{user?.role}</span>
            </div>

            <div className="detail-row">
              <span>Member since</span>
              <span>{formatDate(user?.createdAt)}</span>
            </div>
          </div>

          <div style={{ marginTop: '1.8rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
            <Link href="/dashboard" className="primary-button">← Back to dashboard</Link>
            <button type="button" className="ghost-button" onClick={logout}>Sign out</button>
          </div>
        </section>
      </div>
    </ProtectedRoute>
  );
}
