/**
 * Types for MemoraAI Family & Caregiver Dashboard (Person 3 Task 2)
 * Designed for future integration with Person 2's backend care circle APIs.
 */

export interface WellnessOverview {
  mood: string;
  moodEmoji: string;
  moodNote: string;
  hydrationGlasses: number;
  hydrationGoal: number;
  routinesCompleted: number;
  routinesTotal: number;
}

export interface PatientProfile {
  id: string;
  name: string;
  preferredName: string;
  age: number;
  conditionSummary: string;
  avatarUrl?: string;
  relationshipToUser: string;
  primaryCaregiverName: string;
  lastActive: string;
  wellness: WellnessOverview;
}

export type RoutinePeriod = 'morning' | 'afternoon' | 'evening';

export interface DailyRoutine {
  id: string;
  title: string;
  period: RoutinePeriod;
  timeSlot: string;
  description: string;
  completed: boolean;
  completedAt?: string;
  completedBy?: string;
  caregiverTip?: string;
  categoryTag: string;
}

export interface PatientMemory {
  id: string;
  title: string;
  date: string;
  category: string;
  content: string;
  imageUrl?: string;
  tags: string[];
  audioDuration?: string;
}

export type AppointmentStatus = 'UPCOMING' | 'COMPLETED' | 'RESCHEDULED';

export interface Appointment {
  id: string;
  title: string;
  doctorName: string;
  specialty: string;
  clinicName: string;
  address: string;
  date: string;
  time: string;
  status: AppointmentStatus;
  notes: string;
  prepInstructions?: string;
}

export interface FamilyContribution {
  id: string;
  title: string;
  description: string;
  category: string;
  date: string;
  contributorId: string;
  contributorName: string;
  contributorRelationship: string;
  hasPhoto: boolean;
  photoUrl?: string;
  hasAudio: boolean;
  audioDuration?: string;
  likesCount: number;
  timestamp: string;
}

export type PermissionLevel = 'View Only' | 'Contribute' | 'Full Access';

export interface CareCircleMember {
  id: string;
  name: string;
  relationship: string;
  role: string;
  avatarLetter: string;
  permission: PermissionLevel;
  isPrimary: boolean;
  phone?: string;
  email?: string;
  lastVisit?: string;
}

export type NotificationType = 'memory' | 'routine' | 'reminder' | 'wellness';

export interface FamilyNotification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  timestamp: string;
  isRead: boolean;
  relatedEntityId?: string;
}

export interface NewContributionFormData {
  title: string;
  category: string;
  date: string;
  description: string;
  photoFile: File | null;
  photoPreviewUrl: string | null;
  audioFile: File | null;
  audioPreviewUrl: string | null;
  contributorAttribution: string;
}

