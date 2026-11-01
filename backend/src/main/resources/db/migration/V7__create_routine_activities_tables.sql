CREATE TABLE IF NOT EXISTS routine_activities (
    id UUID PRIMARY KEY,
    patient_user_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    period VARCHAR(20) NOT NULL,
    scheduled_time TIME,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_routine_patient FOREIGN KEY (patient_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_routine_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_routine_period CHECK (period IN ('MORNING', 'AFTERNOON', 'EVENING')),
    CONSTRAINT chk_routine_sort_order CHECK (sort_order >= 0),
    CONSTRAINT chk_routine_title_not_blank CHECK (BTRIM(title) <> '')
);

CREATE INDEX IF NOT EXISTS idx_routine_activities_patient_active_period_sort
    ON routine_activities(patient_user_id, is_active, period, sort_order);
CREATE INDEX IF NOT EXISTS idx_routine_activities_created_by
    ON routine_activities(created_by_user_id);

CREATE TABLE IF NOT EXISTS routine_activity_completions (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL,
    completion_date DATE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_by_user_id UUID NOT NULL,
    CONSTRAINT fk_routine_completion_activity
        FOREIGN KEY (activity_id) REFERENCES routine_activities(id) ON DELETE CASCADE,
    CONSTRAINT fk_routine_completion_user
        FOREIGN KEY (completed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_routine_completion_activity_date UNIQUE (activity_id, completion_date)
);

CREATE INDEX IF NOT EXISTS idx_routine_completion_activity_date
    ON routine_activity_completions(activity_id, completion_date DESC);
CREATE INDEX IF NOT EXISTS idx_routine_completion_user
    ON routine_activity_completions(completed_by_user_id);
