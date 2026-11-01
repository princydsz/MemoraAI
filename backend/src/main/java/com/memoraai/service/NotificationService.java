package com.memoraai.service;

import com.memoraai.dto.NotificationPageResponse;
import com.memoraai.dto.NotificationResponse;
import com.memoraai.dto.UnreadCountResponse;
import com.memoraai.entity.Notification;
import com.memoraai.entity.NotificationType;
import com.memoraai.entity.User;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.repository.NotificationRepository;
import com.memoraai.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public Notification createNotification(UUID recipientUserId, UUID patientUserId,
                                           UUID actorUserId, NotificationType type,
                                           String title, String message,
                                           UUID relatedEntityId, String relatedEntityType,
                                           String dedupeKey) {
        if (dedupeKey != null) {
            Notification existing = notificationRepository.findByDedupeKey(dedupeKey).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Notification notification = new Notification(
                recipientUserId, patientUserId, actorUserId, type, title, message,
                relatedEntityId, relatedEntityType, dedupeKey);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(String recipientEmail, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number must not be negative");
        }
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("Page size must be between 1 and 50");
        }
        User recipient = getAuthenticatedUser(recipientEmail);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = notificationRepository.findVisibleByRecipient(
                recipient.getId(), pageable);
        return new NotificationPageResponse(
                result.getContent().stream().map(NotificationResponse::fromEntity).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String recipientEmail) {
        User recipient = getAuthenticatedUser(recipientEmail);
        return new UnreadCountResponse(notificationRepository.countVisibleUnread(recipient.getId()));
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, String recipientEmail) {
        User recipient = getAuthenticatedUser(recipientEmail);
        int updated = notificationRepository.markOneVisibleRead(
                recipient.getId(), notificationId, Instant.now());
        if (updated == 0) {
            notificationRepository.findVisibleByRecipientAndId(recipient.getId(), notificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        }
        Notification notification = notificationRepository.findVisibleByRecipientAndId(
                recipient.getId(), notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        return NotificationResponse.fromEntity(notification);
    }

    @Transactional
    public void markAllRead(String recipientEmail) {
        User recipient = getAuthenticatedUser(recipientEmail);
        notificationRepository.markAllVisibleRead(recipient.getId(), Instant.now());
    }

    @Transactional
    public void delete(UUID notificationId, String recipientEmail) {
        User recipient = getAuthenticatedUser(recipientEmail);
        Notification notification = notificationRepository.findVisibleByRecipientAndId(
                recipient.getId(), notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notificationRepository.delete(notification);
    }
}
