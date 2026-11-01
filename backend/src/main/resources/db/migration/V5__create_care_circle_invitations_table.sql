CREATE TABLE IF NOT EXISTS care_circle_invitations (
    id UUID PRIMARY KEY,
    patient_user_id UUID NOT NULL,
    inviter_user_id UUID,
    intended_email VARCHAR(255),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    permission_level VARCHAR(20) NOT NULL,
    relationship_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    redeemed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    redeemed_by_user_id UUID,
    CONSTRAINT fk_care_circle_invitation_patient
        FOREIGN KEY (patient_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_care_circle_invitation_inviter
        FOREIGN KEY (inviter_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_care_circle_invitation_redeemer
        FOREIGN KEY (redeemed_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_care_circle_invitation_permission
        CHECK (permission_level IN ('VIEW_ONLY', 'CONTRIBUTE', 'FULL_ACCESS')),
    CONSTRAINT chk_care_circle_invitation_relationship
        CHECK (relationship_type IN ('SPOUSE', 'CHILD', 'SIBLING', 'CAREGIVER')),
    CONSTRAINT chk_care_circle_invitation_status
        CHECK (status IN ('PENDING', 'REDEEMED', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_care_circle_invitation_patient
    ON care_circle_invitations(patient_user_id, status);
CREATE INDEX IF NOT EXISTS idx_care_circle_invitation_token
    ON care_circle_invitations(token_hash);
