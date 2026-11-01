CREATE TABLE IF NOT EXISTS care_circle_memberships (
    id UUID PRIMARY KEY,
    patient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_member_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    relationship_type VARCHAR(20) NOT NULL,
    permission_level VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_care_circle_patient_family UNIQUE (patient_user_id, family_member_user_id),
    CONSTRAINT chk_care_circle_not_self CHECK (patient_user_id <> family_member_user_id),
    CONSTRAINT chk_care_circle_relationship_type CHECK (
        relationship_type IN ('SPOUSE', 'CHILD', 'SIBLING', 'CAREGIVER')
    ),
    CONSTRAINT chk_care_circle_permission_level CHECK (
        permission_level IN ('VIEW_ONLY', 'CONTRIBUTE', 'FULL_ACCESS')
    ),
    CONSTRAINT chk_care_circle_status CHECK (
        status IN ('ACTIVE', 'REVOKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_care_circle_patient_status
    ON care_circle_memberships(patient_user_id, status);
CREATE INDEX IF NOT EXISTS idx_care_circle_family_status
    ON care_circle_memberships(family_member_user_id, status);
