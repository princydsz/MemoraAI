'use client';

import React, { useState } from 'react';
import ProtectedRoute from '../../components/ProtectedRoute';
import { askMemoryApi } from '../../lib/memory';
import { AskMemoryResponse } from '../../types/rag';

export default function AskMemoraAiPage() {
  const [question, setQuestion] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<AskMemoryResponse | null>(null);

  const exampleQuestions = [
    'What did I do recently?',
    'What memories do I have about travel?',
    'What did I remember about my birthday?',
  ];

  const handleAsk = async (qToAsk?: string) => {
    const targetQuestion = (qToAsk !== undefined ? qToAsk : question).trim();
    if (!targetQuestion) return;

    setQuestion(targetQuestion);
    setIsLoading(true);
    setError(null);

    try {
      const res = await askMemoryApi(targetQuestion);
      setResponse(res);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('An unexpected error occurred while processing your question.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <ProtectedRoute>
      <div className="ask-shell page-shell">
        <section className="ask-panel panel">
          <div className="page-header" style={{ marginBottom: '1rem' }}>
            <div>
              <span className="eyebrow">Ask a memory</span>
              <h1 className="section-title" style={{ marginTop: '0.8rem', fontSize: 'clamp(2.2rem, 4vw, 3rem)' }}>
                Ask MemoraAI
              </h1>
            </div>
          </div>

          <div className="prompt-box">
            <label htmlFor="ask-question" style={{ fontWeight: 700, color: 'var(--text-muted)' }}>Your question</label>
            <div className="prompt-row">
              <input
                id="ask-question"
                className="input prompt-input"
                type="text"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !isLoading) {
                    handleAsk();
                  }
                }}
                placeholder="Ask anything about your memories..."
              />
              <button type="button" className="primary-button" onClick={() => handleAsk()} disabled={isLoading || !question.trim()}>
                {isLoading ? 'Thinking...' : 'Ask AI'}
              </button>
            </div>

            <div className="chip-row">
              {exampleQuestions.map((chip, idx) => (
                <button key={idx} type="button" className="chip" onClick={() => handleAsk(chip)} disabled={isLoading}>
                  {chip}
                </button>
              ))}
            </div>
          </div>

          {error && <div className="error-box">{error}</div>}

          {isLoading && (
            <div className="response-card" style={{ textAlign: 'center' }}>
              <p style={{ fontWeight: 700, color: 'var(--text-muted)' }}>Searching your memory vault and preparing an answer…</p>
            </div>
          )}

          {!isLoading && response && (
            <div className={`response-card ${response.answer.startsWith('AI generation unavailable:') ? 'response-card--notice' : ''}`}>
              <p>{response.answer}</p>
            </div>
          )}
        </section>

        {!isLoading && response && response.sources && response.sources.length > 0 && (
          <section className="source-panel panel">
            <h3>Retrieved source memories ({response.sources.length})</h3>
            <div className="source-list">
              {response.sources.map((source) => (
                <div key={source.id} className="source-item">
                  <strong>{source.title}</strong>
                  <span style={{ color: 'var(--text-muted)', fontSize: '0.82rem' }}>
                    {source.category || 'General'} · {source.createdAt ? new Date(source.createdAt).toLocaleDateString() : 'Recent'}
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
    </ProtectedRoute>
  );
}
