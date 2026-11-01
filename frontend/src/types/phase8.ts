export type PermissionLevel = 'VIEW_ONLY' | 'CONTRIBUTE' | 'FULL_ACCESS';
export type RelationshipType = 'SPOUSE' | 'CHILD' | 'SIBLING' | 'CAREGIVER';
export type RoutinePeriod = 'MORNING' | 'AFTERNOON' | 'EVENING';
export type AppointmentStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';

export interface Patient {
  id: string;
  name: string;
  relationshipType: RelationshipType;
  permissionLevel: PermissionLevel;
  status: 'ACTIVE' | 'REVOKED';
}

export interface CareCircleMember {
  memberId: string;
  name: string;
  relationshipType: RelationshipType;
  permissionLevel: PermissionLevel;
  status: 'ACTIVE' | 'REVOKED';
  createdAt: string;
  updatedAt: string;
}

export interface Invitation {
  id: string;
  patientId: string;
  inviterId: string;
  intendedEmail?: string;
  permissionLevel: PermissionLevel;
  relationshipType: RelationshipType;
  status: 'PENDING' | 'REDEEMED' | 'REVOKED' | 'EXPIRED';
  createdAt: string;
  expiresAt: string;
  token?: string;
}

export interface DailyRoutineItem {
  id: string;
  title: string;
  description?: string;
  period: RoutinePeriod;
  scheduledTime?: string;
  sortOrder: number;
  completed: boolean;
  completedAt?: string;
  completedBy?: { id: string; name: string; email: string };
}

export interface DailyRoutine {
  date: string;
  morning: DailyRoutineItem[];
  afternoon: DailyRoutineItem[];
  evening: DailyRoutineItem[];
}

export interface RoutineActivity extends DailyRoutineItem {
  active: boolean;
  createdByUserId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Appointment {
  id: string;
  patientId: string;
  title: string;
  description?: string;
  appointmentDate: string;
  startTime: string;
  endTime?: string;
  location?: string;
  providerName?: string;
  status: AppointmentStatus;
  createdBy?: { id: string; name: string; email: string };
  createdByUserId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  message: string;
  patientId?: string;
  actorId?: string;
  relatedEntityId?: string;
  relatedEntityType?: string;
  readAt?: string;
  createdAt: string;
}
