import { apiFetch, apiFetchVoid } from './api';
import type { CareCircleMember, Invitation, Patient, PermissionLevel, RelationshipType } from '../types/phase8';

export async function getPatients(): Promise<Patient[]> {
  return apiFetch<Patient[]>('/api/family/patients');
}

export async function createPatient(name: string, relationshipType: RelationshipType): Promise<Patient> {
  return apiFetch<Patient>('/api/family/patients', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, relationshipType }),
  });
}

export async function getMembers(patientId: string): Promise<CareCircleMember[]> {
  return apiFetch<CareCircleMember[]>(`/api/family/patients/${patientId}/members`);
}

export async function getPendingInvitations(patientId: string): Promise<Invitation[]> {
  return apiFetch<Invitation[]>(`/api/family/patients/${patientId}/invitations`);
}

export async function createInvitation(
  patientId: string,
  relationshipType: RelationshipType,
  permissionLevel: PermissionLevel,
  intendedEmail?: string
): Promise<Invitation> {
  return apiFetch<Invitation>(`/api/family/patients/${patientId}/invitations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      relationshipType,
      permissionLevel,
      intendedEmail: intendedEmail?.trim() || undefined,
    }),
  });
}

export async function redeemInvitation(token: string) {
  return apiFetch<CareCircleMember>('/api/family/invitations/redeem', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  });
}

export async function revokeInvitation(patientId: string, invitationId: string): Promise<Invitation> {
  return apiFetch<Invitation>(`/api/family/patients/${patientId}/invitations/${invitationId}`, {
    method: 'DELETE',
  });
}

export async function updateMember(
  patientId: string,
  memberId: string,
  permissionLevel: PermissionLevel
): Promise<CareCircleMember> {
  return apiFetch<CareCircleMember>(`/api/family/patients/${patientId}/members/${memberId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ permissionLevel }),
  });
}

export async function revokeMember(patientId: string, memberId: string): Promise<void> {
  return apiFetchVoid(`/api/family/patients/${patientId}/members/${memberId}`, { method: 'DELETE' });
}

export async function leaveCareCircle(patientId: string): Promise<void> {
  return apiFetchVoid(`/api/family/patients/${patientId}/members/me`, { method: 'DELETE' });
}
