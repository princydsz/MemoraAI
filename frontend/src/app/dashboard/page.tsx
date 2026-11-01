'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import ProtectedRoute from '../../components/ProtectedRoute';
import { useAuth } from '../../context/AuthContext';
import { getMemoriesApi } from '../../lib/memory';
import VoiceRecorderModal from '../../components/voice/VoiceRecorderModal';
import { Memory } from '../../types/memory';
import { getPatients } from '../../lib/family';
import { getDailyRoutine, completeRoutine, uncompleteRoutine } from '../../lib/routines';
import type { DailyRoutine, DailyRoutineItem, Patient } from '../../types/phase8';

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const [memoryCount, setMemoryCount] = useState<number | null>(null);
  const [isCountLoading, setIsCountLoading] = useState<boolean>(true);
  const [hasCountError, setHasCountError] = useState<boolean>(false);
  const [isVoiceModalOpen, setIsVoiceModalOpen] = useState<boolean>(false);
  const [isFamilyModalOpen, setIsFamilyModalOpen] = useState<boolean>(false);
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null);
  const [dailyRoutine, setDailyRoutine] = useState<DailyRoutine | null>(null);
  const [routinePatientId, setRoutinePatientId] = useState<string | null>(null);
  const [routineLoading, setRoutineLoading] = useState(true);

  // Time & Date Orientation State
  const [greeting, setGreeting] = useState<string>('Welcome');
  const [formattedDate, setFormattedDate] = useState<string>('');
  const [formattedTime, setFormattedTime] = useState<string>('');

  useEffect(() => {
    const updateDateTime = () => {
      const now = new Date();
      const hour = now.getHours();
      if (hour < 12) {
        setGreeting('Good morning');
      } else if (hour < 18) {
        setGreeting('Good afternoon');
      } else {
        setGreeting('Good evening');
      }

      setFormattedDate(
        now.toLocaleDateString('en-US', {
          weekday: 'long',
          month: 'long',
          day: 'numeric',
          year: 'numeric',
        })
      );

      setFormattedTime(
        now.toLocaleTimeString('en-US', {
          hour: 'numeric',
          minute: '2-digit',
        })
      );
    };

    updateDateTime();
    const interval = setInterval(updateDateTime, 30000);
    return () => clearInterval(interval);
  }, []);

  const fetchMemoryCount = () => {
    setIsCountLoading(true);
    setHasCountError(false);
    getMemoriesApi()
      .then((mems) => {
        setMemoryCount(mems.length);
        setIsCountLoading(false);
      })
      .catch(() => {
        // When backend is unavailable, do NOT assume 0 memories; preserve error state
        setHasCountError(true);
        setIsCountLoading(false);
      });
  };

  useEffect(() => {
    fetchMemoryCount();
  }, []);

  useEffect(() => {
    const loadDashboardRoutine = async () => {
      setRoutineLoading(true);
      try {
        const patients: Patient[] = await getPatients();
        const patient = patients[0] ?? (user ? {
          id: user.id,
          name: user.name,
          relationshipType: 'CHILD' as const,
          permissionLevel: 'FULL_ACCESS' as const,
          status: 'ACTIVE' as const,
        } : null);
        if (!patient) return;
        setRoutinePatientId(patient.id);
        setDailyRoutine(await getDailyRoutine(patient.id));
      } catch {
        setDailyRoutine(null);
      } finally {
        setRoutineLoading(false);
      }
    };
    if (user) loadDashboardRoutine();
  }, [user]);

  // Keyboard accessibility for Family modal
  useEffect(() => {
    if (!isFamilyModalOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setIsFamilyModalOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isFamilyModalOpen]);

  const handleMemoryCreated = (newMemory: Memory) => {
    setMemoryCount((prev) => (prev !== null ? prev + 1 : 1));
    setHasCountError(false);
    setFeedbackMessage(
      `"${newMemory.title || 'Your memory'}" has been safely saved in your vault.`
    );
  };

  const toggleDashboardRoutine = async (item: DailyRoutineItem) => {
    if (!routinePatientId) return;
    try {
      if (item.completed) {
        await uncompleteRoutine(routinePatientId, item.id, dailyRoutine?.date);
      } else {
        await completeRoutine(routinePatientId, item.id, dailyRoutine?.date);
      }
      setDailyRoutine(await getDailyRoutine(routinePatientId, dailyRoutine?.date));
    } catch (err) {
      setFeedbackMessage(err instanceof Error ? err.message : 'Unable to update the routine.');
    }
  };

  const scrollToSchedule = () => {
    const el = document.getElementById('todays-schedule');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const firstName = user?.name ? user.name.split(' ')[0] : 'Friend';

  let countBadgeText = 'Checking vault...';
  let countAriaLabel = 'My Memories - Checking vault';
  if (!isCountLoading) {
    if (hasCountError) {
      countBadgeText = 'Tap to view';
      countAriaLabel = 'My Memories - Tap to view your vault';
    } else if (memoryCount !== null) {
      countBadgeText = `${memoryCount} ${memoryCount === 1 ? 'memory' : 'memories'} saved`;
      countAriaLabel = `My Memories - ${memoryCount} ${memoryCount === 1 ? 'memory' : 'memories'} saved in vault`;
    }
  }

  return (
    <ProtectedRoute>
      <div className="dashboard-shell page-shell">
        {/* Welcome & Orientation Header */}
        <section className="patient-welcome-card" aria-label="Welcome and daily orientation">
          <div className="patient-orientation-header">
            <div>
              <div style={{ display: 'flex', gap: '0.65rem', flexWrap: 'wrap', alignItems: 'center' }}>
                <span className="patient-orientation-banner">
                  <span>📅</span>
                  <span>{formattedDate || 'Today'}</span>
                </span>
                {formattedTime && (
                  <span className="patient-time-pill">
                    <span>🕐</span>
                    <span>{formattedTime}</span>
                  </span>
                )}
              </div>

              <h1 className="patient-greeting-title">
                {greeting}, {firstName}.
              </h1>

              <p className="patient-reassurance-text">
                Everything you need is right here. Take your time, explore your cherished memories, or talk with Memora whenever you are ready.
              </p>
            </div>

            <div className="header-actions">
              <Link href="/profile" className="ghost-button" aria-label="View user profile">
                My Profile
              </Link>
              <button
                type="button"
                className="ghost-button"
                onClick={logout}
                aria-label="Sign out of MemoraAI"
              >
                Sign out
              </button>
            </div>
          </div>

          {/* Feedback banner after saving a memory */}
          {feedbackMessage && (
            <div className="patient-notification-banner" role="status" aria-live="polite">
              <div className="patient-notification-content">
                <span style={{ fontSize: '1.3rem' }}>✓</span>
                <span>{feedbackMessage}</span>
              </div>
              <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center' }}>
                <Link
                  href="/memories"
                  className="secondary-button"
                  style={{ padding: '0.4rem 0.9rem', fontSize: '0.85rem' }}
                >
                  View in Vault
                </Link>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => setFeedbackMessage(null)}
                  style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem' }}
                  aria-label="Dismiss notification"
                >
                  Dismiss
                </button>
              </div>
            </div>
          )}
        </section>

        {/* 2x2 Large Touch Quick Actions Grid */}
        <section className="patient-actions-grid" aria-label="Quick actions">
          {/* Action 1: Talk to Memora */}
          <button
            type="button"
            className="patient-action-btn patient-action-btn--talk"
            onClick={() => setIsVoiceModalOpen(true)}
            aria-label="Talk to Memora - Tap to speak a thought, record a memory, or share how you are feeling"
          >
            <div className="patient-action-icon-wrapper patient-action-icon-wrapper--talk" aria-hidden="true">
              🎙️
            </div>
            <div className="patient-action-details">
              <div className="patient-action-title-row">
                <h2 className="patient-action-title">Talk to Memora</h2>
                <span className="patient-action-badge patient-action-badge--primary">
                  ● Voice Ready
                </span>
              </div>
              <p className="patient-action-desc">
                Tap to speak a thought, record a memory, or share how you are feeling.
              </p>
            </div>
          </button>

          {/* Action 2: My Memories */}
          <Link
            href="/memories"
            className="patient-action-btn patient-action-btn--memories"
            aria-label={countAriaLabel}
          >
            <div className="patient-action-icon-wrapper patient-action-icon-wrapper--memories" aria-hidden="true">
              📖
            </div>
            <div className="patient-action-details">
              <div className="patient-action-title-row">
                <h2 className="patient-action-title">My Memories</h2>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <span className="patient-action-badge patient-action-badge--accent">
                    {countBadgeText}
                  </span>
                  {hasCountError && (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        fetchMemoryCount();
                      }}
                      style={{
                        background: 'rgba(182, 90, 70, 0.12)',
                        border: '1px solid rgba(182, 90, 70, 0.3)',
                        color: 'var(--primary-hover)',
                        borderRadius: '999px',
                        padding: '0.15rem 0.55rem',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                        cursor: 'pointer',
                      }}
                      aria-label="Retry checking memory vault"
                    >
                      ↻ Retry
                    </button>
                  )}
                </div>
              </div>
              <p className="patient-action-desc">
                Browse your photo stories, family moments, and recorded reflections.
              </p>
            </div>
          </Link>

          {/* Action 3: Today's Schedule */}
          <button
            type="button"
            className="patient-action-btn patient-action-btn--schedule"
            onClick={scrollToSchedule}
            aria-label="Today's Schedule - Check your morning, afternoon, and evening routine"
          >
            <div className="patient-action-icon-wrapper patient-action-icon-wrapper--schedule" aria-hidden="true">
              ☀️
            </div>
            <div className="patient-action-details">
              <div className="patient-action-title-row">
                <h2 className="patient-action-title">Today&apos;s Schedule</h2>
                <span className="patient-action-badge patient-action-badge--highlight">
                  {dailyRoutine
                    ? `${dailyRoutine.morning.length + dailyRoutine.afternoon.length + dailyRoutine.evening.length} Daily Routines`
                    : 'Loading routines'}
                </span>
              </div>
              <p className="patient-action-desc">
                Check your morning, afternoon, and evening timeline.
              </p>
            </div>
          </button>

          {/* Action 4: My Family */}
          <button
            type="button"
            className="patient-action-btn patient-action-btn--family"
            onClick={() => setIsFamilyModalOpen(true)}
            aria-label="My Family - Connect with family members, caregivers, and your circle of care"
          >
            <div className="patient-action-icon-wrapper patient-action-icon-wrapper--family" aria-hidden="true">
              👥
            </div>
            <div className="patient-action-details">
              <div className="patient-action-title-row">
                <h2 className="patient-action-title">My Family</h2>
                <span className="patient-action-badge patient-action-badge--muted">
                  Care Circle
                </span>
              </div>
              <p className="patient-action-desc">
                Connect with family members, caregivers, and your circle of care.
              </p>
            </div>
          </button>
        </section>

        {/* Today's Schedule Timeline Section */}
        <section id="todays-schedule" className="patient-schedule-section" aria-label="Today's Schedule Timeline">
          <div className="patient-schedule-header">
            <div>
              <span className="eyebrow" style={{ marginBottom: '0.5rem' }}>
                Daily Routine
              </span>
              <h2 style={{ margin: '0.4rem 0 0.35rem', fontSize: 'clamp(1.6rem, 2.8vw, 2.2rem)', letterSpacing: '-0.04em' }}>
                Today&apos;s Schedule
              </h2>
              <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '1.05rem' }}>
                A calm, predictable rhythm to support your comfort and peace of mind throughout the day.
              </p>
            </div>

            <div className="patient-demo-badge">
              <span>{dailyRoutine?.date ? `Date: ${dailyRoutine.date}` : 'Loading schedule'}</span>
            </div>
          </div>

          <div className="patient-schedule-timeline">
            {routineLoading && <p>Loading today's routine...</p>}
            {!routineLoading && dailyRoutine && [
              ...dailyRoutine.morning,
              ...dailyRoutine.afternoon,
              ...dailyRoutine.evening,
            ].map(item => (
              <article className="patient-timeline-card" key={item.id}>
                <div>
                  <div className="patient-timeline-period">
                    <span className="patient-timeline-badge">
                      {item.period === 'MORNING' ? '☀️ Morning' : item.period === 'AFTERNOON' ? '🌤️ Afternoon' : '🌙 Evening'}
                    </span>
                    <span className="patient-timeline-time">{item.scheduledTime || 'Any time'}</span>
                  </div>
                  <h3 className="patient-timeline-title">{item.title}</h3>
                  <p className="patient-timeline-desc">{item.description || 'No additional details.'}</p>
                </div>
                <button
                  type="button"
                  className={item.completed ? 'secondary-button' : 'primary-button'}
                  onClick={() => toggleDashboardRoutine(item)}
                >
                  {item.completed ? 'Uncomplete' : 'Complete'}
                </button>
              </article>
            ))}
            {!routineLoading && !dailyRoutine && <p>No routine could be loaded for this account.</p>}
          </div>
        </section>

        {/* Orientation & Care Partner Assistance */}
        <section className="patient-care-panel" aria-label="Account and assistance overview">
          <div className="patient-care-card">
            <div>
              <div className="eyebrow" style={{ marginBottom: '0.6rem' }}>Your Information</div>
              <h3 style={{ margin: '0 0 0.85rem', fontSize: '1.25rem', letterSpacing: '-0.03em' }}>
                Personal Vault Details
              </h3>
              <div style={{ display: 'grid', gap: '0.65rem', fontSize: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.65rem 0.9rem', background: 'var(--surface-muted)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Signed In As</span>
                  <strong style={{ color: 'var(--text)' }}>{user?.name || 'Friend'}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.65rem 0.9rem', background: 'var(--surface-muted)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Email</span>
                  <span style={{ color: 'var(--text)', fontWeight: 600 }}>{user?.email || 'Not available'}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.65rem 0.9rem', background: 'var(--surface-muted)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Protected Memories</span>
                  <span style={{ color: 'var(--accent-strong)', fontWeight: 700 }}>
                    {memoryCount} {memoryCount === 1 ? 'moment' : 'moments'}
                  </span>
                </div>
              </div>
            </div>
            <div style={{ marginTop: '1.25rem' }}>
              <Link href="/profile" className="ghost-button" style={{ width: '100%', justifyContent: 'center' }}>
                Manage Profile &amp; Preferences
              </Link>
            </div>
          </div>

          <div className="patient-care-card">
            <div>
              <div className="eyebrow" style={{ marginBottom: '0.6rem' }}>Need Assistance?</div>
              <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.25rem', letterSpacing: '-0.03em' }}>
                Here to Help You Remember
              </h3>
              <p style={{ margin: '0 0 1rem', color: 'var(--text-muted)', fontSize: '1rem', lineHeight: '1.55' }}>
                If you feel unsure about a past event, family member, or memory, you can ask MemoraAI any question. Memora searches your private vault to give you gentle, accurate answers.
              </p>
            </div>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
              <Link href="/ask" className="secondary-button" style={{ flex: '1 1 auto', justifyContent: 'center' }}>
                Ask Memora a Question
              </Link>
              <button
                type="button"
                className="ghost-button"
                onClick={logout}
                style={{ flex: '0 0 auto' }}
              >
                Sign out
              </button>
            </div>
          </div>
        </section>

        {/* Integrated Voice Recorder Modal */}
        <VoiceRecorderModal
          isOpen={isVoiceModalOpen}
          onClose={() => setIsVoiceModalOpen(false)}
          onMemoryCreated={handleMemoryCreated}
          initialCategory="Reflections"
          initialTags="daily reflection, voice"
        />

        {/* Family & Care Circle Modal */}
        {isFamilyModalOpen && (
          <div
            className="memory-modal-backdrop"
            onClick={() => setIsFamilyModalOpen(false)}
            role="presentation"
          >
            <div
              className="memory-view-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="family-modal-title"
              style={{ maxWidth: '580px', gap: '1.4rem' }}
              onClick={(e) => e.stopPropagation()}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <div
                  className="patient-action-icon-wrapper patient-action-icon-wrapper--family"
                  style={{ width: '56px', height: '56px', fontSize: '1.5rem' }}
                  aria-hidden="true"
                >
                  👥
                </div>
                <div>
                  <span className="patient-demo-badge" style={{ marginBottom: '0.4rem' }}>
                    Care Circle
                  </span>
                  <h2 id="family-modal-title" style={{ margin: 0, fontSize: '1.45rem', letterSpacing: '-0.03em' }}>
                    Family &amp; Care Circle
                  </h2>
                </div>
              </div>

              <div style={{ color: 'var(--text-muted)', fontSize: '1.05rem', lineHeight: '1.6', display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
                <p style={{ margin: 0 }}>
                  Your Family dashboard shows Care Circle members, invitations, routines, appointments, memories and reminders.
                </p>
                <p style={{ margin: 0 }}>
                  Use your patient's Care Circle to manage care with the permissions granted by the backend.
                </p>
                <div style={{ padding: '0.9rem 1.1rem', borderRadius: 'var(--radius-sm)', background: 'var(--surface-muted)', border: '1px solid var(--border)', fontSize: '0.95rem' }}>
                  <strong style={{ color: 'var(--text)' }}>Connected:</strong> Care Circle members, routines, appointments and reminders.
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => setIsFamilyModalOpen(false)}
                  style={{ minHeight: '52px', padding: '0.75rem 1.4rem', fontSize: '1.05rem' }}
                >
                  Return to Dashboard
                </button>
                <Link
                  href="/family"
                  className="primary-button"
                  onClick={() => setIsFamilyModalOpen(false)}
                  style={{ minHeight: '52px', padding: '0.75rem 1.8rem', fontSize: '1.05rem' }}
                >
                  Open Family Dashboard
                </Link>
              </div>
            </div>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
