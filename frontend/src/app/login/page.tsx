'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '../../context/AuthContext';

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email.trim() || !password) {
      setError('Please provide both email and password.');
      return;
    }

    setIsLoading(true);

    try {
      await login({ email: email.trim(), password });
      router.push('/dashboard');
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Login failed. Please verify your credentials.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-layout">
      <div className="auth-shell">
        <aside className="auth-visual">
          <div>
            <div className="eyebrow">Return to your vault</div>
            <h1 className="section-title" style={{ marginTop: '1rem', fontSize: '3rem' }}>
              Gentle access to meaningful memories.
            </h1>
          </div>

          <div className="auth-visual-panel">
            <div className="auth-visual-meta">
              <span>Today’s moments</span>
              <span>Secure session</span>
            </div>

            <div className="photo-stack">
              <div className="photo-tile">Family</div>
              <div className="photo-tile">Travel</div>
              <div className="photo-tile">Home</div>
            </div>
          </div>
        </aside>

        <div className="auth-form-wrap">
          <div className="auth-form">
            <div className="auth-head">
              <div className="auth-badge">M</div>
              <h2 className="auth-title">Welcome back</h2>
              <p className="auth-subtitle">Sign in to your MemoraAI account.</p>
            </div>

            {error && <div className="error-box">{error}</div>}

            <form onSubmit={handleSubmit} className="form-grid" style={{ marginTop: '1.5rem' }}>
              <div className="field">
                <label htmlFor="login-email">Email address</label>
                <input
                  id="login-email"
                  className="input"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                />
              </div>

              <div className="field">
                <label htmlFor="login-password">Password</label>
                <input
                  id="login-password"
                  className="input"
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                />
              </div>

              <button type="submit" className="primary-button" style={{ width: '100%' }} disabled={isLoading}>
                {isLoading ? 'Signing in...' : 'Sign in'}
              </button>
            </form>

            <div className="inline-actions">
              <span>Need an account?</span>
              <Link href="/register">Create one</Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
