'use client';

import React from 'react';
import Link from 'next/link';
import Image from "next/image";
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();

  return (
    <header className="topbar">
      <div className="topbar-inner">
        <Link href="/" className="brand" aria-label="MemoraAI home">
          <Image
            src="/logo.jpeg"
            alt="MemoraAI logo"
            width={120}
            height={120}
            className="brand-mark"
          />
          <span className="brand-text">MemoraAI</span>
        </Link>

        <nav className="nav-links" aria-label="Main navigation">
          {isAuthenticated && user ? (
            <>
              <Link href="/dashboard" className="nav-link">Dashboard</Link>
              <Link href="/memories" className="nav-link">Memory Vault</Link>
              <Link href="/ask" className="nav-link nav-link--active">Ask AI</Link>
              <Link href="/profile" className="nav-link">Profile</Link>

              <div className="nav-user" aria-label="Account menu">
                <span className="profile-pill">{user.name?.charAt(0)?.toUpperCase() || 'U'}</span>
                <span style={{ fontWeight: 700 }}>{user.name?.split(' ')[0] || 'User'}</span>
              </div>

              <button type="button" className="ghost-button" onClick={logout}>
                Sign out
              </button>
            </>
          ) : (
            <>
              <Link href="/login" className="nav-link">Sign in</Link>
              <Link href="/register" className="primary-button">Get Started</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
