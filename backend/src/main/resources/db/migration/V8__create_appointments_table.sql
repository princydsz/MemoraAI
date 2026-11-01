CREATE TABLE IF NOT EXISTS appointments (
    id UUID PRIMARY KEY,
    patient_user_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME,
    location VARCHAR(255),
    provider_name VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_appointment_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_appointment_title_not_blank CHECK (BTRIM(title) <> ''),
    CONSTRAINT chk_appointment_time_order CHECK (end_time IS NULL OR end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_appointments_patient_date
    ON appointments(patient_user_id, appointment_date DESC);
CREATE INDEX IF NOT EXISTS idx_appointments_patient_status_date
    ON appointments(patient_user_id, status, appointment_date DESC);
CREATE INDEX IF NOT EXISTS idx_appointments_creator
    ON appointments(created_by_user_id);
