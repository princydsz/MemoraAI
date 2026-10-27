'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import ProtectedRoute from '../../components/ProtectedRoute';
import { useAuth } from '../../context/AuthContext';
import { getMemoriesApi } from '../../lib/memory';

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const [memoryCount, setMemoryCount] = useState<number>(0);
  const [categoryCount, setCategoryCount] = useState<number>(0);
  const [taggedCount, setTaggedCount] = useState<number>(0);

  useEffect(() => {
    getMemoriesApi()
      .then((mems) => {
        setMemoryCount(mems.length);
        const uniqueCategories = new Set(mems.map((m) => m.category).filter(Boolean));
        setCategoryCount(uniqueCategories.size);
        const tagged = mems.filter((m) => m.tags && m.tags.trim().length > 0).length;
        setTaggedCount(tagged);
      })
      .catch(() => {
        setMemoryCount(0);
        setCategoryCount(0);
        setTaggedCount(0);
      });
  }, []);

  return (
    <ProtectedRoute>
      <div className="dashboard-shell page-shell">
        <section className="welcome-panel panel">
          <div>
            <span className="eyebrow">Authenticated dashboard</span>
            <h1>Welcome back, {user?.name?.split(' ')[0] || 'friend'}.</h1>
            <p>What would you like to revisit today?</p>
          </div>

          <div className="header-actions">
            <Link href="/memories" className="primary-button">Open vault</Link>
            <Link href="/ask" className="secondary-button">Ask MemoraAI</Link>
            <Link href="/profile" className="ghost-button">View profile</Link>
            <button type="button" className="ghost-button" onClick={logout}>Sign out</button>
          </div>
        </section>

        <section className="metrics-grid">
          <article className="metric-card app-card">
            <span className="metric-label">Total memories</span>
            <span className="metric-value">{memoryCount}</span>
            <div className="metric-note">Vault status: active</div>
          </article>

          <article className="metric-card app-card">
            <span className="metric-label">Active categories</span>
            <span className="metric-value">{categoryCount}</span>
            <div className="metric-note">Organized by topic</div>
          </article>

          <article className="metric-card app-card">
            <span className="metric-label">Tagged memories</span>
            <span className="metric-value">{taggedCount}</span>
            <div className="metric-note">Searchable by keywords</div>
          </article>
        </section>

        <section className="action-grid">
          <Link href="/memories" className="action-card action-card--primary app-card">
            <div>
              <div className="eyebrow" style={{ marginBottom: '0.6rem' }}>Memory vault</div>
              <h3>Browse and update memories</h3>
              <p>Create, organise, and revisit meaningful moments from your personal collection.</p>
            </div>
            <span className="status-badge">Active</span>
          </Link>

          <Link href="/ask" className="action-card action-card--secondary app-card">
            <div>
              <div className="eyebrow" style={{ marginBottom: '0.6rem' }}>Ask AI</div>
              <h3>Search your history naturally</h3>
              <p>Ask questions about people, places, and events and get grounded answers from your vault.</p>
            </div>
            <span className="status-badge">Grounded</span>
          </Link>

          <Link href="/profile" className="action-card action-card--accent app-card">
            <div>
              <div className="eyebrow" style={{ marginBottom: '0.6rem' }}>Profile</div>
              <h3>Review your account</h3>
              <p>Check your account details, role, and personal settings in one place.</p>
            </div>
            <span className="status-badge">Ready</span>
          </Link>
        </section>

        <section className="account-panel panel">
          <h2>Account overview</h2>
          <div className="overview-grid">
            <div className="overview-item">
              <div className="overview-label">Full name</div>
              <span className="overview-value">{user?.name}</span>
            </div>
            <div className="overview-item">
              <div className="overview-label">Email address</div>
              <span className="overview-value">{user?.email}</span>
            </div>
            <div className="overview-item">
              <div className="overview-label">Account role</div>
              <span className="overview-value">{user?.role}</span>
            </div>
            <div className="overview-item">
              <div className="overview-label">User ID</div>
              <span className="overview-value">{user?.id}</span>
            </div>
          </div>
        </section>
      </div>
    </ProtectedRoute>
  );
}
