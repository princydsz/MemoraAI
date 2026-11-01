import { apiFetch } from './api';
import type { Appointment, AppointmentStatus, Page } from '../types/phase8';

export interface CreateAppointmentInput {
  title: string;
  description?: string;
  appointmentDate: string;
  startTime: string;
  endTime?: string;
  location?: string;
  providerName?: string;
  status?: AppointmentStatus;
}

export async function getAppointments(
  patientId: string,
  options: { upcoming?: boolean; past?: boolean; status?: AppointmentStatus; from?: string; to?: string; page?: number; size?: number } = {}
): Promise<Page<Appointment>> {
  const params = new URLSearchParams();
  if (options.upcoming) params.set('upcoming', 'true');
  if (options.past) params.set('past', 'true');
  if (options.status) params.set('status', options.status);
  if (options.from) params.set('from', options.from);
  if (options.to) params.set('to', options.to);
  params.set('page', String(options.page ?? 0));
  params.set('size', String(options.size ?? 20));
  const suffix = params.toString();
  return apiFetch<Page<Appointment>>(`/api/family/patients/${patientId}/appointments${suffix ? `?${suffix}` : ''}`);
}

export async function createAppointment(patientId: string, data: CreateAppointmentInput): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/family/patients/${patientId}/appointments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
}

export async function updateAppointment(
  patientId: string,
  appointmentId: string,
  data: Partial<CreateAppointmentInput>
): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/family/patients/${patientId}/appointments/${appointmentId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
}

export async function completeAppointment(patientId: string, appointmentId: string): Promise<Appointment> {
  return updateAppointment(patientId, appointmentId, { status: 'COMPLETED' });
}

export async function cancelAppointment(patientId: string, appointmentId: string): Promise<Appointment> {
  return apiFetch<Appointment>(`/api/family/patients/${patientId}/appointments/${appointmentId}/cancel`, {
    method: 'POST',
  });
}
