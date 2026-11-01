package com.memoraai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "care_circle_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_care_circle_patient_family",
                columnNames = {"patient_user_id", "family_member_user_id"}
        )
)
public class CareCircleMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "patient_user_id", nullable = false)
    private UUID patientUserId;

    @Column(name = "family_member_user_id", nullable = false)
    private UUID familyMemberUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 20)
    private RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", nullable = false, length = 20)
    private PermissionLevel permissionLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CareCircleMembership() {
    }

    public CareCircleMembership(
            UUID patientUserId,
            UUID familyMemberUserId,
            RelationshipType relationshipType,
            PermissionLevel permissionLevel,
            MembershipStatus status) {
        if (patientUserId != null && patientUserId.equals(familyMemberUserId)) {
            throw new IllegalArgumentException("A user cannot be their own family member");
        }
        this.patientUserId = patientUserId;
        this.familyMemberUserId = familyMemberUserId;
        this.relationshipType = relationshipType;
        this.permissionLevel = permissionLevel;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientUserId() {
        return patientUserId;
    }

    public void setPatientUserId(UUID patientUserId) {
        this.patientUserId = patientUserId;
    }

    public UUID getFamilyMemberUserId() {
        return familyMemberUserId;
    }

    public void setFamilyMemberUserId(UUID familyMemberUserId) {
        this.familyMemberUserId = familyMemberUserId;
    }

    public RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(RelationshipType relationshipType) {
        this.relationshipType = relationshipType;
    }

    public PermissionLevel getPermissionLevel() {
        return permissionLevel;
    }

    public void setPermissionLevel(PermissionLevel permissionLevel) {
        this.permissionLevel = permissionLevel;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
