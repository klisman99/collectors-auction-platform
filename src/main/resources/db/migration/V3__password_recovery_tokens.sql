CREATE TABLE password_recovery_tokens (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES regular_accounts (id),
    token_digest VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT password_recovery_tokens_digest_unique UNIQUE (token_digest)
);

CREATE INDEX password_recovery_tokens_account_id_idx ON password_recovery_tokens (account_id);
