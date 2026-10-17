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
      <div style={{
        minHeight: 'calc(100vh - 70px)',
        backgroundColor: '#f8fafc',
        padding: '2rem 1.5rem',
      }}>
        <div style={{
          maxWidth: '1100px',
          margin: '0 auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '2rem',
        }}>
          {/* Welcome Header */}
          <div style={{
            backgroundColor: '#ffffff',
            padding: '2rem',
            borderRadius: '16px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '1rem',
          }}>
            <div>
              <div style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                backgroundColor: '#e0f2fe',
                color: '#0369a1',
                padding: '0.25rem 0.75rem',
                borderRadius: '9999px',
                fontSize: '0.8rem',
                fontWeight: 600,
                marginBottom: '0.5rem',
              }}>
                Authenticated Dashboard
              </div>
              <h1 style={{ fontSize: '1.875rem', fontWeight: 700, color: '#0f172a', margin: '0 0 0.35rem 0' }}>
                Welcome back, {user?.name}!
              </h1>
              <p style={{ fontSize: '0.95rem', color: '#64748b', margin: 0 }}>
                Your personal MemoraAI vault and memory management space
              </p>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <Link
                href="/memories"
                style={{
                  backgroundColor: '#0284c7',
                  color: '#ffffff',
                  textDecoration: 'none',
                  padding: '0.55rem 1.1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                }}
              >
                Open Memory Vault
              </Link>
              <Link
                href="/ask"
                style={{
                  backgroundColor: '#4f46e5',
                  color: '#ffffff',
                  textDecoration: 'none',
                  padding: '0.55rem 1.1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  boxShadow: '0 2px 4px rgba(79, 70, 229, 0.25)',
                }}
              >
                Ask MemoraAI
              </Link>
              <Link
                href="/profile"
                style={{
                  backgroundColor: '#ffffff',
                  color: '#334155',
                  border: '1px solid #cbd5e1',
                  textDecoration: 'none',
                  padding: '0.55rem 1.1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                }}
              >
                View Profile
              </Link>
              <button
                onClick={logout}
                style={{
                  backgroundColor: '#ffffff',
                  color: '#ef4444',
                  border: '1px solid #fecaca',
                  padding: '0.55rem 1.1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  cursor: 'pointer',
                }}
              >
                Sign out
              </button>
            </div>
          </div>

          {/* User Profile Summary Card */}
          <div style={{
            backgroundColor: '#ffffff',
            padding: '1.5rem 2rem',
            borderRadius: '16px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 2px 4px rgba(0, 0, 0, 0.02)',
          }}>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 700, color: '#1e293b', marginTop: 0, marginBottom: '1rem' }}>
              Account Overview
            </h2>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: '1.25rem',
            }}>
              <div>
                <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>FULL NAME</div>
                <div style={{ fontSize: '1rem', fontWeight: 600, color: '#0f172a', marginTop: '0.2rem' }}>
                  {user?.name}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>EMAIL ADDRESS</div>
                <div style={{ fontSize: '1rem', fontWeight: 600, color: '#0f172a', marginTop: '0.2rem' }}>
                  {user?.email}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>ACCOUNT ROLE</div>
                <div style={{ marginTop: '0.2rem' }}>
                  <span style={{
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    padding: '0.2rem 0.6rem',
                    borderRadius: '4px',
                    backgroundColor: user?.role === 'ADMIN' ? '#e0e7ff' : '#dcfce7',
                    color: user?.role === 'ADMIN' ? '#4338ca' : '#15803d',
                  }}>
                    {user?.role}
                  </span>
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>USER ID</div>
                <div style={{ marginTop: '0.2rem' }}>
                  <code style={{ fontSize: '0.8rem', backgroundColor: '#f1f5f9', padding: '0.2rem 0.4rem', borderRadius: '4px', color: '#334155' }}>
                    {user?.id}
                  </code>
                </div>
              </div>
            </div>
          </div>

          {/* Memory Metrics Overview — Backed by verified memory data */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))',
            gap: '1.25rem',
          }}>
            <div style={{
              backgroundColor: '#ffffff',
              padding: '1.5rem',
              borderRadius: '14px',
              border: '1px solid #e2e8f0',
            }}>
              <div style={{ fontSize: '0.875rem', color: '#64748b', fontWeight: 600 }}>Total Memories</div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: '#0284c7', marginTop: '0.35rem' }}>{memoryCount}</div>
              <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginTop: '0.35rem' }}>Vault Status: Active</div>
            </div>

            <div style={{
              backgroundColor: '#ffffff',
              padding: '1.5rem',
              borderRadius: '14px',
              border: '1px solid #e2e8f0',
            }}>
              <div style={{ fontSize: '0.875rem', color: '#64748b', fontWeight: 600 }}>Active Categories</div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: '#0f172a', marginTop: '0.35rem' }}>{categoryCount}</div>
              <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginTop: '0.35rem' }}>Auto-organized topics</div>
            </div>

            <div style={{
              backgroundColor: '#ffffff',
              padding: '1.5rem',
              borderRadius: '14px',
              border: '1px solid #e2e8f0',
            }}>
              <div style={{ fontSize: '0.875rem', color: '#64748b', fontWeight: 600 }}>Tagged Memories</div>
              <div style={{ fontSize: '2rem', fontWeight: 700, color: '#0f172a', marginTop: '0.35rem' }}>{taggedCount}</div>
              <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginTop: '0.35rem' }}>Enriched with keywords</div>
            </div>
          </div>

          {/* Memory Actions & Features */}
          <div>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', marginBottom: '1rem' }}>
              Memory Actions & Features
            </h2>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              gap: '1.25rem',
            }}>
              {/* Memory Vault Card */}
              <Link
                href="/memories"
                style={{
                  backgroundColor: '#ffffff',
                  padding: '1.5rem',
                  borderRadius: '14px',
                  border: '1px solid #0284c7',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '1rem',
                  textDecoration: 'none',
                  boxShadow: '0 2px 4px rgba(2, 132, 199, 0.1)',
                  transition: 'box-shadow 0.2s ease, transform 0.2s ease',
                }}
              >
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#0284c7', margin: 0 }}>
                      Memory Vault
                    </h3>
                    <span style={{
                      fontSize: '0.7rem',
                      fontWeight: 700,
                      backgroundColor: '#dcfce7',
                      color: '#15803d',
                      padding: '0.15rem 0.5rem',
                      borderRadius: '9999px',
                    }}>
                      Active
                    </span>
                  </div>
                  <p style={{ fontSize: '0.85rem', color: '#64748b', marginTop: '0.5rem', marginBottom: 0 }}>
                    Create, view, edit, attach images, and manage your stored personal memories.
                  </p>
                </div>
              </Link>

              {/* Prominent Ask MemoraAI Card */}
              <Link
                href="/ask"
                style={{
                  backgroundColor: '#ffffff',
                  padding: '1.5rem',
                  borderRadius: '14px',
                  border: '2px solid #6366f1',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '1rem',
                  textDecoration: 'none',
                  boxShadow: '0 4px 12px rgba(99, 102, 241, 0.15)',
                  position: 'relative',
                  overflow: 'hidden',
                }}
              >
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#4f46e5', margin: 0 }}>
                      Ask MemoraAI
                    </h3>
                    <span style={{
                      fontSize: '0.7rem',
                      fontWeight: 700,
                      backgroundColor: '#ede9fe',
                      color: '#6d28d9',
                      padding: '0.15rem 0.5rem',
                      borderRadius: '9999px',
                    }}>
                      AI Grounded
                    </span>
                  </div>
                  <p style={{ fontSize: '0.85rem', color: '#64748b', marginTop: '0.5rem', marginBottom: 0 }}>
                    Ask questions and receive answers synthesized strictly from your personal memories using RAG.
                  </p>
                </div>
              </Link>

              {/* Active Search Vault Card */}
              <Link
                href="/memories"
                style={{
                  backgroundColor: '#ffffff',
                  padding: '1.5rem',
                  borderRadius: '14px',
                  border: '1px solid #cbd5e1',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '1rem',
                  textDecoration: 'none',
                  boxShadow: '0 2px 4px rgba(0, 0, 0, 0.04)',
                }}
              >
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#1e293b', margin: 0 }}>
                      Search Vault
                    </h3>
                    <span style={{
                      fontSize: '0.7rem',
                      fontWeight: 700,
                      backgroundColor: '#dcfce7',
                      color: '#15803d',
                      padding: '0.15rem 0.5rem',
                      borderRadius: '9999px',
                    }}>
                      Active
                    </span>
                  </div>
                  <p style={{ fontSize: '0.85rem', color: '#64748b', marginTop: '0.5rem', marginBottom: 0 }}>
                    AI-assisted semantic vector retrieval and instant keyword search across all memories.
                  </p>
                </div>
              </Link>

              {/* Planned Future Roadmap Features */}
              {[
                { title: 'Voice Reflections', desc: 'Record audio memories with automated Whisper speech transcription.', tag: 'Roadmap' },
                { title: 'Family Tree', desc: 'Map multi-generational relationships and share memories across relatives.', tag: 'Roadmap' },
              ].map((action, idx) => (
                <div key={idx} style={{
                  backgroundColor: '#f8fafc',
                  padding: '1.5rem',
                  borderRadius: '14px',
                  border: '1px dashed #cbd5e1',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '1rem',
                  opacity: 0.85,
                }}>
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <h3 style={{ fontSize: '1rem', fontWeight: 600, color: '#64748b', margin: 0 }}>
                        {action.title}
                      </h3>
                      <span style={{
                        fontSize: '0.7rem',
                        fontWeight: 600,
                        backgroundColor: '#f1f5f9',
                        color: '#64748b',
                        padding: '0.15rem 0.5rem',
                        borderRadius: '9999px',
                      }}>
                        {action.tag}
                      </span>
                    </div>
                    <p style={{ fontSize: '0.85rem', color: '#94a3b8', marginTop: '0.5rem', marginBottom: 0 }}>
                      {action.desc}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
