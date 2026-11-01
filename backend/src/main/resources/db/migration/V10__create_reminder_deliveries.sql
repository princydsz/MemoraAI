ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(100);

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notification_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notification_type CHECK (
    type IN (
        'MEMBER_JOINED',
        'INVITATION_REDEEMED',
        'MEMBER_REMOVED',
        'ROUTINE_REMINDER',
        'APPOINTMENT_REMINDER'
    )
);

CREATE TABLE IF NOT EXISTS reminder_deliveries (
    id UUID PRIMARY KEY,
    source_type VARCHAR(30) NOT NULL,
    source_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    patient_user_id UUID NOT NULL,
    reminder_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_for TIMESTAMP WITH TIME ZONE NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    notification_id UUID,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_reminder_delivery UNIQUE (source_type, source_id, recipient_user_id, reminder_key),
    CONSTRAINT fk_reminder_recipient FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reminder_patient FOREIGN KEY (patient_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reminder_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE SET NULL,
    CONSTRAINT chk_reminder_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    CONSTRAINT chk_reminder_notification_type CHECK (
        notification_type IN ('ROUTINE_REMINDER', 'APPOINTMENT_REMINDER')
    ),
    CONSTRAINT chk_reminder_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_reminder_deliveries_status_scheduled
    ON reminder_deliveries(status, scheduled_for ASC);
CREATE INDEX IF NOT EXISTS idx_reminder_deliveries_source
    ON reminder_deliveries(source_type, source_id);
