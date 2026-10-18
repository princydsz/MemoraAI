'use client';

import React, { useState } from 'react';
import ProtectedRoute from '../../components/ProtectedRoute';
import { askMemoryApi } from '../../lib/memory';
import { AskMemoryResponse } from '../../types/rag';
import Link from 'next/link';

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
      <div style={{
        maxWidth: '900px',
        margin: '2rem auto',
        padding: '0 1.5rem',
        fontFamily: 'system-ui, -apple-system, sans-serif'
      }}>
        {/* Header */}
        <div style={{ marginBottom: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#ffffff',
              fontWeight: 700,
              fontSize: '1.1rem'
            }}>
              ?
            </div>
            <h1 style={{ fontSize: '1.875rem', fontWeight: 800, color: '#0f172a', margin: 0 }}>
              Ask MemoraAI
            </h1>
          </div>
          <p style={{ color: '#64748b', fontSize: '0.975rem', margin: 0 }}>
            Search your entire personal vault using natural language. Relevant memories are retrieved securely for each answer.
          </p>
        </div>

        {/* Input Area */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '12px',
          border: '1px solid #e2e8f0',
          padding: '1.5rem',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
          marginBottom: '1.5rem'
        }}>
          <label style={{ display: 'block', fontWeight: 600, color: '#334155', marginBottom: '0.5rem', fontSize: '0.9rem' }}>
            Your Question
          </label>
          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <input
              type="text"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !isLoading) {
                  handleAsk();
                }
              }}
              placeholder="Ask anything about your stored memories..."
              style={{
                flex: 1,
                padding: '0.75rem 1rem',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '0.95rem',
                outline: 'none',
                transition: 'border-color 0.15s ease'
              }}
            />
            <button
              onClick={() => handleAsk()}
              disabled={isLoading || !question.trim()}
              style={{
                backgroundColor: isLoading || !question.trim() ? '#94a3b8' : '#0284c7',
                color: '#ffffff',
                border: 'none',
                padding: '0.75rem 1.5rem',
                borderRadius: '8px',
                fontWeight: 600,
                fontSize: '0.95rem',
                cursor: isLoading || !question.trim() ? 'not-allowed' : 'pointer',
                transition: 'background-color 0.15s ease',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem'
              }}
            >
              {isLoading ? 'Thinking...' : 'Ask AI'}
            </button>
          </div>

          {/* Example Suggestion Chips */}
          <div style={{ marginTop: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>Try asking:</span>
            {exampleQuestions.map((chip, idx) => (
              <button
                key={idx}
                onClick={() => handleAsk(chip)}
                disabled={isLoading}
                style={{
                  backgroundColor: '#f1f5f9',
                  color: '#0369a1',
                  border: '1px solid #bae6fd',
                  padding: '0.35rem 0.75rem',
                  borderRadius: '9999px',
                  fontSize: '0.825rem',
                  fontWeight: 500,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease'
                }}
              >
                {chip}
              </button>
            ))}
          </div>
        </div>

        {/* Error Alert */}
        {error && (
          <div style={{
            backgroundColor: '#fef2f2',
            border: '1px solid #fecaca',
            color: '#b91c1c',
            padding: '1rem 1.25rem',
            borderRadius: '10px',
            marginBottom: '1.5rem',
            fontSize: '0.9rem'
          }}>
            {error}
          </div>
        )}

        {/* Loading Spinner */}
        {isLoading && (
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '12px',
            border: '1px solid #e2e8f0',
            padding: '3rem 2rem',
            textAlign: 'center',
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)'
          }}>
            <div style={{
              display: 'inline-block',
              width: '40px',
              height: '40px',
              border: '4px solid #e2e8f0',
              borderTopColor: '#0284c7',
              borderRadius: '50%',
              animation: 'spin 1s linear infinite'
            }} />
            <style>{`
              @keyframes spin {
                0% { transform: rotate(0deg); }
                100% { transform: rotate(360deg); }
              }
            `}</style>
            <p style={{ marginTop: '1rem', color: '#475569', fontWeight: 600, fontSize: '0.95rem' }}>
              Searching your memory vault & generating grounded answer...
            </p>
          </div>
        )}

        {/* Response Answer Card */}
        {!isLoading && response && (
          <div>
            <div style={{
              backgroundColor: '#ffffff',
              borderRadius: '12px',
              border: response.answer.startsWith('AI generation unavailable:')
                ? '1px solid #f59e0b'
                : '1px solid #0284c7',
              padding: '1.75rem',
              boxShadow: response.answer.startsWith('AI generation unavailable:')
                ? '0 4px 12px rgba(245, 158, 11, 0.08)'
                : '0 4px 12px rgba(2, 132, 199, 0.08)',
              marginBottom: '1.5rem'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                <span style={{
                  fontSize: '0.75rem',
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                  color: response.answer.startsWith('AI generation unavailable:') ? '#b45309' : '#0284c7',
                  backgroundColor: response.answer.startsWith('AI generation unavailable:') ? '#fef3c7' : '#e0f2fe',
                  padding: '0.25rem 0.6rem',
                  borderRadius: '4px'
                }}>
                  {response.answer.startsWith('AI generation unavailable:') ? 'AI Generation Notice' : 'Grounded AI Answer'}
                </span>
                <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                  Sources used: <strong>{response.retrievedCount}</strong>
                </span>
                <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                  Generated by: <strong>{response.provider || 'AI service'}</strong>
                  {response.model ? ` (${response.model})` : ''}
                </span>
              </div>

              <div style={{
                fontSize: '1.05rem',
                lineHeight: '1.6',
                color: '#0f172a',
                whiteSpace: 'pre-wrap',
                fontWeight: 500
              }}>
                {response.answer}
              </div>
            </div>

            {/* Source Memories Section */}
            {response.sources && response.sources.length > 0 ? (
              <div style={{ marginTop: '2rem' }}>
                <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#334155', marginBottom: '1rem' }}>
                  Retrieved Source Memories ({response.sources.length})
                </h3>
                <div style={{ display: 'grid', gap: '1rem' }}>
                  {response.sources.map((src) => (
                    <div
                      key={src.id}
                      style={{
                        backgroundColor: '#ffffff',
                        borderRadius: '10px',
                        border: '1px solid #e2e8f0',
                        padding: '1.25rem',
                        transition: 'box-shadow 0.15s ease'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                        <Link
                          href={`/memories`}
                          style={{
                            fontSize: '1rem',
                            fontWeight: 700,
                            color: '#0f172a',
                            textDecoration: 'none'
                          }}
                        >
                          {src.title}
                        </Link>
                        <span style={{
                          fontSize: '0.75rem',
                          backgroundColor: '#f0fdf4',
                          color: '#166534',
                          border: '1px solid #bbf7d0',
                          padding: '0.15rem 0.5rem',
                          borderRadius: '4px',
                          fontWeight: 600
                        }}>
                          {(src.similarityScore * 100).toFixed(0)}% match
                        </span>
                      </div>

                      <div style={{ display: 'flex', gap: '0.75rem', fontSize: '0.8rem', color: '#64748b', flexWrap: 'wrap' }}>
                        {src.category && (
                          <span style={{ backgroundColor: '#f1f5f9', padding: '0.15rem 0.5rem', borderRadius: '4px' }}>
                            📁 {src.category}
                          </span>
                        )}
                        {src.tags && (
                          <span style={{ backgroundColor: '#f1f5f9', padding: '0.15rem 0.5rem', borderRadius: '4px' }}>
                            🏷️ {src.tags}
                          </span>
                        )}
                        <span style={{ marginLeft: 'auto', fontSize: '0.75rem', color: '#94a3b8' }}>
                          Recorded: {new Date(src.createdAt).toLocaleDateString()}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div style={{
                backgroundColor: '#f8fafc',
                borderRadius: '10px',
                border: '1px dashed #cbd5e1',
                padding: '1.5rem',
                textAlign: 'center',
                color: '#64748b',
                fontSize: '0.9rem'
              }}>
                No personal memories matched this question. The answer may use general knowledge, but it does not establish personal facts.
              </div>
            )}
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
