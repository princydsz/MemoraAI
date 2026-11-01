import { apiFetch, apiFetchVoid } from './api';
import type { DailyRoutine, DailyRoutineItem, RoutineActivity, RoutinePeriod } from '../types/phase8';

export interface CreateRoutineInput {
  title: string;
  description?: string;
  period: RoutinePeriod;
  scheduledTime?: string;
  sortOrder?: number;
}

export async function getDailyRoutine(patientId: string, date?: string): Promise<DailyRoutine> {
  const query = date ? `?date=${encodeURIComponent(date)}` : '';
  return apiFetch<DailyRoutine>(`/api/family/patients/${patientId}/routines${query}`);
}

export async function createRoutine(patientId: string, data: CreateRoutineInput): Promise<RoutineActivity> {
  return apiFetch<RoutineActivity>(`/api/family/patients/${patientId}/routines/activities`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
}

export async function updateRoutine(
  patientId: string,
  activityId: string,
  data: Partial<CreateRoutineInput> & { active?: boolean }
): Promise<RoutineActivity> {
  return apiFetch<RoutineActivity>(`/api/family/patients/${patientId}/routines/activities/${activityId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
}

export async function archiveRoutine(patientId: string, activityId: string): Promise<RoutineActivity> {
  return apiFetch<RoutineActivity>(`/api/family/patients/${patientId}/routines/activities/${activityId}`, {
    method: 'DELETE',
  });
}

export async function reactivateRoutine(patientId: string, activityId: string): Promise<RoutineActivity> {
  return apiFetch<RoutineActivity>(`/api/family/patients/${patientId}/routines/activities/${activityId}/reactivate`, {
    method: 'POST',
  });
}

export async function completeRoutine(patientId: string, activityId: string, date?: string): Promise<DailyRoutineItem> {
  const query = date ? `?date=${encodeURIComponent(date)}` : '';
  return apiFetch<DailyRoutineItem>(`/api/family/patients/${patientId}/routines/activities/${activityId}/complete${query}`, {
    method: 'POST',
  });
}

export async function uncompleteRoutine(patientId: string, activityId: string, date?: string): Promise<void> {
  const query = date ? `?date=${encodeURIComponent(date)}` : '';
  return apiFetchVoid(`/api/family/patients/${patientId}/routines/activities/${activityId}/complete${query}`, {
    method: 'DELETE',
  });
}
