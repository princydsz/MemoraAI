ALTER TABLE memories
    ADD COLUMN IF NOT EXISTS contributed_by_user_id UUID;

ALTER TABLE memories
    ADD CONSTRAINT fk_memories_contributed_by
        FOREIGN KEY (contributed_by_user_id)
        REFERENCES users(id)
        ON DELETE SET NULL;

ALTER TABLE memories
    ADD CONSTRAINT chk_memory_contributor_not_owner
        CHECK (contributed_by_user_id IS NULL OR contributed_by_user_id <> user_id);

CREATE INDEX IF NOT EXISTS idx_memories_contributed_by_user_id
    ON memories(contributed_by_user_id);
