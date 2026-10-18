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
    if (!trimmedQuestion || isLoading) {
      return;
    }

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
            background: '#ffffff',
            border: '1px solid #cbd5e1',
            borderRadius: '1rem',
            boxShadow: '0 16px 40px rgba(15, 23, 42, 0.2)',
          }}
        >
          <header style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: '1rem',
            padding: '1rem 1.1rem',
            background: '#f0f9ff',
            borderBottom: '1px solid #e0f2fe',
          }}>
            <div>
              <strong style={{ display: 'block', color: '#0f172a' }}>Ask your vault</strong>
              <span style={{ color: '#475569', fontSize: '0.78rem' }}>
                Searches your memories; only relevant matches are used.
              </span>
            </div>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              aria-label="Close Ask AI"
              style={{
                border: 0,
                background: 'transparent',
                color: '#475569',
                cursor: 'pointer',
                fontSize: '1.35rem',
              }}
            >
              ×
            </button>
          </header>

          <div style={{ padding: '1rem 1.1rem', overflowY: 'auto' }} aria-live="polite">
            {error && (
              <p role="alert" style={{ color: '#b91c1c', marginTop: 0 }}>
                {error}
              </p>
            )}
            {isLoading && (
              <p style={{ color: '#475569', marginTop: 0 }}>Searching your vault...</p>
            )}
            {response && (
              <div>
                <p style={{ color: '#0f172a', lineHeight: 1.55, whiteSpace: 'pre-wrap', marginTop: 0 }}>
                  {response.answer}
                </p>
                <p style={{ color: '#64748b', fontSize: '0.8rem' }}>
                  {response.sources.length} source{response.sources.length === 1 ? '' : 's'}
                  {response.provider ? ` · ${response.provider}` : ''}
                  {response.model ? ` (${response.model})` : ''}
                </p>
                {response.sources.length > 0 && (
                  <div>
                    <strong style={{ color: '#334155', fontSize: '0.85rem' }}>Matched memories</strong>
                    <ul style={{ paddingLeft: '1.25rem', marginBottom: 0 }}>
                      {response.sources.map((source) => (
                        <li key={source.id} style={{ marginTop: '0.35rem' }}>
                          <Link href="/memories" style={{ color: '#0369a1' }}>
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
              borderTop: '1px solid #e2e8f0',
            }}
          >
            <label htmlFor="vault-assistant-question" style={{ color: '#334155', fontSize: '0.85rem', fontWeight: 600 }}>
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
                padding: '0.7rem',
                border: '1px solid #cbd5e1',
                borderRadius: '0.55rem',
                font: 'inherit',
              }}
            />
            <button
              type="submit"
              disabled={isLoading || !question.trim()}
              style={{
                justifySelf: 'end',
                border: 0,
                borderRadius: '0.55rem',
                padding: '0.65rem 1rem',
                background: isLoading || !question.trim() ? '#94a3b8' : '#0284c7',
                color: '#ffffff',
                fontWeight: 700,
                cursor: isLoading || !question.trim() ? 'not-allowed' : 'pointer',
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
          padding: '0.85rem 1.15rem',
          background: '#0369a1',
          color: '#ffffff',
          boxShadow: '0 6px 18px rgba(3, 105, 161, 0.35)',
          fontWeight: 700,
          cursor: 'pointer',
        }}
      >
        {isOpen ? 'Close AI' : 'Ask AI'}
      </button>
    </div>
  );
}
