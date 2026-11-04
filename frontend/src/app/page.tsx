'use client';

import React from 'react';
import Link from 'next/link';
import { useAuth } from '../context/AuthContext';

export default function Home() {
  const { user, isAuthenticated, isLoading, logout } = useAuth();

  return (
    <main className="homepage">
      <div className="page-shell">
        <section className="hero panel">
          <div className="hero-copy">
            <span className="eyebrow">Personal memory companion</span>
            <h1 className="hero-title">
              Keep the stories that <span className="highlight">matter most</span> close.
            </h1>
            <p className="hero-text">
              MemoraAI helps preserve everyday moments, conversations, and places in a calm,
              beautifully organized vault designed for families and memory care.
            </p>

            {isLoading ? (
              <div className="hero-actions">
                <span className="secondary-button" style={{ opacity: 0.8 }}>Checking session...</span>
              </div>
            ) : isAuthenticated && user ? (
              <div className="hero-actions">
                <Link href="/dashboard" className="primary-button">Open dashboard</Link>
                <Link href="/memories" className="secondary-button">View vault</Link>
                <button type="button" className="ghost-button" onClick={logout}>Sign out</button>
              </div>
            ) : (
              <div className="hero-actions">
                <Link href="/register" className="primary-button">Create account</Link>
                <Link href="/login" className="secondary-button">Sign in</Link>
              </div>
            )}

            <div className="hero-trust">
              <span className="dot" />
              Secure, personal, and grounded in your own memories.
            </div>
          </div>

          <div className="hero-visual">
            <div className="memory-showcase">
              <div className="showcase-top">
                <span className="showcase-badge">Memory vault</span>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', fontWeight: 700 }}>
                  {isAuthenticated && user ? 'Today' : 'Preview'}
                </span>
              </div>

              <div className="showcase-panel">
                <div className="memory-card">
                  <div className="memory-photo">Garden afternoon</div>
                  <h3 className="memory-title">Sunday picnic at the park</h3>
                  <p className="memory-meta">Family · 18 June · Shared moments</p>
                </div>
                <div className="memory-card">
                  <div className="memory-photo">Holiday dinner</div>
                  <h3 className="memory-title">Birthday dinner with the family</h3>
                  <p className="memory-meta">Celebration · 9 April · Warm memories</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="feature-strip">
          <article className="feature-card app-card">
            <div className="feature-icon">01</div>
            <h3>Memory preservation</h3>
            <p>Save meaningful moments, places, and stories in a digital vault that stays easy to revisit.</p>
          </article>

          <article className="feature-card app-card">
            <div className="feature-icon">02</div>
            <h3>Grounded conversations</h3>
            <p>Ask about your memories and receive answers grounded in the details you have already saved.</p>
          </article>

          <article className="feature-card app-card">
            <div className="feature-icon">03</div>
            <h3>Family connection</h3>
            <p>Share context and keep important moments within reach for the people who matter most.</p>
          </article>
        </section>
      </div>
    </main>
  );
}
