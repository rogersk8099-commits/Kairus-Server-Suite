import { randomUUID } from "node:crypto";
import pg from "pg";
import type { AuthSession, AuthStore, DiscordIdentityProfile, PlatformUser } from "./types.js";

const { Pool } = pg;
type Row = Record<string, unknown>;

function timestamp(value: unknown): string {
  return new Date(value as string | Date).toISOString();
}

function userFrom(row: Row): PlatformUser {
  return {
    id: String(row.id),
    displayName: String(row.display_name),
    avatarUrl: row.avatar_url ? String(row.avatar_url) : null,
    createdAt: timestamp(row.created_at),
    updatedAt: timestamp(row.updated_at),
  };
}

function sessionFrom(row: Row): AuthSession {
  return {
    id: String(row.session_id),
    user: userFrom({
      id: row.user_id,
      display_name: row.display_name,
      avatar_url: row.avatar_url,
      created_at: row.user_created_at,
      updated_at: row.user_updated_at,
    }),
    expiresAt: timestamp(row.expires_at),
  };
}

export class PostgresAuthStore implements AuthStore {
  public readonly kind = "postgres" as const;
  private readonly pool: pg.Pool;

  constructor(databaseUrl: string) {
    this.pool = new Pool({
      connectionString: databaseUrl,
      max: 10,
      ssl: databaseUrl.includes("localhost") ? undefined : { rejectUnauthorized: false },
    });
  }

  async close(): Promise<void> { await this.pool.end(); }

  async createOAuthState(stateHash: string, redirectUri: string, expiresAt: Date): Promise<void> {
    await this.pool.query(
      "INSERT INTO auth_oauth_states (id, provider, state_hash, redirect_uri, expires_at) VALUES ($1, 'discord', $2, $3, $4)",
      [randomUUID(), stateHash, redirectUri, expiresAt],
    );
  }

  async consumeOAuthState(stateHash: string, redirectUri: string): Promise<boolean> {
    const result = await this.pool.query(
      `UPDATE auth_oauth_states SET consumed_at = NOW()
       WHERE state_hash = $1 AND provider = 'discord' AND redirect_uri = $2
         AND consumed_at IS NULL AND expires_at > NOW()
       RETURNING id`,
      [stateHash, redirectUri],
    );
    return result.rowCount === 1;
  }

  async upsertDiscordIdentity(profile: DiscordIdentityProfile): Promise<PlatformUser> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(
        "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))",
        [`discord:${profile.id}`],
      );
      const existing = await client.query(
        "SELECT user_id FROM oauth_identities WHERE provider = 'discord' AND provider_account_id = $1 FOR UPDATE",
        [profile.id],
      );
      const displayName = profile.globalName ?? profile.username;
      let userId: string;
      if (existing.rows[0]) {
        userId = String(existing.rows[0].user_id);
        await client.query(
          "UPDATE platform_users SET display_name = $2, avatar_url = $3, updated_at = NOW(), last_login_at = NOW() WHERE id = $1",
          [userId, displayName, profile.avatarUrl],
        );
      } else {
        userId = randomUUID();
        await client.query(
          "INSERT INTO platform_users (id, display_name, avatar_url, last_login_at) VALUES ($1, $2, $3, NOW())",
          [userId, displayName, profile.avatarUrl],
        );
      }
      await client.query(
        `INSERT INTO oauth_identities
           (id, user_id, provider, provider_account_id, username, display_name, avatar_hash, profile, last_login_at)
         VALUES ($1, $2, 'discord', $3, $4, $5, $6, $7::jsonb, NOW())
         ON CONFLICT (provider, provider_account_id) DO UPDATE SET
           username = EXCLUDED.username, display_name = EXCLUDED.display_name,
           avatar_hash = EXCLUDED.avatar_hash, profile = EXCLUDED.profile,
           updated_at = NOW(), last_login_at = NOW()`,
        [randomUUID(), userId, profile.id, profile.username, profile.globalName, profile.avatarHash, JSON.stringify(profile.rawProfile)],
      );
      const result = await client.query("SELECT * FROM platform_users WHERE id = $1", [userId]);
      await client.query("COMMIT");
      return userFrom(result.rows[0]);
    } catch (error) {
      await client.query("ROLLBACK").catch(() => undefined);
      throw error;
    } finally { client.release(); }
  }

  async getOrCreateDemoUser(displayName: string): Promise<PlatformUser> {
    const id = "00000000-0000-4000-8000-000000000001";
    const { rows } = await this.pool.query(
      `INSERT INTO platform_users (id, display_name, avatar_url, last_login_at)
       VALUES ($1, $2, NULL, NOW())
       ON CONFLICT (id) DO UPDATE SET display_name = EXCLUDED.display_name, updated_at = NOW(), last_login_at = NOW()
       RETURNING *`,
      [id, displayName],
    );
    return userFrom(rows[0]);
  }

  async issueLoginTicket(ticketHash: string, userId: string, expiresAt: Date): Promise<void> {
    await this.pool.query(
      "INSERT INTO auth_login_tickets (id, ticket_hash, user_id, expires_at) VALUES ($1, $2, $3, $4)",
      [randomUUID(), ticketHash, userId, expiresAt],
    );
  }

  async redeemLoginTicket(ticketHash: string, sessionHash: string, sessionExpiresAt: Date): Promise<AuthSession | null> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      const ticket = await client.query(
        `UPDATE auth_login_tickets SET consumed_at = NOW()
         WHERE ticket_hash = $1 AND consumed_at IS NULL AND expires_at > NOW()
         RETURNING user_id`,
        [ticketHash],
      );
      if (!ticket.rows[0]) {
        await client.query("ROLLBACK");
        return null;
      }
      const sessionId = randomUUID();
      const result = await client.query(
        `WITH inserted AS (
           INSERT INTO auth_sessions (id, session_hash, user_id, expires_at)
           VALUES ($1, $2, $3, $4) RETURNING *
         )
         SELECT inserted.id AS session_id, inserted.expires_at,
                u.id AS user_id, u.display_name, u.avatar_url,
                u.created_at AS user_created_at, u.updated_at AS user_updated_at
         FROM inserted JOIN platform_users u ON u.id = inserted.user_id`,
        [sessionId, sessionHash, ticket.rows[0].user_id, sessionExpiresAt],
      );
      await client.query("COMMIT");
      return sessionFrom(result.rows[0]);
    } catch (error) {
      await client.query("ROLLBACK").catch(() => undefined);
      throw error;
    } finally { client.release(); }
  }

  async validateSession(sessionHash: string): Promise<AuthSession | null> {
    const { rows } = await this.pool.query(
      `WITH valid AS (
         UPDATE auth_sessions SET last_seen_at = NOW()
         WHERE session_hash = $1 AND revoked_at IS NULL AND expires_at > NOW()
         RETURNING *
       )
       SELECT valid.id AS session_id, valid.expires_at,
              u.id AS user_id, u.display_name, u.avatar_url,
              u.created_at AS user_created_at, u.updated_at AS user_updated_at
       FROM valid JOIN platform_users u ON u.id = valid.user_id`,
      [sessionHash],
    );
    return rows[0] ? sessionFrom(rows[0]) : null;
  }

  async revokeSession(sessionHash: string): Promise<boolean> {
    const result = await this.pool.query(
      "UPDATE auth_sessions SET revoked_at = NOW() WHERE session_hash = $1 AND revoked_at IS NULL AND expires_at > NOW()",
      [sessionHash],
    );
    return result.rowCount === 1;
  }

  async cleanupAuthArtifacts(): Promise<void> {
    await this.pool.query("SELECT cleanup_auth_artifacts()");
  }
}
