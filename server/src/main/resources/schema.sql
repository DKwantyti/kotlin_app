CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'TEACHER')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS students (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS teachers (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    department VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS channels (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('GROUP', 'DIRECT')),
    direct_key VARCHAR(80),
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS channels_direct_key_active
    ON channels (direct_key)
    WHERE direct_key IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS channel_members (
    channel_id UUID NOT NULL REFERENCES channels (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (channel_id, user_id)
);

CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES channels (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users (id),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS messages_channel_created
    ON messages (channel_id, created_at);

CREATE TABLE IF NOT EXISTS attachments (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    uploader_id UUID NOT NULL REFERENCES users (id),
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(80) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 1073741824),
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS student_items (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students (user_id) ON DELETE CASCADE,
    attachment_id UUID NOT NULL REFERENCES attachments (id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS student_items_student
    ON student_items (student_id);
