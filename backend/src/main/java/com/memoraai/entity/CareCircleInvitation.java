package com.memoraai.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "care_circle_invitations", uniqueConstraints =
@UniqueConstraint(name = "care_circle_invitations_token_hash_key", columnNames = "token_hash"))
public class CareCircleInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_user_id", nullable = false)
    private UUID patientUserId;

    @Column(name = "inviter_user_id")
    private UUID inviterUserId;

    @Column(name = "intended_email")
    private String intendedEmail;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", nullable = false, length = 20)
    private PermissionLevel permissionLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 20)
    private RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "redeemed_by_user_id")
    private UUID redeemedByUserId;

    protected CareCircleInvitation() {
    }

    public CareCircleInvitation(UUID patientUserId, UUID inviterUserId, String intendedEmail,
                                String tokenHash, PermissionLevel permissionLevel,
                                RelationshipType relationshipType, Instant expiresAt) {
        this.patientUserId = patientUserId;
        this.inviterUserId = inviterUserId;
        this.intendedEmail = intendedEmail;
        this.tokenHash = tokenHash;
        this.permissionLevel = permissionLevel;
        this.relationshipType = relationshipType;
        this.status = InvitationStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (expiresAt == null) {
            expiresAt = now.plusSeconds(7L * 24 * 60 * 60);
        }
        if (status == null) {
            status = InvitationStatus.PENDING;
        }
    }

    public UUID getId() { return id; }
    public UUID getPatientUserId() { return patientUserId; }
    public UUID getInviterUserId() { return inviterUserId; }
    public String getIntendedEmail() { return intendedEmail; }
    public String getTokenHash() { return tokenHash; }
    public PermissionLevel getPermissionLevel() { return permissionLevel; }
    public RelationshipType getRelationshipType() { return relationshipType; }
    public InvitationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRedeemedAt() { return redeemedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public UUID getRedeemedByUserId() { return redeemedByUserId; }

    public void redeem(UUID redeemerId, Instant redeemedAt) {
        this.status = InvitationStatus.REDEEMED;
        this.redeemedByUserId = redeemerId;
        this.redeemedAt = redeemedAt;
    }

    public void revoke(Instant revokedAt) {
        this.status = InvitationStatus.REVOKED;
        this.revokedAt = revokedAt;
    }

    public InvitationStatus effectiveStatus(Instant now) {
        if (status == InvitationStatus.PENDING && expiresAt != null && !expiresAt.isAfter(now)) {
            return InvitationStatus.EXPIRED;
        }
        return status;
    }
}
