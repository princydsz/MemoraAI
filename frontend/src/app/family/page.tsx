'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import ProtectedRoute from '../../components/ProtectedRoute';
import { useAuth } from '../../context/AuthContext';
import {
  Appointment,
  CareCircleMember,
  DailyRoutine,
  FamilyContribution,
  FamilyNotification,
  NewContributionFormData,
  PatientMemory,
  PatientProfile,
} from '../../types/family';
import {
  initialAppointments,
  initialCareCircleMembers,
  initialContributions,
  initialMemories,
  initialNotifications,
  initialPatientProfile,
  initialRoutines,
} from '../../lib/familyMockData';

export default function FamilyDashboardPage() {
  const { user } = useAuth();

  // Mock State for Features
  const [patient, setPatient] = useState<PatientProfile>(initialPatientProfile);
  const [routines, setRoutines] = useState<DailyRoutine[]>(initialRoutines);
  const [memories] = useState<PatientMemory[]>(initialMemories);
  const [appointments] = useState<Appointment[]>(initialAppointments);
  const [contributions, setContributions] = useState<FamilyContribution[]>(initialContributions);
  const [careCircle] = useState<CareCircleMember[]>(initialCareCircleMembers);
  const [notifications, setNotifications] = useState<FamilyNotification[]>(initialNotifications);

  // UI Control State
  const [isContributeModalOpen, setIsContributeModalOpen] = useState<boolean>(false);
  const [isNotifPanelOpen, setIsNotifPanelOpen] = useState<boolean>(false);
  const [viewingMemory, setViewingMemory] = useState<PatientMemory | null>(null);
  const [demoNotice, setDemoNotice] = useState<string | null>(null);

  // Contribute Form State
  const [formData, setFormData] = useState<NewContributionFormData>({
    title: '',
    category: 'Family Tradition',
    date: new Date().toISOString().split('T')[0],
    description: '',
    photoFile: null,
    photoPreviewUrl: null,
    audioFile: null,
    audioPreviewUrl: null,
    contributorAttribution: user?.name ? `${user.name} (Family Member)` : 'Sarah Jenkins (Daughter)',
  });
  const [formError, setFormError] = useState<string | null>(null);
  const [showLivePreview, setShowLivePreview] = useState<boolean>(false);

  // Calculate unread notifications count
  const unreadCount = notifications.filter((n) => !n.isRead).length;

  // Keyboard accessibility for modals
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setIsContributeModalOpen(false);
        setIsNotifPanelOpen(false);
        setViewingMemory(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Update contributor attribution when user loads
  useEffect(() => {
    if (user?.name) {
      setFormData((prev) => ({
        ...prev,
        contributorAttribution: `${user.name} (${patient.relationshipToUser.split('&')[0].trim() || 'Family Member'})`,
      }));
    }
  }, [user, patient.relationshipToUser]);

  // Caregiver routine check-off toggle handler
  const handleToggleRoutine = (routineId: string) => {
    setRoutines((prevRoutines) => {
      const updated = prevRoutines.map((routine) => {
        if (routine.id === routineId) {
          const nextState = !routine.completed;
          return {
            ...routine,
            completed: nextState,
            completedAt: nextState ? new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }) : undefined,
            completedBy: nextState ? user?.name?.split(' ')[0] || 'Caregiver' : undefined,
          };
        }
        return routine;
      });

      // Update patient wellness routines count in local state
      const completedCount = updated.filter((r) => r.completed).length;
      setPatient((prevPatient) => ({
        ...prevPatient,
        wellness: {
          ...prevPatient.wellness,
          routinesCompleted: completedCount,
        },
      }));

      return updated;
    });

    setDemoNotice(
      'Routine updated in local demo state. Caregiver synchronization with the patient dashboard will connect in Phase 12.'
    );
  };

  // Like contribution handler
  const handleLikeContribution = (contribId: string) => {
    setContributions((prev) =>
      prev.map((c) => (c.id === contribId ? { ...c, likesCount: c.likesCount + 1 } : c))
    );
  };

  // Mark all notifications as read
  const handleMarkAllRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
  };

  // Mark single notification as read
  const handleToggleNotificationRead = (notifId: string) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === notifId ? { ...n, isRead: !n.isRead } : n))
    );
  };

  // Handle photo file selection
  const handlePhotoSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const previewUrl = URL.createObjectURL(file);
      setFormData((prev) => ({
        ...prev,
        photoFile: file,
        photoPreviewUrl: previewUrl,
      }));
    }
  };

  // Handle sample audio toggle simulation
  const handleToggleSampleAudio = () => {
    if (formData.audioPreviewUrl) {
      setFormData((prev) => ({
        ...prev,
        audioFile: null,
        audioPreviewUrl: null,
      }));
    } else {
      setFormData((prev) => ({
        ...prev,
        audioPreviewUrl: 'simulated-audio-recording.wav',
      }));
    }
  };

  const resetForm = () => {
    setFormData({
      title: '',
      category: 'Family Tradition',
      date: new Date().toISOString().split('T')[0],
      description: '',
      photoFile: null,
      photoPreviewUrl: null,
      audioFile: null,
      audioPreviewUrl: null,
      contributorAttribution: user?.name ? `${user.name} (Family Member)` : 'Sarah Jenkins (Daughter)',
    });
    setFormError(null);
    setShowLivePreview(false);
  };

  // Submit demo contribution
  const handleContributeSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.title.trim() || !formData.description.trim()) {
      setFormError('Please provide both a memory title and description.');
      return;
    }

    const newContrib: FamilyContribution = {
      id: `contrib-${Date.now()}`,
      title: formData.title.trim(),
      description: formData.description.trim(),
      category: formData.category,
      date: 'Just now',
      contributorId: user?.id || 'usr-family-current',
      contributorName: user?.name || 'Sarah Jenkins',
      contributorRelationship: patient.relationshipToUser.split('&')[0].trim() || 'Family Member',
      hasPhoto: !!formData.photoPreviewUrl,
      photoUrl: formData.photoPreviewUrl || undefined,
      hasAudio: !!formData.audioPreviewUrl,
      audioDuration: formData.audioPreviewUrl ? '0:35' : undefined,
      likesCount: 1,
      timestamp: new Date().toISOString(),
    };

    setContributions((prev) => [newContrib, ...prev]);
    setIsContributeModalOpen(false);
    setDemoNotice(
      `Demo contribution "${newContrib.title}" added to family stream. (Note: In Phase 12, this will be safely linked to Eleanor's Memory Vault with contributor attribution).`
    );
    resetForm();
  };

  return (
    <ProtectedRoute>
      <div className="family-dashboard-shell page-shell">
        {/* Top Header */}
        <header className="family-top-header" aria-label="Family dashboard header">
          <div>
            <div className="family-header-badges">
              <span className="eyebrow">Caregiver &amp; Family Circle</span>
              <span className="family-demo-pill">
                <span>⚠️</span>
                <span>Demo Mode • Backend Family Sync in Phase 12</span>
              </span>
            </div>

            <h1 className="section-title" style={{ marginTop: '0.4rem' }}>
              Family Dashboard
            </h1>

            <p className="section-copy">
              Coordinate care routines, share meaningful family contributions, and stay closely connected with Eleanor.
            </p>
          </div>

          <div className="family-header-controls">
            <button
              type="button"
              className="family-notif-btn"
              onClick={() => setIsNotifPanelOpen(!isNotifPanelOpen)}
              aria-label={`View notifications, ${unreadCount} unread`}
              aria-expanded={isNotifPanelOpen}
            >
              <span>🔔</span>
              <span>Updates</span>
              {unreadCount > 0 && <span className="family-notif-badge">{unreadCount}</span>}
            </button>

            <button
              type="button"
              className="primary-button"
              onClick={() => setIsContributeModalOpen(true)}
              aria-label="Contribute a family memory"
            >
              <span>+</span>
              <span>Contribute a Memory</span>
            </button>
          </div>
        </header>

        {/* Demo Action Notice */}
        {demoNotice && (
          <div
            className="patient-notification-banner"
            role="status"
            aria-live="polite"
            style={{ marginTop: '1.25rem' }}
          >
            <div className="patient-notification-content">
              <span style={{ fontSize: '1.2rem' }}>ℹ️</span>
              <span>{demoNotice}</span>
            </div>
            <button
              type="button"
              className="ghost-button"
              onClick={() => setDemoNotice(null)}
              style={{ padding: '0.35rem 0.8rem', fontSize: '0.85rem' }}
              aria-label="Dismiss notice"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* In-App Notifications Drawer */}
        {isNotifPanelOpen && (
          <aside className="family-notifications-panel" role="region" aria-label="Notifications panel">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <h3 style={{ margin: 0, fontSize: '1.15rem' }}>Care Updates</h3>
                {unreadCount > 0 && <span className="family-notif-badge">{unreadCount}</span>}
              </div>
              <button
                type="button"
                className="ghost-button"
                onClick={() => setIsNotifPanelOpen(false)}
                style={{ padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
                aria-label="Close notifications panel"
              >
                ✕
              </button>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                Demo activity log
              </span>
              {unreadCount > 0 && (
                <button
                  type="button"
                  onClick={handleMarkAllRead}
                  style={{ background: 'none', border: 'none', color: 'var(--primary)', fontSize: '0.82rem', fontWeight: 700, cursor: 'pointer' }}
                >
                  Mark all as read
                </button>
              )}
            </div>

            <div style={{ display: 'grid', gap: '0.65rem' }}>
              {notifications.map((n) => (
                <div
                  key={n.id}
                  className={`family-notification-item ${!n.isRead ? 'family-notification-item--unread' : ''}`}
                >
                  <div className="family-notification-header">
                    <span className="family-notification-title">{n.title}</span>
                    <span className="family-notification-time">{n.timestamp}</span>
                  </div>
                  <p className="family-notification-msg">{n.message}</p>
                  <button
                    type="button"
                    onClick={() => handleToggleNotificationRead(n.id)}
                    style={{
                      alignSelf: 'flex-start',
                      background: 'none',
                      border: 'none',
                      color: 'var(--accent-strong)',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      padding: 0,
                      marginTop: '0.2rem',
                    }}
                  >
                    {n.isRead ? 'Mark as unread' : 'Mark as read'}
                  </button>
                </div>
              ))}
            </div>
          </aside>
        )}

        {/* Feature 1: Patient Overview & Wellness Card */}
        <section className="family-patient-card" aria-label="Patient overview and daily wellness">
          <div className="family-patient-identity">
            <div className="family-patient-avatar" aria-hidden="true">
              👵
            </div>
            <div className="family-patient-meta">
              <span className="patient-demo-badge" style={{ marginBottom: '0.35rem' }}>
                Linked Patient Profile (Mock)
              </span>
              <h2>{patient.name}</h2>
              <p className="family-patient-subtitle">
                Age {patient.age} • {patient.conditionSummary}
              </p>
              <div className="family-relationship-tag">
                <span>👤</span>
                <span>You are signed in as {user?.name || 'Sarah'} ({patient.relationshipToUser})</span>
              </div>
              <div style={{ marginTop: '0.5rem', fontSize: '0.86rem', color: 'var(--accent-strong)', fontWeight: 600 }}>
                ● {patient.lastActive}
              </div>
            </div>
          </div>

          <div className="family-wellness-grid">
            {/* Mood Box */}
            <div className="family-wellness-box">
              <div>
                <span className="family-wellness-label">Today&apos;s Mood</span>
                <div className="family-wellness-val">
                  <span>{patient.wellness.moodEmoji}</span>
                  <span style={{ fontSize: '1.1rem' }}>{patient.wellness.mood}</span>
                </div>
              </div>
              <p className="family-wellness-note">{patient.wellness.moodNote}</p>
            </div>

            {/* Hydration Box */}
            <div className="family-wellness-box">
              <div>
                <span className="family-wellness-label">Hydration</span>
                <div className="family-wellness-val">
                  <span>💧</span>
                  <span>{patient.wellness.hydrationGlasses} / {patient.wellness.hydrationGoal} glasses</span>
                </div>
              </div>
              <div className="family-progress-track" aria-hidden="true">
                <div
                  className="family-progress-fill"
                  style={{
                    width: `${Math.min(100, (patient.wellness.hydrationGlasses / patient.wellness.hydrationGoal) * 100)}%`,
                  }}
                />
              </div>
              <p className="family-wellness-note">Goal: 6 glasses daily for cognitive wellness.</p>
            </div>

            {/* Activities Completed Box */}
            <div className="family-wellness-box">
              <div>
                <span className="family-wellness-label">Routines</span>
                <div className="family-wellness-val">
                  <span>📋</span>
                  <span>{patient.wellness.routinesCompleted} / {patient.wellness.routinesTotal} done</span>
                </div>
              </div>
              <div className="family-progress-track" aria-hidden="true">
                <div
                  className="family-progress-fill"
                  style={{
                    width: `${Math.min(100, (patient.wellness.routinesCompleted / patient.wellness.routinesTotal) * 100)}%`,
                    background: 'linear-gradient(90deg, var(--primary), var(--primary-hover))',
                  }}
                />
              </div>
              <p className="family-wellness-note">
                {patient.wellness.routinesCompleted === patient.wellness.routinesTotal
                  ? 'All daily routines completed!'
                  : `${patient.wellness.routinesTotal - patient.wellness.routinesCompleted} routines remaining today.`}
              </p>
            </div>
          </div>
        </section>

        {/* Main 2-Column Sections Grid */}
        <div className="family-main-grid">
          {/* Left Column: Routines + Family Contributions */}
          <div className="family-left-col">
            {/* Feature 2: Daily Activities and Routines */}
            <section className="family-card-panel" aria-label="Daily routines and caregiver check-offs">
              <div className="family-panel-header">
                <div>
                  <h2 className="family-panel-title">
                    <span>🗓️</span>
                    <span>Daily Activities &amp; Routines</span>
                  </h2>
                  <p style={{ margin: '0.25rem 0 0', color: 'var(--text-muted)', fontSize: '0.92rem' }}>
                    Caregivers can review schedule times and check off routines as they are completed.
                  </p>
                </div>
                <span className="family-demo-pill">Local Demo Toggles</span>
              </div>

              <div className="family-routines-list">
                {routines.map((routine) => (
                  <article
                    key={routine.id}
                    className={`family-routine-item ${routine.completed ? 'family-routine-item--completed' : ''}`}
                  >
                    <div className="family-routine-info">
                      <div className="family-routine-header-row">
                        <span className="family-routine-time">{routine.timeSlot}</span>
                        <span
                          className={`patient-timeline-badge ${
                            routine.period === 'morning'
                              ? 'patient-action-badge--highlight'
                              : routine.period === 'afternoon'
                              ? 'patient-action-badge--accent'
                              : 'patient-action-badge--primary'
                          }`}
                        >
                          {routine.period === 'morning' ? '☀️ Morning' : routine.period === 'afternoon' ? '🌤️ Afternoon' : '🌙 Evening'}
                        </span>
                        {routine.completed && (
                          <span style={{ fontSize: '0.78rem', color: 'var(--accent-strong)', fontWeight: 700 }}>
                            ✓ Done {routine.completedAt && `at ${routine.completedAt}`} {routine.completedBy && `by ${routine.completedBy}`}
                          </span>
                        )}
                      </div>

                      <h3 className="family-routine-title">{routine.title}</h3>
                      <p className="family-routine-desc">{routine.description}</p>

                      {routine.caregiverTip && (
                        <div className="family-routine-tip">
                          <span>💡</span>
                          <span><strong>Caregiver Tip:</strong> {routine.caregiverTip}</span>
                        </div>
                      )}
                    </div>

                    <button
                      type="button"
                      className={`family-check-toggle-btn ${
                        routine.completed ? 'family-check-toggle-btn--done' : 'family-check-toggle-btn--pending'
                      }`}
                      onClick={() => handleToggleRoutine(routine.id)}
                      aria-label={`${routine.completed ? 'Reopen' : 'Mark as done'} ${routine.title}`}
                    >
                      {routine.completed ? '✓ Completed' : 'Mark Done'}
                    </button>
                  </article>
                ))}
              </div>

              <div style={{ marginTop: '1rem', padding: '0.75rem 1rem', background: 'var(--surface-muted)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                ℹ️ <strong>Note:</strong> Routine check-offs update local state for UI demonstration. In Phase 12, Person 2&apos;s routine persistence API will sync completed routines across patient and caregiver screens in real time.
              </div>
            </section>

            {/* Feature 5: Family Contributions */}
            <section className="family-card-panel" aria-label="Family memory contributions stream">
              <div className="family-panel-header">
                <div>
                  <h2 className="family-panel-title">
                    <span>💌</span>
                    <span>Family Contributions Stream</span>
                  </h2>
                  <p style={{ margin: '0.25rem 0 0', color: 'var(--text-muted)', fontSize: '0.92rem' }}>
                    Photo stories, warm voice notes, and milestone memories shared by family members.
                  </p>
                </div>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ padding: '0.45rem 1rem', fontSize: '0.88rem' }}
                  onClick={() => setIsContributeModalOpen(true)}
                >
                  + Add Memory
                </button>
              </div>

              <div className="family-contrib-list">
                {contributions.map((contrib) => (
                  <article key={contrib.id} className="family-contrib-card">
                    <div className="family-contrib-header">
                      <div className="family-contributor-tag">
                        <span>💙</span>
                        <span>{contrib.contributorName} ({contrib.contributorRelationship})</span>
                      </div>
                      <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>{contrib.date}</span>
                    </div>

                    <h3 className="family-contrib-title">{contrib.title}</h3>
                    <p className="family-contrib-desc">{contrib.description}</p>

                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                        {contrib.hasPhoto && (
                          <span className="family-contrib-media-badge">
                            <span>📷</span>
                            <span>Attached Photo</span>
                          </span>
                        )}
                        {contrib.hasAudio && (
                          <span className="family-contrib-media-badge" style={{ color: 'var(--primary-hover)' }}>
                            <span>🎙️</span>
                            <span>Audio Note ({contrib.audioDuration || 'Voice note'})</span>
                          </span>
                        )}
                        <span className="status-badge">{contrib.category}</span>
                      </div>

                      <button
                        type="button"
                        onClick={() => handleLikeContribution(contrib.id)}
                        className="ghost-button"
                        style={{ padding: '0.25rem 0.65rem', fontSize: '0.82rem' }}
                        aria-label={`Like contribution from ${contrib.contributorName}, current likes: ${contrib.likesCount}`}
                      >
                        ❤️ {contrib.likesCount}
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          </div>

          {/* Right Column: Recent Patient Memories + Appointments + Care Circle */}
          <div className="family-right-col">
            {/* Feature 4: Upcoming Appointments */}
            <section className="family-card-panel" aria-label="Upcoming appointments and medical reminders">
              <div className="family-panel-header">
                <div>
                  <h2 className="family-panel-title">
                    <span>🏥</span>
                    <span>Upcoming Appointments</span>
                  </h2>
                  <p style={{ margin: '0.2rem 0 0', color: 'var(--text-muted)', fontSize: '0.88rem' }}>
                    Medical visits and wellness therapy sessions.
                  </p>
                </div>
                <span className="family-demo-pill">Mock Schedule</span>
              </div>

              <div className="family-appointments-list">
                {appointments.map((apt) => {
                  const [month, day] = apt.date.split(' ');
                  return (
                    <article key={apt.id} className="family-appointment-item">
                      <div className="family-appointment-datebox" aria-hidden="true">
                        <span className="family-appointment-datebox-month">{month}</span>
                        <span className="family-appointment-datebox-day">{day?.replace(',', '')}</span>
                      </div>

                      <div className="family-appointment-details">
                        <h3 className="family-appointment-title">{apt.title}</h3>
                        <p className="family-appointment-clinic">
                          <strong>{apt.doctorName}</strong> • {apt.time}
                        </p>
                        <p style={{ margin: '0.15rem 0 0', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                          📍 {apt.clinicName}
                        </p>
                        <div className="family-appointment-notes">
                          <strong>Note:</strong> {apt.notes}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            </section>

            {/* Feature 3: Recent Patient Memories */}
            <section className="family-card-panel" aria-label="Recent patient memories">
              <div className="family-panel-header">
                <div>
                  <h2 className="family-panel-title">
                    <span>📖</span>
                    <span>Recent Patient Memories</span>
                  </h2>
                  <p style={{ margin: '0.2rem 0 0', color: 'var(--text-muted)', fontSize: '0.88rem' }}>
                    Stories and reflections preserved in Eleanor&apos;s vault.
                  </p>
                </div>
                <Link
                  href="/memories"
                  className="ghost-button"
                  style={{ padding: '0.35rem 0.8rem', fontSize: '0.82rem' }}
                >
                  View Vault
                </Link>
              </div>

              <div className="family-memories-grid">
                {memories.map((mem) => (
                  <article key={mem.id} className="family-memory-card">
                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span className="family-memory-category">{mem.category}</span>
                        <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{mem.date}</span>
                      </div>
                      <h3 className="family-memory-title">{mem.title}</h3>
                      <p className="family-memory-snippet">
                        {mem.content.length > 100 ? `${mem.content.slice(0, 100)}...` : mem.content}
                      </p>
                    </div>

                    <div style={{ marginTop: '0.85rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ display: 'flex', gap: '0.3rem', flexWrap: 'wrap' }}>
                        {mem.tags.slice(0, 2).map((tag) => (
                          <span key={tag} style={{ fontSize: '0.72rem', color: 'var(--accent-strong)', fontWeight: 600 }}>
                            #{tag}
                          </span>
                        ))}
                      </div>
                      <button
                        type="button"
                        onClick={() => setViewingMemory(mem)}
                        className="ghost-button"
                        style={{ padding: '0.25rem 0.6rem', fontSize: '0.78rem' }}
                        aria-label={`Read story: ${mem.title}`}
                      >
                        Read Story
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </section>

            {/* Feature 7: Care Circle and Permissions */}
            <section className="family-card-panel" aria-label="Care circle and permissions directory">
              <div className="family-panel-header">
                <div>
                  <h2 className="family-panel-title">
                    <span>👥</span>
                    <span>Care Circle &amp; Permissions</span>
                  </h2>
                  <p style={{ margin: '0.2rem 0 0', color: 'var(--text-muted)', fontSize: '0.88rem' }}>
                    Designated family members and role permissions.
                  </p>
                </div>
                <span className="family-demo-pill">Informational Only</span>
              </div>

              <div className="family-members-grid">
                {careCircle.map((member) => (
                  <div key={member.id} className="family-member-card">
                    <div className="family-member-avatar" aria-hidden="true">
                      {member.avatarLetter}
                    </div>
                    <div className="family-member-info">
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', justifyContent: 'space-between' }}>
                        <h3 className="family-member-name">{member.name}</h3>
                        {member.isPrimary && (
                          <span style={{ fontSize: '0.68rem', fontWeight: 800, color: 'var(--primary)', textTransform: 'uppercase' }}>
                            Primary
                          </span>
                        )}
                      </div>
                      <p className="family-member-role">
                        {member.relationship} • {member.role}
                      </p>
                      <span
                        className={`family-permission-chip ${
                          member.permission === 'Full Access'
                            ? 'family-permission-chip--full'
                            : member.permission === 'Contribute'
                            ? 'family-permission-chip--contribute'
                            : 'family-permission-chip--view'
                        }`}
                      >
                        🔒 {member.permission}
                      </span>
                    </div>
                  </div>
                ))}
              </div>

              <div style={{ marginTop: '1rem', padding: '0.75rem', background: 'var(--surface-muted)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                ℹ️ <strong>Permissions Note:</strong> Displayed permissions are informational mock data. Real access control and invitation links will be enforced by Person 2&apos;s Spring Boot backend security filters.
              </div>
            </section>
          </div>
        </div>

        {/* Feature 6: Contribute a Memory Modal */}
        {isContributeModalOpen && (
          <div
            className="memory-modal-backdrop"
            onClick={() => setIsContributeModalOpen(false)}
            role="presentation"
          >
            <div
              className="memory-view-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="contribute-modal-title"
              style={{ maxWidth: '640px', gap: '1.25rem' }}
              onClick={(e) => e.stopPropagation()}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <span className="patient-demo-badge" style={{ marginBottom: '0.35rem' }}>
                    Family Contribution Mode (Demo)
                  </span>
                  <h2 id="contribute-modal-title" style={{ margin: '0.3rem 0 0', fontSize: '1.45rem', letterSpacing: '-0.03em' }}>
                    Contribute a Memory to Eleanor&apos;s Vault
                  </h2>
                  <p style={{ margin: '0.25rem 0 0', color: 'var(--text-muted)', fontSize: '0.92rem' }}>
                    Share photos, warm messages, or recorded voice notes to comfort and reassure Eleanor.
                  </p>
                </div>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => setIsContributeModalOpen(false)}
                  style={{ padding: '0.35rem 0.65rem', fontSize: '0.9rem' }}
                  aria-label="Close contribute memory modal"
                >
                  ✕
                </button>
              </div>

              {formError && (
                <div style={{ padding: '0.75rem 1rem', background: 'var(--danger-background)', border: '1px solid var(--danger-border)', borderRadius: 'var(--radius-sm)', color: 'var(--danger)', fontSize: '0.9rem' }}>
                  {formError}
                </div>
              )}

              <form onSubmit={handleContributeSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div className="family-form-group">
                  <label htmlFor="contrib-title" className="family-form-label">
                    Memory Title *
                  </label>
                  <input
                    id="contrib-title"
                    type="text"
                    required
                    className="family-form-input"
                    placeholder="e.g. Leo's First Soccer Goal / 1980 Family Picnic"
                    value={formData.title}
                    onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  <div className="family-form-group">
                    <label htmlFor="contrib-category" className="family-form-label">
                      Category
                    </label>
                    <select
                      id="contrib-category"
                      className="family-form-select"
                      value={formData.category}
                      onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                    >
                      <option value="Family Tradition">Family Tradition</option>
                      <option value="Milestone">Milestone Event</option>
                      <option value="Vacation">Trip &amp; Vacation</option>
                      <option value="Voice Note">Voice Note</option>
                      <option value="Everyday Story">Everyday Story</option>
                    </select>
                  </div>

                  <div className="family-form-group">
                    <label htmlFor="contrib-date" className="family-form-label">
                      Date of Event
                    </label>
                    <input
                      id="contrib-date"
                      type="date"
                      className="family-form-input"
                      value={formData.date}
                      onChange={(e) => setFormData({ ...formData, date: e.target.value })}
                    />
                  </div>
                </div>

                <div className="family-form-group">
                  <label htmlFor="contrib-desc" className="family-form-label">
                    Description &amp; Reassuring Message *
                  </label>
                  <textarea
                    id="contrib-desc"
                    required
                    rows={4}
                    className="family-form-textarea"
                    placeholder="Share the story behind this memory, who was there, and why it is special..."
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  />
                </div>

                {/* Media Attachments Section */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  {/* Photo Attachment */}
                  <div className="family-form-group">
                    <label htmlFor="contrib-photo" className="family-form-label">
                      Attach Photo (Optional)
                    </label>
                    <input
                      id="contrib-photo"
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      onChange={handlePhotoSelect}
                      style={{ fontSize: '0.85rem' }}
                    />
                    {formData.photoPreviewUrl && (
                      <div style={{ marginTop: '0.4rem', fontSize: '0.8rem', color: 'var(--accent-strong)' }}>
                        ✓ Photo attached for preview
                      </div>
                    )}
                  </div>

                  {/* Audio Attachment Simulation */}
                  <div className="family-form-group">
                    <label className="family-form-label">
                      Attach Voice Note (Optional)
                    </label>
                    <button
                      type="button"
                      onClick={handleToggleSampleAudio}
                      className={formData.audioPreviewUrl ? 'secondary-button' : 'ghost-button'}
                      style={{ padding: '0.55rem 0.9rem', fontSize: '0.85rem', justifyContent: 'center' }}
                    >
                      {formData.audioPreviewUrl ? '✓ Audio Note Attached (0:35)' : '🎙️ Record / Attach Voice Note'}
                    </button>
                  </div>
                </div>

                {/* Contributor Attribution Preview */}
                <div style={{ padding: '0.75rem 1rem', background: 'var(--surface-muted)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)', fontSize: '0.88rem' }}>
                  <strong>Contributor Attribution:</strong> {formData.contributorAttribution}
                  <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                    Target Patient ID: <code>{patient.id}</code> (Preserved for Person 2 backend integration)
                  </div>
                </div>

                {/* Live Preview Toggle */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <button
                    type="button"
                    onClick={() => setShowLivePreview(!showLivePreview)}
                    style={{ background: 'none', border: 'none', color: 'var(--accent-strong)', fontSize: '0.88rem', fontWeight: 700, cursor: 'pointer' }}
                  >
                    {showLivePreview ? '▲ Hide Live Preview' : '▼ Show Live Preview'}
                  </button>
                </div>

                {showLivePreview && (
                  <div style={{ padding: '1rem', border: '1.5px dashed var(--accent)', borderRadius: 'var(--radius-sm)', background: 'var(--surface)' }}>
                    <div style={{ fontSize: '0.75rem', fontWeight: 800, textTransform: 'uppercase', color: 'var(--accent-strong)', marginBottom: '0.4rem' }}>
                      Preview: How this memory appears in the family stream
                    </div>
                    <div className="family-contributor-tag">
                      <span>💙</span>
                      <span>{user?.name || 'Sarah Jenkins'} ({patient.relationshipToUser.split('&')[0].trim()})</span>
                    </div>
                    <h4 style={{ margin: '0.35rem 0 0.2rem', fontSize: '1.1rem' }}>{formData.title || 'Untitled Memory'}</h4>
                    <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--text-muted)' }}>
                      {formData.description || 'Description will appear here...'}
                    </p>
                    <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
                      <span className="status-badge">{formData.category}</span>
                      {formData.photoPreviewUrl && <span className="family-contrib-media-badge">📷 Photo</span>}
                      {formData.audioPreviewUrl && <span className="family-contrib-media-badge">🎙️ Audio Note</span>}
                    </div>
                  </div>
                )}

                {/* Form Action Buttons */}
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
                  <button
                    type="button"
                    className="ghost-button"
                    onClick={() => setIsContributeModalOpen(false)}
                  >
                    Cancel
                  </button>
                  <button type="submit" className="primary-button">
                    Submit Contribution (Demo)
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Read Memory Modal */}
        {viewingMemory && (
          <div
            className="memory-modal-backdrop"
            onClick={() => setViewingMemory(null)}
            role="presentation"
          >
            <div
              className="memory-view-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="view-memory-title"
              style={{ maxWidth: '620px' }}
              onClick={(e) => e.stopPropagation()}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <span className="family-memory-category" style={{ marginBottom: '0.4rem' }}>
                    {viewingMemory.category}
                  </span>
                  <h2 id="view-memory-title" style={{ margin: '0.3rem 0 0', fontSize: '1.45rem', letterSpacing: '-0.03em' }}>
                    {viewingMemory.title}
                  </h2>
                  <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>{viewingMemory.date}</span>
                </div>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => setViewingMemory(null)}
                  style={{ padding: '0.35rem 0.65rem' }}
                  aria-label="Close memory modal"
                >
                  ✕
                </button>
              </div>

              <div style={{ color: 'var(--text)', fontSize: '1.05rem', lineHeight: '1.6' }}>
                <p>{viewingMemory.content}</p>
              </div>

              <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                {viewingMemory.tags.map((tag) => (
                  <span key={tag} className="status-badge">
                    #{tag}
                  </span>
                ))}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => setViewingMemory(null)}
                >
                  Done Reading
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}

