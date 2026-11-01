import { apiFetch, apiFetchVoid } from './api';
import type { NotificationItem, Page } from '../types/phase8';

export async function getNotifications(page = 0, size = 20): Promise<Page<NotificationItem>> {
  return apiFetch<Page<NotificationItem>>(`/api/notifications?page=${page}&size=${size}`);
}

export async function getUnreadCount(): Promise<{ unreadCount: number }> {
  return apiFetch<{ unreadCount: number }>('/api/notifications/unread-count');
}

export async function markNotificationRead(notificationId: string): Promise<NotificationItem> {
  return apiFetch<NotificationItem>(`/api/notifications/${notificationId}/read`, { method: 'PATCH' });
}

export async function markAllNotificationsRead(): Promise<void> {
  return apiFetchVoid('/api/notifications/read-all', { method: 'PATCH' });
}
