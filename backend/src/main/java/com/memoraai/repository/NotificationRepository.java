package com.memoraai.repository;

import com.memoraai.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query("""
            select n from Notification n
            where n.recipientUserId = :recipientId
              and (n.patientUserId is null
                   or n.patientUserId = :recipientId
                   or exists (
                        select cm.id from CareCircleMembership cm
                        where cm.patientUserId = n.patientUserId
                          and cm.familyMemberUserId = :recipientId
                          and cm.status = com.memoraai.entity.MembershipStatus.ACTIVE
                   ))
            order by n.createdAt desc
            """)
    Page<Notification> findVisibleByRecipient(@Param("recipientId") UUID recipientId,
                                              Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.recipientUserId = :recipientId
              and n.readAt is null
              and (n.patientUserId is null
                   or n.patientUserId = :recipientId
                   or exists (
                        select cm.id from CareCircleMembership cm
                        where cm.patientUserId = n.patientUserId
                          and cm.familyMemberUserId = :recipientId
                          and cm.status = com.memoraai.entity.MembershipStatus.ACTIVE
                   ))
            """)
    long countVisibleUnread(@Param("recipientId") UUID recipientId);

    @Query("""
            select n from Notification n
            where n.id = :notificationId
              and n.recipientUserId = :recipientId
              and (n.patientUserId is null
                   or n.patientUserId = :recipientId
                   or exists (
                        select cm.id from CareCircleMembership cm
                        where cm.patientUserId = n.patientUserId
                          and cm.familyMemberUserId = :recipientId
                          and cm.status = com.memoraai.entity.MembershipStatus.ACTIVE
                   ))
            """)
    Optional<Notification> findVisibleByRecipientAndId(@Param("recipientId") UUID recipientId,
                                                       @Param("notificationId") UUID notificationId);

    @Modifying
    @Query(value = """
            update notifications n
            set read_at = :readAt
            where n.id = :notificationId
              and n.recipient_user_id = :recipientId
              and n.read_at is null
              and (
                    n.patient_user_id is null
                    or n.patient_user_id = :recipientId
                    or exists (
                        select 1 from care_circle_memberships cm
                        where cm.patient_user_id = n.patient_user_id
                          and cm.family_member_user_id = :recipientId
                          and cm.status = 'ACTIVE'
                    )
              )
            """, nativeQuery = true)
    int markOneVisibleRead(@Param("recipientId") UUID recipientId,
                           @Param("notificationId") UUID notificationId,
                           @Param("readAt") Instant readAt);

    @Modifying
    @Query(value = """
            update notifications n
            set read_at = :readAt
            where n.recipient_user_id = :recipientId
              and n.read_at is null
              and (
                    n.patient_user_id is null
                    or n.patient_user_id = :recipientId
                    or exists (
                        select 1 from care_circle_memberships cm
                        where cm.patient_user_id = n.patient_user_id
                          and cm.family_member_user_id = :recipientId
                          and cm.status = 'ACTIVE'
                    )
              )
            """, nativeQuery = true)
    int markAllVisibleRead(@Param("recipientId") UUID recipientId,
                           @Param("readAt") Instant readAt);
}
