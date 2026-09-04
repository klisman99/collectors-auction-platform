CREATE TABLE regular_accounts (
    id UUID PRIMARY KEY,
    normalized_email VARCHAR(254) NOT NULL,
    public_handle VARCHAR(30) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT regular_accounts_normalized_email_unique UNIQUE (normalized_email),
    CONSTRAINT regular_accounts_public_handle_unique UNIQUE (public_handle)
);

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES regular_accounts (id),
    token_digest VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT email_verification_tokens_digest_unique UNIQUE (token_digest)
);

CREATE INDEX email_verification_tokens_account_id_idx ON email_verification_tokens (account_id);

CREATE TABLE audit_records (
    id UUID PRIMARY KEY,
    actor_type VARCHAR(32) NOT NULL,
    actor_id UUID,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata VARCHAR(2000) NOT NULL
);

CREATE INDEX audit_records_target_idx ON audit_records (target_type, target_id);

CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

CREATE TABLE event_publication (
    id UUID NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20),
    completion_attempts INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
