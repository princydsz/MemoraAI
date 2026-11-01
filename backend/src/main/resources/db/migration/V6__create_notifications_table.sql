CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    patient_user_id UUID,
    actor_user_id UUID,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    related_entity_id UUID,
    related_entity_type VARCHAR(100),
    dedupe_key VARCHAR(255),
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_patient
        FOREIGN KEY (patient_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_notification_type CHECK (
        type IN ('MEMBER_JOINED', 'INVITATION_REDEEMED', 'MEMBER_REMOVED')
    )
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications(recipient_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read
    ON notifications(recipient_user_id, read_at);
CREATE UNIQUE INDEX IF NOT EXISTS uk_notifications_dedupe_key
    ON notifications(dedupe_key)
    WHERE dedupe_key IS NOT NULL;
