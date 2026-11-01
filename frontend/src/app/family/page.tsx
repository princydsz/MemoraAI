'use client';

import React, { useEffect, useState } from 'react';
import ProtectedRoute from '../../components/ProtectedRoute';
import { useAuth } from '../../context/AuthContext';
import {
  createInvitation,
  getMembers,
  getPatients,
  getPendingInvitations,
  leaveCareCircle,
  revokeInvitation,
  revokeMember,
  updateMember,
  createPatient,
  redeemInvitation,
} from '../../lib/family';
import { getDailyRoutine, completeRoutine, uncompleteRoutine, createRoutine } from '../../lib/routines';
import { getAppointments, createAppointment, cancelAppointment, completeAppointment } from '../../lib/appointments';
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../../lib/notifications';
import type { CareCircleMember, DailyRoutine, Invitation, NotificationItem, Page, Patient, PermissionLevel, RelationshipType } from '../../types/phase8';

export default function FamilyPage() {
  const { user } = useAuth();
  const [patients, setPatients] = useState<Patient[]>([]);
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null);
  const [members, setMembers] = useState<CareCircleMember[]>([]);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [routine, setRoutine] = useState<DailyRoutine | null>(null);
  const [appointments, setAppointments] = useState<Page<import('../../types/phase8').Appointment> | null>(null);
  const [notifications, setNotifications] = useState<Page<NotificationItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newPatientName, setNewPatientName] = useState('');
  const [newPatientRelationship, setNewPatientRelationship] = useState<RelationshipType>('CHILD');
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRelationship, setInviteRelationship] = useState<RelationshipType>('CHILD');
  const [invitePermission, setInvitePermission] = useState<PermissionLevel>('CONTRIBUTE');
  const [routineTitle, setRoutineTitle] = useState('');
  const [routinePeriod, setRoutinePeriod] = useState<'MORNING' | 'AFTERNOON' | 'EVENING'>('MORNING');
  const [routineTime, setRoutineTime] = useState('');
  const [appointmentTitle, setAppointmentTitle] = useState('');
  const [appointmentDate, setAppointmentDate] = useState('');
  const [appointmentTime, setAppointmentTime] = useState('');
  const [invitationToken, setInvitationToken] = useState('');
  const [appointmentView, setAppointmentView] = useState<'UPCOMING' | 'PAST'>('UPCOMING');

  const canManage = selectedPatient?.permissionLevel === 'FULL_ACCESS';

  const loadPatientData = async (patient: Patient) => {
    setLoading(true);
    setError(null);
    try {
      const [memberData, invitationData, routineData, appointmentData, notificationData] = await Promise.all([
        getMembers(patient.id).catch(() => []),
        getPendingInvitations(patient.id).catch(() => []),
        getDailyRoutine(patient.id),
        getAppointments(patient.id, { upcoming: true, size: 10 }),
        getNotifications(0, 10),
      ]);
      setMembers(memberData);
      setInvitations(invitationData);
      setRoutine(routineData);
      setAppointments(appointmentData);
      setNotifications(notificationData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load patient information.');
    } finally {
      setLoading(false);
    }
  };

  const initialize = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getPatients();
      let available = data;
      if (available.length === 0 && user) {
        available = [{
          id: user.id,
          name: user.name,
          relationshipType: 'CHILD',
          permissionLevel: 'FULL_ACCESS',
          status: 'ACTIVE',
        }];
      }
      setPatients(available);
      if (available.length > 0) {
        setSelectedPatient(available[0]);
        await loadPatientData(available[0]);
      } else {
        setLoading(false);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load Care Circle.');
      setLoading(false);
    }
  };

  useEffect(() => { initialize(); }, []);

  const selectPatient = async (patient: Patient) => {
    setSelectedPatient(patient);
    await loadPatientData(patient);
  };

  const handleCreatePatientSafe = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPatientName.trim()) return;
    try {
      const patient = await createPatient(newPatientName.trim(), newPatientRelationship);
      setNewPatientName('');
      await initialize();
      await selectPatient(patient);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create patient.');
    }
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPatient) return;
    try {
      await createInvitation(selectedPatient.id, inviteRelationship, invitePermission, inviteEmail);
      setInviteEmail('');
      setInvitations(await getPendingInvitations(selectedPatient.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create invitation.');
    }
  };

  const handleCreateRoutine = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPatient || !routineTitle.trim()) return;
    try {
      await createRoutine(selectedPatient.id, {
        title: routineTitle.trim(),
        period: routinePeriod,
        scheduledTime: routineTime || undefined,
        sortOrder: 0,
      });
      setRoutineTitle('');
      setRoutineTime('');
      setRoutine(await getDailyRoutine(selectedPatient.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create routine.');
    }
  };

  const handleCreateAppointment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPatient || !appointmentTitle.trim() || !appointmentDate || !appointmentTime) return;
    try {
      await createAppointment(selectedPatient.id, {
        title: appointmentTitle.trim(),
        appointmentDate,
        startTime: appointmentTime,
        status: 'SCHEDULED',
      });
      setAppointmentTitle('');
      setAppointmentDate('');
      setAppointmentTime('');
      setAppointments(await getAppointments(selectedPatient.id, { upcoming: true, size: 10 }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create appointment.');
    }
  };

  const toggleRoutine = async (activityId: string, completed: boolean) => {
    if (!selectedPatient) return;
    try {
      if (completed) await uncompleteRoutine(selectedPatient.id, activityId, routine?.date);
      else await completeRoutine(selectedPatient.id, activityId, routine?.date);
      setRoutine(await getDailyRoutine(selectedPatient.id, routine?.date));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update routine.');
    }
  };

  const handleRedeemInvitation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!invitationToken.trim()) return;
    try {
      await redeemInvitation(invitationToken.trim());
      setInvitationToken('');
      await initialize();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to redeem invitation.');
    }
  };

  const handleCompleteAppointment = async (appointmentId: string) => {
    if (!selectedPatient) return;
    try {
      await completeAppointment(selectedPatient.id, appointmentId);
      setAppointments(await getAppointments(selectedPatient.id, { upcoming: true, size: 10 }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to complete appointment.');
    }
  };

  const handleCancelAppointment = async (appointmentId: string) => {
    if (!selectedPatient || !window.confirm('Cancel this appointment?')) return;
    try {
      await cancelAppointment(selectedPatient.id, appointmentId);
      setAppointments(await getAppointments(selectedPatient.id, { upcoming: true, size: 10 }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to cancel appointment.');
    }
  };

  const markRead = async (id: string) => {
    try {
      await markNotificationRead(id);
      setNotifications(await getNotifications(0, 10));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update notification.');
    }
  };

  const markAllRead = async () => {
    try {
      await markAllNotificationsRead();
      setNotifications(await getNotifications(0, 10));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update notifications.');
    }
  };

  const routineItems = routine ? [...routine.morning, ...routine.afternoon, ...routine.evening] : [];

  return (
    <ProtectedRoute>
      <div className="family-dashboard-shell page-shell">
        <section className="family-header panel">
          <div>
            <span className="family-eyebrow">Care Circle</span>
            <h1>Family dashboard</h1>
            <p>Manage patients, routines, appointments, members, and notifications.</p>
          </div>
          <button className="secondary-button" type="button" onClick={initialize}>Refresh</button>
        </section>

        <section className="panel" style={{ marginBottom: '1rem' }}>
          <h2>Redeem invitation</h2>
          <form onSubmit={handleRedeemInvitation} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
            <input
              className="form-input"
              value={invitationToken}
              onChange={event => setInvitationToken(event.target.value)}
              placeholder="Invitation token"
            />
            <button className="primary-button" type="submit">Join Care Circle</button>
          </form>
        </section>

        {error && <div className="error-message" role="alert">{error}</div>}

        <section className="panel" style={{ marginBottom: '1rem' }}>
          <h2>Patients</h2>
          <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
            {patients.map(patient => (
              <button
                key={patient.id}
                className={selectedPatient?.id === patient.id ? 'primary-button' : 'secondary-button'}
                type="button"
                onClick={() => selectPatient(patient)}
              >
                {patient.name}
              </button>
            ))}
          </div>

          {patients.length === 0 && (
            <form onSubmit={handleCreatePatientSafe} style={{ marginTop: '1rem', display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
              <input className="form-input" value={newPatientName} onChange={e => setNewPatientName(e.target.value)} placeholder="Patient name" />
              <select className="form-input" value={newPatientRelationship} onChange={e => setNewPatientRelationship(e.target.value as RelationshipType)}>
                <option value="CHILD">Child</option>
                <option value="SPOUSE">Spouse</option>
                <option value="SIBLING">Sibling</option>
                <option value="CAREGIVER">Caregiver</option>
              </select>
              <button className="primary-button" type="submit">Create patient</button>
            </form>
          )}
        </section>

        {loading ? (
          <div className="panel"><p>Loading patient information...</p></div>
        ) : selectedPatient ? (
          <div className="family-content-grid">
            <section className="panel">
              <h2>Today's routine</h2>
              {routineItems.length === 0 && <p>No active routine activities.</p>}
              {routineItems.map(item => (
                <div className="detail-row" key={item.id}>
                  <div>
                    <strong>{item.title}</strong>
                    <div>{item.period.toLowerCase()}{item.scheduledTime ? ` · ${item.scheduledTime}` : ''}</div>
                  </div>
                  <button
                    className={item.completed ? 'secondary-button' : 'primary-button'}
                    type="button"
                    onClick={() => toggleRoutine(item.id, item.completed)}
                  >
                    {item.completed ? 'Uncomplete' : 'Complete'}
                  </button>
                </div>
              ))}
              {canManage && (
                <form onSubmit={handleCreateRoutine} style={{ marginTop: '1rem', display: 'grid', gap: '0.6rem' }}>
                  <input className="form-input" value={routineTitle} onChange={e => setRoutineTitle(e.target.value)} placeholder="Routine title" />
                  <select className="form-input" value={routinePeriod} onChange={e => setRoutinePeriod(e.target.value as typeof routinePeriod)}>
                    <option>MORNING</option><option>AFTERNOON</option><option>EVENING</option>
                  </select>
                  <input className="form-input" type="time" value={routineTime} onChange={e => setRoutineTime(e.target.value)} />
                  <button className="primary-button" type="submit">Add routine</button>
                </form>
              )}
            </section>

            <section className="panel">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.5rem' }}>
                <h2>{appointmentView === 'UPCOMING' ? 'Upcoming appointments' : 'Past appointments'}</h2>
                <button
                  className="secondary-button"
                  type="button"
                  onClick={async () => {
                    const nextView = appointmentView === 'UPCOMING' ? 'PAST' : 'UPCOMING';
                    setAppointmentView(nextView);
                    setAppointments(nextView === 'UPCOMING'
                      ? await getAppointments(selectedPatient.id, { upcoming: true, size: 10 })
                      : await getAppointments(selectedPatient.id, { past: true, size: 10 }));
                  }}
                >
                  Show {appointmentView === 'UPCOMING' ? 'past' : 'upcoming'}
                </button>
              </div>
              {!appointments?.content.length && <p>No {appointmentView.toLowerCase()} appointments.</p>}
              {appointments?.content.map(appointment => (
                <div className="detail-row" key={appointment.id}>
                  <div>
                    <strong>{appointment.title}</strong>
                    <div>{appointment.appointmentDate} · {appointment.startTime}</div>
                    <div>{appointment.status}</div>
                  </div>
                  {canManage && (
                    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                      {appointmentView === 'UPCOMING' && appointment.status === 'SCHEDULED' && (
                        <>
                          <button className="secondary-button" type="button" onClick={() => handleCompleteAppointment(appointment.id)}>Complete</button>
                          <button className="ghost-button" type="button" onClick={() => handleCancelAppointment(appointment.id)}>Cancel</button>
                        </>
                      )}
                    </div>
                  )}
                </div>
              ))}
              {canManage && (
                <form onSubmit={handleCreateAppointment} style={{ marginTop: '1rem', display: 'grid', gap: '0.6rem' }}>
                  <input className="form-input" value={appointmentTitle} onChange={e => setAppointmentTitle(e.target.value)} placeholder="Appointment title" />
                  <input className="form-input" type="date" value={appointmentDate} onChange={e => setAppointmentDate(e.target.value)} />
                  <input className="form-input" type="time" value={appointmentTime} onChange={e => setAppointmentTime(e.target.value)} />
                  <button className="primary-button" type="submit">Add appointment</button>
                </form>
              )}
            </section>

            <section className="panel">
              <h2>Care Circle</h2>
              {members.length === 0 && <p>No active members.</p>}
              {members.map(member => (
                <div className="detail-row" key={member.memberId}>
                  <div><strong>{member.name}</strong><div>{member.permissionLevel.replace('_', ' ')}</div></div>
                  {canManage && (
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <select
                        className="form-input"
                        value={member.permissionLevel}
                        onChange={e => setMembers(members.map(m =>
                          m.memberId === member.memberId
                            ? { ...m, permissionLevel: e.target.value as PermissionLevel }
                            : m
                        ))}
                      >
                        <option value="VIEW_ONLY">VIEW_ONLY</option>
                        <option value="CONTRIBUTE">CONTRIBUTE</option>
                        <option value="FULL_ACCESS">FULL_ACCESS</option>
                      </select>
                      <button className="secondary-button" type="button" onClick={async () => { await updateMember(selectedPatient.id, member.memberId, member.permissionLevel); await loadPatientData(selectedPatient); }}>Save</button>
                      <button className="ghost-button" type="button" onClick={async () => { if (window.confirm('Revoke this member?')) { await revokeMember(selectedPatient.id, member.memberId); await loadPatientData(selectedPatient); } }}>Revoke</button>
                    </div>
                  )}
                </div>
              ))}
              {canManage && (
                <form onSubmit={handleInvite} style={{ marginTop: '1rem', display: 'grid', gap: '0.6rem' }}>
                  <input className="form-input" type="email" value={inviteEmail} onChange={e => setInviteEmail(e.target.value)} placeholder="Family member email (optional)" />
                  <select className="form-input" value={inviteRelationship} onChange={e => setInviteRelationship(e.target.value as RelationshipType)}>
                    <option value="CHILD">Child</option><option value="SPOUSE">Spouse</option><option value="SIBLING">Sibling</option><option value="CAREGIVER">Caregiver</option>
                  </select>
                  <select className="form-input" value={invitePermission} onChange={e => setInvitePermission(e.target.value as PermissionLevel)}>
                    <option value="VIEW_ONLY">View only</option><option value="CONTRIBUTE">Contribute</option><option value="FULL_ACCESS">Full access</option>
                  </select>
                  <button className="primary-button" type="submit">Create invitation</button>
                </form>
              )}
              {invitations.map(invitation => (
                <div className="detail-row" key={invitation.id}>
                  <div><strong>Pending invitation</strong><div>{invitation.permissionLevel}</div>{invitation.token && <code>{invitation.token}</code>}</div>
                  {canManage && <button className="ghost-button" type="button" onClick={async () => { await revokeInvitation(selectedPatient.id, invitation.id); setInvitations(await getPendingInvitations(selectedPatient.id)); }}>Revoke</button>}
                </div>
              ))}
              <button className="ghost-button" type="button" onClick={async () => { if (window.confirm('Leave this Care Circle?')) { await leaveCareCircle(selectedPatient.id); await initialize(); } }}>Leave Care Circle</button>
            </section>

            <section className="panel">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2>Notifications</h2>
                <button className="secondary-button" type="button" onClick={markAllRead}>Mark all read</button>
              </div>
              {!notifications?.content.length && <p>No notifications.</p>}
              {notifications?.content.map(notification => (
                <button className="detail-row" type="button" key={notification.id} style={{ width: '100%', textAlign: 'left', border: 0, background: 'transparent' }} onClick={() => markRead(notification.id)}>
                  <div><strong>{notification.title}</strong><div>{notification.message}</div></div>
                  <span>{notification.readAt ? 'Read' : 'Unread'}</span>
                </button>
              ))}
            </section>
          </div>
        ) : null}
      </div>
    </ProtectedRoute>
  );
}
