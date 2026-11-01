package com.memoraai.repository;

import com.memoraai.entity.ReminderDelivery;
import com.memoraai.entity.ReminderDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, UUID> {
    Optional<ReminderDelivery> findBySourceTypeAndSourceIdAndRecipientUserIdAndReminderKey(
            String sourceType, UUID sourceId, UUID recipientUserId, String reminderKey);

    List<ReminderDelivery> findByStatusInAndAttemptCountLessThan(
            List<ReminderDeliveryStatus> statuses, int maximumAttempts);
}
