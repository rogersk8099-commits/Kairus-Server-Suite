-- Canonical Prisma persistence for the dedicated Discord bot.
-- Generated from discord-bot/prisma/schema.prisma and made idempotent for the
-- control-plane migration runner. Existing control-plane tables are untouched.

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_guilds" (
    "guild_id" VARCHAR(32) NOT NULL,
    "name" VARCHAR(100),
    "timezone" VARCHAR(64) NOT NULL DEFAULT 'UTC',
    "locale" VARCHAR(16) NOT NULL DEFAULT 'en-US',
    "profile_visibility" VARCHAR(16) NOT NULL DEFAULT 'members',
    "announcement_channel_id" VARCHAR(32),
    "maintenance_message" TEXT,
    "maintenance_enabled" BOOLEAN NOT NULL DEFAULT false,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_guilds_pkey" PRIMARY KEY ("guild_id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_setup_resources" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "resource_key" VARCHAR(100) NOT NULL,
    "resource_type" VARCHAR(32) NOT NULL,
    "discord_id" VARCHAR(32) NOT NULL,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_setup_resources_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_memberships" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "discord_user_id" VARCHAR(32) NOT NULL,
    "membership_tier_id" TEXT,
    "status" VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    "external_ref" TEXT,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "started_at" TIMESTAMPTZ(6),
    "expires_at" TIMESTAMPTZ(6),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_memberships_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_events" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "title" VARCHAR(200) NOT NULL,
    "description" TEXT NOT NULL,
    "starts_at" TIMESTAMPTZ(6) NOT NULL,
    "status" VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    "created_by" VARCHAR(32) NOT NULL,
    "announcement_channel_id" VARCHAR(32),
    "announcement_message_id" VARCHAR(32),
    "cancellation_reason" TEXT,
    "started_at" TIMESTAMPTZ(6),
    "cancelled_at" TIMESTAMPTZ(6),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_events_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_event_rsvps" (
    "id" TEXT NOT NULL,
    "event_id" TEXT NOT NULL,
    "discord_user_id" VARCHAR(32) NOT NULL,
    "status" VARCHAR(16) NOT NULL,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_event_rsvps_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_event_reminders" (
    "id" TEXT NOT NULL,
    "event_id" TEXT NOT NULL,
    "kind" VARCHAR(16) NOT NULL,
    "due_at" TIMESTAMPTZ(6) NOT NULL,
    "dedupe_key" VARCHAR(255) NOT NULL,
    "delivered_at" TIMESTAMPTZ(6),

    CONSTRAINT "discord_event_reminders_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_streamers" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "discord_user_id" VARCHAR(32) NOT NULL,
    "platform" VARCHAR(16) NOT NULL,
    "channel_url" TEXT NOT NULL,
    "channel_external_id" VARCHAR(255),
    "content_description" TEXT NOT NULL,
    "status" VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    "reviewed_by" VARCHAR(32),
    "review_reason" TEXT,
    "last_live_stream_id" VARCHAR(255),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_streamers_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_polls" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "question" TEXT NOT NULL,
    "options" JSONB NOT NULL,
    "status" VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    "created_by" VARCHAR(32) NOT NULL,
    "closes_at" TIMESTAMPTZ(6),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_polls_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_poll_votes" (
    "id" TEXT NOT NULL,
    "poll_id" TEXT NOT NULL,
    "linked_account_id" TEXT NOT NULL,
    "discord_user_id" VARCHAR(32) NOT NULL,
    "option_index" INTEGER NOT NULL,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "discord_poll_votes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_suggestions" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "author_discord_id" VARCHAR(32) NOT NULL,
    "title" VARCHAR(200) NOT NULL,
    "body" TEXT NOT NULL,
    "state" VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    "staff_note" TEXT,
    "channel_id" VARCHAR(32),
    "message_id" VARCHAR(32),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_suggestions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_suggestion_votes" (
    "id" TEXT NOT NULL,
    "suggestion_id" TEXT NOT NULL,
    "discord_user_id" VARCHAR(32) NOT NULL,
    "value" INTEGER NOT NULL,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_suggestion_votes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_tickets" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "requester_discord_id" VARCHAR(32) NOT NULL,
    "category" VARCHAR(32) NOT NULL,
    "subject" VARCHAR(200) NOT NULL,
    "description" TEXT NOT NULL,
    "channel_id" VARCHAR(32) NOT NULL,
    "state" VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    "assigned_to" VARCHAR(32),
    "closed_by" VARCHAR(32),
    "closed_at" TIMESTAMPTZ(6),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_tickets_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_ticket_notes" (
    "id" TEXT NOT NULL,
    "ticket_id" TEXT NOT NULL,
    "author_discord_id" VARCHAR(32) NOT NULL,
    "body" TEXT NOT NULL,
    "internal" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "discord_ticket_notes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_reports" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "reporter_discord_id" VARCHAR(32) NOT NULL,
    "accused_player" VARCHAR(100) NOT NULL,
    "reason" TEXT NOT NULL,
    "evidence_urls" JSONB NOT NULL DEFAULT '[]',
    "status" VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    "reviewed_by" VARCHAR(32),
    "resolution" TEXT,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_reports_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_moderation_actions" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "target_discord_id" VARCHAR(32) NOT NULL,
    "moderator_discord_id" VARCHAR(32) NOT NULL,
    "action" VARCHAR(16) NOT NULL,
    "reason" TEXT NOT NULL,
    "duration_seconds" INTEGER,
    "platform_case_id" VARCHAR(255),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "discord_moderation_actions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_notifications" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32) NOT NULL,
    "target" VARCHAR(16) NOT NULL,
    "recipient_discord_id" VARCHAR(32),
    "subject" VARCHAR(200) NOT NULL,
    "body" TEXT NOT NULL,
    "dedupe_key" VARCHAR(255) NOT NULL,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "status" VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    "scheduled_for" TIMESTAMPTZ(6),
    "sent_at" TIMESTAMPTZ(6),
    "failure_reason" TEXT,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "discord_notifications_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_dedupe_claims" (
    "key" VARCHAR(255) NOT NULL,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "expires_at" TIMESTAMPTZ(6),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "discord_dedupe_claims_pkey" PRIMARY KEY ("key")
);

