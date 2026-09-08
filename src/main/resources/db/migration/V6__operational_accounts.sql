CREATE TABLE identity_mutation_guard (
    id SMALLINT PRIMARY KEY
);

INSERT INTO identity_mutation_guard (id) VALUES (1);

CREATE TABLE operational_accounts (
    id UUID PRIMARY KEY,
    normalized_email VARCHAR(254) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    invited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invited_by UUID,
    activated_at TIMESTAMP WITH TIME ZONE,
    deactivated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT operational_accounts_normalized_email_unique UNIQUE (normalized_email)
);

CREATE INDEX operational_accounts_status_role_idx ON operational_accounts (status, role);

CREATE TABLE operational_account_invitations (
    id UUID PRIMARY KEY,
    operational_account_id UUID NOT NULL REFERENCES operational_accounts (id),
    token_digest VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    invited_by UUID NOT NULL,
    reason_category VARCHAR(32) NOT NULL,
    public_reason VARCHAR(500) NOT NULL,
    internal_note VARCHAR(2000),
    CONSTRAINT operational_account_invitations_token_digest_unique UNIQUE (token_digest)
);

CREATE INDEX operational_account_invitations_account_id_idx
    ON operational_account_invitations (operational_account_id);
