-- Discord ecosystem schema. This file is applied only by scripts/migrate.ts.
-- Statements are idempotent so a partially provisioned non-production database can be reconciled safely.

CREATE TABLE IF NOT EXISTS guild_configs (
  guild_id VARCHAR(32) PRIMARY KEY,
  name VARCHAR(100),
  timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
  locale VARCHAR(16) NOT NULL DEFAULT 'en-US',
  moderation_log_channel_id VARCHAR(32),
  ticket_category_id VARCHAR(32),
  ticket_log_channel_id VARCHAR(32),
  suggestions_channel_id VARCHAR(32),
  events_channel_id VARCHAR(32),
  stream_announcements_id VARCHAR(32),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS guild_resources (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  key VARCHAR(100) NOT NULL,
  title VARCHAR(200) NOT NULL,
  url TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT guild_resources_guild_id_key_key UNIQUE (guild_id, key)
);
CREATE INDEX IF NOT EXISTS guild_resources_guild_id_sort_order_idx ON guild_resources (guild_id, sort_order);

CREATE TABLE IF NOT EXISTS memberships (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  discord_user_id VARCHAR(32) NOT NULL,
  membership_tier_id VARCHAR(64),
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE','PAUSED','EXPIRED','CANCELLED')),
  started_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  external_ref VARCHAR(255),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT memberships_guild_id_discord_user_id_key UNIQUE (guild_id, discord_user_id)
);
CREATE INDEX IF NOT EXISTS memberships_guild_id_status_idx ON memberships (guild_id, status);
CREATE INDEX IF NOT EXISTS memberships_membership_tier_id_idx ON memberships (membership_tier_id);

CREATE TABLE IF NOT EXISTS tickets (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  channel_id VARCHAR(32) NOT NULL,
  opener_discord_id VARCHAR(32) NOT NULL,
  assignee_discord_id VARCHAR(32),
  subject VARCHAR(200) NOT NULL,
  status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','WAITING_ON_MEMBER','RESOLVED','CLOSED')),
  priority TEXT NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
  closed_at TIMESTAMPTZ,
  closed_by_discord_id VARCHAR(32),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT tickets_guild_id_channel_id_key UNIQUE (guild_id, channel_id)
);
CREATE INDEX IF NOT EXISTS tickets_guild_id_status_created_at_idx ON tickets (guild_id, status, created_at);
CREATE INDEX IF NOT EXISTS tickets_opener_discord_id_idx ON tickets (opener_discord_id);

CREATE TABLE IF NOT EXISTS reports (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  reporter_discord_id VARCHAR(32) NOT NULL,
  target_discord_id VARCHAR(32),
  target_message_id VARCHAR(32),
  target_channel_id VARCHAR(32),
  reason TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','UNDER_REVIEW','ACTIONED','DISMISSED','CLOSED')),
  reviewer_discord_id VARCHAR(32),
  resolution TEXT,
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS reports_guild_id_status_created_at_idx ON reports (guild_id, status, created_at);
CREATE INDEX IF NOT EXISTS reports_target_discord_id_idx ON reports (target_discord_id);

CREATE TABLE IF NOT EXISTS moderation_actions (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  target_discord_id VARCHAR(32) NOT NULL,
  moderator_discord_id VARCHAR(32) NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('WARN','TIMEOUT','KICK','BAN','UNBAN','NOTE')),
  reason TEXT,
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  revoked_by_discord_id VARCHAR(32),
  discord_case_id VARCHAR(32),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS moderation_actions_guild_id_target_discord_id_created_at_idx ON moderation_actions (guild_id, target_discord_id, created_at);
CREATE INDEX IF NOT EXISTS moderation_actions_guild_id_type_created_at_idx ON moderation_actions (guild_id, type, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS moderation_actions_guild_id_discord_case_id_key ON moderation_actions (guild_id, discord_case_id) WHERE discord_case_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS event_registrations (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  event_id VARCHAR(64) NOT NULL,
  discord_user_id VARCHAR(32) NOT NULL,
  status TEXT NOT NULL DEFAULT 'REGISTERED' CHECK (status IN ('REGISTERED','WAITLISTED','CANCELLED','ATTENDED','NO_SHOW')),
  notes TEXT,
  registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT event_registrations_guild_event_user_key UNIQUE (guild_id, event_id, discord_user_id)
);
CREATE INDEX IF NOT EXISTS event_registrations_guild_id_status_idx ON event_registrations (guild_id, status);
CREATE INDEX IF NOT EXISTS event_registrations_event_id_idx ON event_registrations (event_id);

CREATE TABLE IF NOT EXISTS polls (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  channel_id VARCHAR(32) NOT NULL,
  message_id VARCHAR(32),
  creator_discord_id VARCHAR(32) NOT NULL,
  question TEXT NOT NULL,
  options JSONB NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','OPEN','CLOSED','ARCHIVED')),
  closes_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS polls_guild_id_status_closes_at_idx ON polls (guild_id, status, closes_at);
CREATE UNIQUE INDEX IF NOT EXISTS polls_guild_id_message_id_key ON polls (guild_id, message_id) WHERE message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS poll_votes (
  id TEXT PRIMARY KEY,
  poll_id TEXT NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
  discord_user_id VARCHAR(32) NOT NULL,
  option_index INTEGER NOT NULL CHECK (option_index >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT poll_votes_poll_id_discord_user_id_key UNIQUE (poll_id, discord_user_id)
);
CREATE INDEX IF NOT EXISTS poll_votes_poll_id_option_index_idx ON poll_votes (poll_id, option_index);

CREATE TABLE IF NOT EXISTS suggestions (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  author_discord_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32),
  message_id VARCHAR(32),
  body TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','UNDER_REVIEW','PLANNED','IMPLEMENTED','DECLINED','ARCHIVED')),
  reviewer_discord_id VARCHAR(32),
  staff_response TEXT,
  upvote_count INTEGER NOT NULL DEFAULT 0 CHECK (upvote_count >= 0),
  downvote_count INTEGER NOT NULL DEFAULT 0 CHECK (downvote_count >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS suggestions_guild_id_status_created_at_idx ON suggestions (guild_id, status, created_at);
CREATE INDEX IF NOT EXISTS suggestions_author_discord_id_idx ON suggestions (author_discord_id);
CREATE UNIQUE INDEX IF NOT EXISTS suggestions_guild_id_message_id_key ON suggestions (guild_id, message_id) WHERE message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS streamers (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  discord_user_id VARCHAR(32),
  platform VARCHAR(32) NOT NULL,
  channel_handle VARCHAR(255) NOT NULL,
  display_name VARCHAR(100),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  last_live_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT streamers_guild_id_platform_channel_handle_key UNIQUE (guild_id, platform, channel_handle)
);
CREATE INDEX IF NOT EXISTS streamers_guild_id_enabled_idx ON streamers (guild_id, enabled);

CREATE TABLE IF NOT EXISTS stream_notifications (
  id TEXT PRIMARY KEY,
  streamer_id TEXT NOT NULL REFERENCES streamers(id) ON DELETE CASCADE,
  discord_message_id VARCHAR(32),
  stream_url TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT stream_notifications_streamer_id_started_at_key UNIQUE (streamer_id, started_at)
);
CREATE INDEX IF NOT EXISTS stream_notifications_started_at_idx ON stream_notifications (started_at);
CREATE UNIQUE INDEX IF NOT EXISTS stream_notifications_discord_message_id_key ON stream_notifications (discord_message_id) WHERE discord_message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS notifications (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  recipient_discord_id VARCHAR(32),
  channel_id VARCHAR(32),
  type VARCHAR(64) NOT NULL,
  payload JSONB NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED','CANCELLED')),
  dedupe_key VARCHAR(255),
  scheduled_for TIMESTAMPTZ,
  sent_at TIMESTAMPTZ,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT notifications_guild_id_dedupe_key_key UNIQUE (guild_id, dedupe_key)
);
CREATE INDEX IF NOT EXISTS notifications_status_scheduled_for_idx ON notifications (status, scheduled_for);
CREATE INDEX IF NOT EXISTS notifications_guild_id_created_at_idx ON notifications (guild_id, created_at);

CREATE TABLE IF NOT EXISTS audit_logs (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  actor_discord_id VARCHAR(32),
  action VARCHAR(100) NOT NULL,
  entity_type VARCHAR(100) NOT NULL,
  entity_id VARCHAR(255),
  correlation_id VARCHAR(100),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS audit_logs_guild_id_created_at_idx ON audit_logs (guild_id, created_at);
CREATE INDEX IF NOT EXISTS audit_logs_entity_type_entity_id_idx ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS audit_logs_correlation_id_idx ON audit_logs (correlation_id);

CREATE TABLE IF NOT EXISTS webhook_receipts (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) REFERENCES guild_configs(guild_id) ON DELETE SET NULL,
  source VARCHAR(64) NOT NULL,
  external_event_id VARCHAR(255) NOT NULL,
  signature VARCHAR(1024),
  payload JSONB NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMPTZ,
  error TEXT,
  CONSTRAINT webhook_receipts_source_external_event_id_key UNIQUE (source, external_event_id)
);
CREATE INDEX IF NOT EXISTS webhook_receipts_processed_at_idx ON webhook_receipts (processed_at);
CREATE INDEX IF NOT EXISTS webhook_receipts_guild_id_received_at_idx ON webhook_receipts (guild_id, received_at);

-- Immutable Minecraft-origin event ledger and retry deduplication boundary.
CREATE TABLE IF NOT EXISTS bridge_events (
  id TEXT PRIMARY KEY,
  server_id VARCHAR(100) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(32) NOT NULL CHECK (event_type IN ('CHAT','PLAYER_JOIN','PLAYER_LEAVE','PLAYER_DEATH','PLAYER_ADVANCEMENT','SERVER_STARTED','SERVER_STOPPING','MAINTENANCE')),
  occurred_at TIMESTAMPTZ NOT NULL,
  world_name VARCHAR(128),
  minecraft_uuid UUID,
  minecraft_name VARCHAR(16),
  content VARCHAR(500) NOT NULL,
  details JSONB NOT NULL DEFAULT '{}'::jsonb,
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT bridge_events_server_id_event_id_key UNIQUE (server_id, event_id)
);
CREATE INDEX IF NOT EXISTS bridge_events_server_id_received_at_idx ON bridge_events (server_id, received_at);
CREATE INDEX IF NOT EXISTS bridge_events_event_type_occurred_at_idx ON bridge_events (event_type, occurred_at);

-- Durable Discord-to-Minecraft queue. Rejections are terminal dead letters with an auditable reason.
CREATE TABLE IF NOT EXISTS chat_relay_queue (
  id TEXT PRIMARY KEY,
  server_id VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(128),
  discord_message_id VARCHAR(32),
  discord_author_id VARCHAR(32),
  display_name VARCHAR(48),
  target_world VARCHAR(128),
  content VARCHAR(500) NOT NULL,
  status TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','delivered','rejected')),
  delivery_attempts INTEGER NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0),
  last_attempt_at TIMESTAMPTZ,
  acknowledged_at TIMESTAMPTZ,
  acknowledgement_detail VARCHAR(500),
  dead_lettered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chat_relay_queue_server_id_idempotency_key_key UNIQUE (server_id, idempotency_key),
  CONSTRAINT chat_relay_queue_server_id_discord_message_id_key UNIQUE (server_id, discord_message_id)
);
CREATE INDEX IF NOT EXISTS chat_relay_queue_server_status_created_idx ON chat_relay_queue (server_id, status, created_at, id);
CREATE INDEX IF NOT EXISTS chat_relay_queue_dead_lettered_at_idx ON chat_relay_queue (dead_lettered_at) WHERE dead_lettered_at IS NOT NULL;

-- Canonical relay history used by the Discord bot for both directions; queue delivery state remains above.
CREATE TABLE IF NOT EXISTS chat_relay_messages (
  id TEXT PRIMARY KEY,
  guild_id VARCHAR(32) NOT NULL REFERENCES guild_configs(guild_id) ON DELETE CASCADE,
  direction TEXT NOT NULL CHECK (direction IN ('DISCORD_TO_GAME','GAME_TO_DISCORD')),
  discord_channel_id VARCHAR(32),
  discord_message_id VARCHAR(32),
  discord_author_id VARCHAR(32),
  external_message_id VARCHAR(255),
  content TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  relayed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chat_relay_messages_guild_direction_discord_message_key UNIQUE (guild_id, direction, discord_message_id)
);
CREATE INDEX IF NOT EXISTS chat_relay_messages_guild_id_relayed_at_idx ON chat_relay_messages (guild_id, relayed_at);
CREATE UNIQUE INDEX IF NOT EXISTS chat_relay_messages_guild_direction_external_message_key ON chat_relay_messages (guild_id, direction, external_message_id) WHERE external_message_id IS NOT NULL;