-- CreateTable
CREATE TABLE IF NOT EXISTS "discord_webhook_receipts" (
    "id" TEXT NOT NULL,
    "guild_id" VARCHAR(32),
    "source" VARCHAR(64) NOT NULL,
    "external_event_id" VARCHAR(255) NOT NULL,
    "signature_hash" VARCHAR(64) NOT NULL,
    "payload" JSONB NOT NULL,
    "received_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "processed_at" TIMESTAMPTZ(6),
    "error" TEXT,

    CONSTRAINT "discord_webhook_receipts_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "discord_setup_resources_guild_type_idx" ON "discord_setup_resources"("guild_id", "resource_type");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_setup_resources_guild_id_resource_key_key" ON "discord_setup_resources"("guild_id", "resource_key");
CREATE INDEX IF NOT EXISTS "discord_memberships_guild_status_idx" ON "discord_memberships"("guild_id", "status");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_memberships_guild_user_key" ON "discord_memberships"("guild_id", "discord_user_id");
CREATE INDEX IF NOT EXISTS "discord_events_guild_status_start_idx" ON "discord_events"("guild_id", "status", "starts_at");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_event_rsvps_event_user_key" ON "discord_event_rsvps"("event_id", "discord_user_id");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_event_reminders_dedupe_key_key" ON "discord_event_reminders"("dedupe_key");
CREATE INDEX IF NOT EXISTS "discord_event_reminders_due_idx" ON "discord_event_reminders"("due_at", "delivered_at");
CREATE INDEX IF NOT EXISTS "discord_streamers_guild_status_idx" ON "discord_streamers"("guild_id", "status");
CREATE INDEX IF NOT EXISTS "discord_polls_guild_status_close_idx" ON "discord_polls"("guild_id", "status", "closes_at");
CREATE INDEX IF NOT EXISTS "discord_poll_votes_poll_option_idx" ON "discord_poll_votes"("poll_id", "option_index");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_poll_votes_poll_account_key" ON "discord_poll_votes"("poll_id", "linked_account_id");
CREATE INDEX IF NOT EXISTS "discord_suggestions_guild_state_idx" ON "discord_suggestions"("guild_id", "state", "created_at");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_suggestion_votes_suggestion_user_key" ON "discord_suggestion_votes"("suggestion_id", "discord_user_id");
CREATE INDEX IF NOT EXISTS "discord_tickets_guild_requester_state_idx" ON "discord_tickets"("guild_id", "requester_discord_id", "state");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_tickets_guild_channel_key" ON "discord_tickets"("guild_id", "channel_id");
CREATE INDEX IF NOT EXISTS "discord_ticket_notes_ticket_created_idx" ON "discord_ticket_notes"("ticket_id", "created_at");
CREATE INDEX IF NOT EXISTS "discord_reports_guild_status_idx" ON "discord_reports"("guild_id", "status", "created_at");
CREATE INDEX IF NOT EXISTS "discord_moderation_target_idx" ON "discord_moderation_actions"("guild_id", "target_discord_id", "created_at");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_notifications_dedupe_key_key" ON "discord_notifications"("dedupe_key");
CREATE INDEX IF NOT EXISTS "discord_notifications_status_scheduled_idx" ON "discord_notifications"("status", "scheduled_for");
CREATE INDEX IF NOT EXISTS "discord_dedupe_claims_expires_idx" ON "discord_dedupe_claims"("expires_at");
CREATE INDEX IF NOT EXISTS "discord_webhook_receipts_processed_idx" ON "discord_webhook_receipts"("processed_at");
CREATE UNIQUE INDEX IF NOT EXISTS "discord_webhook_receipts_source_event_key" ON "discord_webhook_receipts"("source", "external_event_id");

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_setup_resources_guild_id_fkey') THEN
    ALTER TABLE "discord_setup_resources" ADD CONSTRAINT "discord_setup_resources_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_memberships_guild_id_fkey') THEN
    ALTER TABLE "discord_memberships" ADD CONSTRAINT "discord_memberships_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_events_guild_id_fkey') THEN
    ALTER TABLE "discord_events" ADD CONSTRAINT "discord_events_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_event_rsvps_event_id_fkey') THEN
    ALTER TABLE "discord_event_rsvps" ADD CONSTRAINT "discord_event_rsvps_event_id_fkey" FOREIGN KEY ("event_id") REFERENCES "discord_events"("id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_event_reminders_event_id_fkey') THEN
    ALTER TABLE "discord_event_reminders" ADD CONSTRAINT "discord_event_reminders_event_id_fkey" FOREIGN KEY ("event_id") REFERENCES "discord_events"("id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_streamers_guild_id_fkey') THEN
    ALTER TABLE "discord_streamers" ADD CONSTRAINT "discord_streamers_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_polls_guild_id_fkey') THEN
    ALTER TABLE "discord_polls" ADD CONSTRAINT "discord_polls_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_poll_votes_poll_id_fkey') THEN
    ALTER TABLE "discord_poll_votes" ADD CONSTRAINT "discord_poll_votes_poll_id_fkey" FOREIGN KEY ("poll_id") REFERENCES "discord_polls"("id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_suggestions_guild_id_fkey') THEN
    ALTER TABLE "discord_suggestions" ADD CONSTRAINT "discord_suggestions_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_suggestion_votes_suggestion_id_fkey') THEN
    ALTER TABLE "discord_suggestion_votes" ADD CONSTRAINT "discord_suggestion_votes_suggestion_id_fkey" FOREIGN KEY ("suggestion_id") REFERENCES "discord_suggestions"("id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_tickets_guild_id_fkey') THEN
    ALTER TABLE "discord_tickets" ADD CONSTRAINT "discord_tickets_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_ticket_notes_ticket_id_fkey') THEN
    ALTER TABLE "discord_ticket_notes" ADD CONSTRAINT "discord_ticket_notes_ticket_id_fkey" FOREIGN KEY ("ticket_id") REFERENCES "discord_tickets"("id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_reports_guild_id_fkey') THEN
    ALTER TABLE "discord_reports" ADD CONSTRAINT "discord_reports_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_moderation_actions_guild_id_fkey') THEN
    ALTER TABLE "discord_moderation_actions" ADD CONSTRAINT "discord_moderation_actions_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_notifications_guild_id_fkey') THEN
    ALTER TABLE "discord_notifications" ADD CONSTRAINT "discord_notifications_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE CASCADE ON UPDATE CASCADE;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'discord_webhook_receipts_guild_id_fkey') THEN
    ALTER TABLE "discord_webhook_receipts" ADD CONSTRAINT "discord_webhook_receipts_guild_id_fkey" FOREIGN KEY ("guild_id") REFERENCES "discord_guilds"("guild_id") ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
