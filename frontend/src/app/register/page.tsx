'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '../../context/AuthContext';

export default function RegisterPage() {
  const router = useRouter();
  const { register } = useAuth();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim() || !email.trim() || !password) {
      setError('Please fill in all required fields.');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setIsLoading(true);

    try {
      await register({ name: name.trim(), email: email.trim(), password });
      router.push('/dashboard');
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Registration failed. Please try again.');
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
            <div className="eyebrow">Start your memory journey</div>
            <h1 className="section-title" style={{ marginTop: '1rem', fontSize: '3rem' }}>
              Preserve the moments you never want to lose.
            </h1>
          </div>

          <div className="auth-visual-panel">
            <div className="auth-visual-meta">
              <span>Memory vault</span>
              <span>Private by design</span>
            </div>

            <div className="photo-stack">
              <div className="photo-tile">Photo</div>
              <div className="photo-tile">Stories</div>
              <div className="photo-tile">Family</div>
            </div>
          </div>
        </aside>

        <div className="auth-form-wrap">
          <div className="auth-form">
            <div className="auth-head">
              <div className="auth-badge">M</div>
              <h2 className="auth-title">Create account</h2>
              <p className="auth-subtitle">Join MemoraAI to organize life’s treasured moments.</p>
            </div>

            {error && <div className="error-box">{error}</div>}

            <form onSubmit={handleSubmit} className="form-grid" style={{ marginTop: '1.5rem' }}>
              <div className="field">
                <label htmlFor="register-name">Full name</label>
                <input
                  id="register-name"
                  className="input"
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Alex Johnson"
                />
              </div>

              <div className="field">
                <label htmlFor="register-email">Email address</label>
                <input
                  id="register-email"
                  className="input"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="alex@example.com"
                />
              </div>

              <div className="field">
                <label htmlFor="register-password">Password</label>
                <input
                  id="register-password"
                  className="input"
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Minimum 8 characters"
                />
              </div>

              <div className="field">
                <label htmlFor="register-confirm">Confirm password</label>
                <input
                  id="register-confirm"
                  className="input"
                  type="password"
                  required
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Repeat your password"
                />
              </div>

              <button type="submit" className="primary-button" style={{ width: '100%' }} disabled={isLoading}>
                {isLoading ? 'Creating account...' : 'Create account'}
              </button>
            </form>

            <div className="inline-actions">
              <span>Already have an account?</span>
              <Link href="/login">Sign in</Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
