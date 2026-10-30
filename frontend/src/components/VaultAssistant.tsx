'use client';

import React, { FormEvent, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '../context/AuthContext';
import { askMemoryApi } from '../lib/memory';
import { AskMemoryResponse } from '../types/rag';

export default function VaultAssistant() {
  const { isAuthenticated } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [question, setQuestion] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<AskMemoryResponse | null>(null);

  if (!isAuthenticated) {
    return null;
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion || isLoading) return;

    setIsLoading(true);
    setError(null);
    setResponse(null);

    try {
      setResponse(await askMemoryApi(trimmedQuestion));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not search your memory vault.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{ position: 'fixed', right: '1.25rem', bottom: '1.25rem', zIndex: 100 }}>
      {isOpen && (
        <section
          aria-label="Ask your memory vault"
          style={{
            display: 'flex',
            flexDirection: 'column',
            width: 'min(24rem, calc(100vw - 2.5rem))',
            maxHeight: 'min(75vh, 42rem)',
            marginBottom: '0.75rem',
            overflow: 'hidden',
            background: 'rgba(255, 255, 255, 0.96)',
            border: '1px solid rgba(232, 220, 207, 1)',
            borderRadius: '1.2rem',
            boxShadow: '0 20px 40px rgba(62, 39, 35, 0.15)',
          }}
        >
          <header
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '1rem',
              padding: '1rem 1.1rem',
              background: 'linear-gradient(135deg, rgba(182, 90, 70, 0.12), rgba(74, 142, 139, 0.12))',
              borderBottom: '1px solid rgba(232, 220, 207, 1)',
            }}
          >
            <div>
              <strong style={{ display: 'block', color: 'var(--color-text)' }}>Ask your vault</strong>
              <span style={{ color: 'var(--color-secondary-text)', fontSize: '0.78rem' }}>
                Relevant memories only
              </span>
            </div>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              aria-label="Close Ask AI"
              style={{
                border: 0,
                background: 'transparent',
                color: 'var(--color-secondary-text)',
                cursor: 'pointer',
                fontSize: '1.35rem',
              }}
            >
              ×
            </button>
          </header>

          <div style={{ padding: '1rem 1.1rem', overflowY: 'auto' }} aria-live="polite">
            {error && (
              <p role="alert" style={{ color: 'var(--color-error)', marginTop: 0, fontWeight: 600 }}>
                {error}
              </p>
            )}
            {isLoading && (
              <p style={{ color: 'var(--color-secondary-text)', marginTop: 0 }}>Searching your vault…</p>
            )}
            {response && (
              <div>
                <p style={{ color: 'var(--color-text)', lineHeight: 1.6, whiteSpace: 'pre-wrap', marginTop: 0 }}>
                  {response.answer}
                </p>
                <p style={{ color: 'var(--color-secondary-text)', fontSize: '0.8rem', marginTop: '0.7rem' }}>
                  {response.sources.length} source{response.sources.length === 1 ? '' : 's'}
                  {response.provider ? ` · ${response.provider}` : ''}
                  {response.model ? ` (${response.model})` : ''}
                </p>
                {response.sources.length > 0 && (
                  <div>
                    <strong style={{ color: 'var(--color-text)', fontSize: '0.85rem' }}>Matched memories</strong>
                    <ul style={{ paddingLeft: '1.2rem', marginBottom: 0 }}>
                      {response.sources.map((source) => (
                        <li key={source.id} style={{ marginTop: '0.35rem' }}>
                          <Link href="/memories" style={{ color: 'var(--primary-hover)', fontWeight: 600 }}>
                            {source.title}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </div>

          <form
            onSubmit={handleSubmit}
            style={{
              display: 'grid',
              gap: '0.6rem',
              padding: '0.9rem 1rem 1rem',
              borderTop: '1px solid rgba(232, 220, 207, 1)',
            }}
          >
            <label htmlFor="vault-assistant-question" style={{ color: 'var(--color-text)', fontSize: '0.85rem', fontWeight: 700 }}>
              Question
            </label>
            <textarea
              id="vault-assistant-question"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ask about a memory..."
              rows={2}
              maxLength={2000}
              disabled={isLoading}
              style={{
                boxSizing: 'border-box',
                width: '100%',
                resize: 'vertical',
                padding: '0.8rem 0.9rem',
                border: '1px solid rgba(183, 155, 141, 0.9)',
                borderRadius: '0.8rem',
                background: 'rgba(252, 250, 249, 0.9)',
                font: 'inherit',
              }}
            />
            <button
              type="submit"
              disabled={isLoading || !question.trim()}
              style={{
                justifySelf: 'end',
                border: 0,
                borderRadius: '999px',
                padding: '0.7rem 1.1rem',
                background: isLoading || !question.trim() ? 'var(--color-disabled)' : 'var(--color-primary)',
                color: 'var(--color-surface)',
                fontWeight: 700,
                cursor: isLoading || !question.trim() ? 'not-allowed' : 'pointer',
                boxShadow: '0 10px 18px rgba(182, 90, 70, 0.24)',
              }}
            >
              {isLoading ? 'Searching…' : 'Ask AI'}
            </button>
          </form>
        </section>
      )}

      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        aria-expanded={isOpen}
        aria-label={isOpen ? 'Close Ask AI' : 'Ask AI about your vault'}
        style={{
          display: 'block',
          marginLeft: 'auto',
          border: 0,
          borderRadius: '9999px',
          padding: '0.85rem 1.2rem',
          background: 'linear-gradient(135deg, var(--primary), #c66f5c)',
          color: 'var(--color-surface)',
          boxShadow: '0 10px 18px rgba(182, 90, 70, 0.3)',
          fontWeight: 700,
          cursor: 'pointer',
        }}
      >
        {isOpen ? 'Close AI' : 'Ask AI'}
      </button>
    </div>
  );
}
