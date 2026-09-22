import { randomUUID } from "node:crypto";
import type { AuthSession, AuthStore, DiscordIdentityProfile, PlatformUser } from "./types.js";

type OAuthState = { redirectUri: string; expiresAt: Date; consumedAt: Date | null };
type LoginTicket = { userId: string; expiresAt: Date; consumedAt: Date | null };
type StoredSession = { id: string; userId: string; expiresAt: Date; revokedAt: Date | null };

export class MemoryAuthStore implements AuthStore {
  public readonly kind = "memory" as const;
  private readonly states = new Map<string, OAuthState>();
  private readonly tickets = new Map<string, LoginTicket>();
  private readonly sessions = new Map<string, StoredSession>();
  private readonly users = new Map<string, PlatformUser>();
  private readonly discordUsers = new Map<string, string>();
  private demoUserId: string | null = null;

  constructor(private readonly now: () => Date = () => new Date()) {}

  async close(): Promise<void> {}

  async createOAuthState(stateHash: string, redirectUri: string, expiresAt: Date): Promise<void> {
    if (this.states.has(stateHash)) throw new Error("OAuth state collision");
    this.states.set(stateHash, { redirectUri, expiresAt, consumedAt: null });
  }

  async consumeOAuthState(stateHash: string, redirectUri: string): Promise<boolean> {
    const state = this.states.get(stateHash);
    if (!state || state.redirectUri !== redirectUri || state.consumedAt || state.expiresAt <= this.now()) return false;
    state.consumedAt = this.now();
    return true;
  }

  async upsertDiscordIdentity(profile: DiscordIdentityProfile): Promise<PlatformUser> {
    const existingId = this.discordUsers.get(profile.id);
    const now = this.now().toISOString();
    if (existingId) {
      const existing = this.users.get(existingId)!;
      const updated = { ...existing, displayName: profile.globalName ?? profile.username, avatarUrl: profile.avatarUrl, updatedAt: now };
      this.users.set(existingId, updated);
      return { ...updated };
    }
    const user: PlatformUser = {
      id: randomUUID(),
      displayName: profile.globalName ?? profile.username,
      avatarUrl: profile.avatarUrl,
      createdAt: now,
      updatedAt: now,
    };
    this.users.set(user.id, user);
    this.discordUsers.set(profile.id, user.id);
    return { ...user };
  }

  async getOrCreateDemoUser(displayName: string): Promise<PlatformUser> {
    const now = this.now().toISOString();
    if (this.demoUserId) {
      const existing = this.users.get(this.demoUserId)!;
      const updated = { ...existing, displayName, updatedAt: now };
      this.users.set(existing.id, updated);
      return { ...updated };
    }
    const user = { id: randomUUID(), displayName, avatarUrl: null, createdAt: now, updatedAt: now };
    this.demoUserId = user.id;
    this.users.set(user.id, user);
    return { ...user };
  }

  async issueLoginTicket(ticketHash: string, userId: string, expiresAt: Date): Promise<void> {
    if (!this.users.has(userId)) throw new Error("Unknown auth user");
    this.tickets.set(ticketHash, { userId, expiresAt, consumedAt: null });
  }

  async redeemLoginTicket(ticketHash: string, sessionHash: string, sessionExpiresAt: Date): Promise<AuthSession | null> {
    const ticket = this.tickets.get(ticketHash);
    if (!ticket || ticket.consumedAt || ticket.expiresAt <= this.now()) return null;
    ticket.consumedAt = this.now();
    const id = randomUUID();
    this.sessions.set(sessionHash, { id, userId: ticket.userId, expiresAt: sessionExpiresAt, revokedAt: null });
    return { id, user: { ...this.users.get(ticket.userId)! }, expiresAt: sessionExpiresAt.toISOString() };
  }

  async validateSession(sessionHash: string): Promise<AuthSession | null> {
    const session = this.sessions.get(sessionHash);
    if (!session || session.revokedAt || session.expiresAt <= this.now()) return null;
    const user = this.users.get(session.userId);
    return user ? { id: session.id, user: { ...user }, expiresAt: session.expiresAt.toISOString() } : null;
  }

  async getDiscordUserId(userId: string): Promise<string | null> { return [...this.discordUsers.entries()].find(([, platformUserId]) => platformUserId === userId)?.[0] ?? null; }

  async revokeSession(sessionHash: string): Promise<boolean> {
    const session = this.sessions.get(sessionHash);
    if (!session || session.revokedAt || session.expiresAt <= this.now()) return false;
    session.revokedAt = this.now();
    return true;
  }

  async cleanupAuthArtifacts(): Promise<void> {
    const now = this.now();
    for (const [key, value] of this.states) if (value.expiresAt <= now) this.states.delete(key);
    for (const [key, value] of this.tickets) if (value.expiresAt <= now) this.tickets.delete(key);
    for (const [key, value] of this.sessions) if (value.expiresAt <= now || value.revokedAt) this.sessions.delete(key);
  }
}
