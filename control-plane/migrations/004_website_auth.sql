CREATE TABLE IF NOT EXISTS platform_users (
  id UUID PRIMARY KEY,
  display_name TEXT NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 80),
  avatar_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_login_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS oauth_identities (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
  provider TEXT NOT NULL CHECK (provider IN ('discord')),
  provider_account_id TEXT NOT NULL,
  username TEXT NOT NULL,
  display_name TEXT,
  avatar_hash TEXT,
  profile JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_login_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (provider, provider_account_id),
  UNIQUE (user_id, provider)
);
CREATE INDEX IF NOT EXISTS oauth_identities_user_id_idx ON oauth_identities (user_id);

CREATE TABLE IF NOT EXISTS auth_oauth_states (
  id UUID PRIMARY KEY,
  provider TEXT NOT NULL CHECK (provider IN ('discord')),
  state_hash CHAR(64) NOT NULL UNIQUE,
  redirect_uri TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS auth_oauth_states_expires_at_idx ON auth_oauth_states (expires_at);
CREATE INDEX IF NOT EXISTS auth_oauth_states_unconsumed_idx ON auth_oauth_states (consumed_at, expires_at);

CREATE TABLE IF NOT EXISTS auth_login_tickets (
  id UUID PRIMARY KEY,
  ticket_hash CHAR(64) NOT NULL UNIQUE,
  user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS auth_login_tickets_user_id_idx ON auth_login_tickets (user_id);
CREATE INDEX IF NOT EXISTS auth_login_tickets_expires_at_idx ON auth_login_tickets (expires_at);

CREATE TABLE IF NOT EXISTS auth_sessions (
  id UUID PRIMARY KEY,
  session_hash CHAR(64) NOT NULL UNIQUE,
  user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS auth_sessions_user_id_idx ON auth_sessions (user_id);
CREATE INDEX IF NOT EXISTS auth_sessions_expires_at_idx ON auth_sessions (expires_at);
CREATE INDEX IF NOT EXISTS auth_sessions_active_lookup_idx ON auth_sessions (session_hash, revoked_at, expires_at);

CREATE OR REPLACE FUNCTION cleanup_auth_artifacts()
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  DELETE FROM auth_oauth_states
    WHERE expires_at < NOW() - INTERVAL '1 hour'
       OR consumed_at < NOW() - INTERVAL '1 hour';
  DELETE FROM auth_login_tickets
    WHERE expires_at < NOW() - INTERVAL '1 hour'
       OR consumed_at < NOW() - INTERVAL '1 hour';
  DELETE FROM auth_sessions
    WHERE expires_at < NOW() - INTERVAL '7 days'
       OR revoked_at < NOW() - INTERVAL '7 days';
END;
$$;
