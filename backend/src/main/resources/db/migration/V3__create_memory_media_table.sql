CREATE TABLE IF NOT EXISTS memory_media (
    id UUID PRIMARY KEY,
    memory_id UUID NOT NULL REFERENCES memories(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CONSTRAINT chk_file_size_positive CHECK (file_size > 0),
    s3_key VARCHAR(512) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memory_media_memory_id ON memory_media(memory_id);
CREATE INDEX IF NOT EXISTS idx_memory_media_user_id ON memory_media(user_id);
CREATE INDEX IF NOT EXISTS idx_memory_media_memory_id_user_id ON memory_media(memory_id, user_id);
CREATE INDEX IF NOT EXISTS idx_memory_media_created_at ON memory_media(created_at);
