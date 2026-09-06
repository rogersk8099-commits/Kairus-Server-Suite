export type PlatformUser = {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
};

export type DiscordIdentityProfile = {
  id: string;
  username: string;
  globalName: string | null;
  avatarHash: string | null;
  avatarUrl: string | null;
  rawProfile: Record<string, unknown>;
};

export type AuthSession = {
  id: string;
  user: PlatformUser;
  expiresAt: string;
};

export interface AuthStore {
  readonly kind: "memory" | "postgres";
  close(): Promise<void>;
  createOAuthState(stateHash: string, redirectUri: string, expiresAt: Date): Promise<void>;
  consumeOAuthState(stateHash: string, redirectUri: string): Promise<boolean>;
  upsertDiscordIdentity(profile: DiscordIdentityProfile): Promise<PlatformUser>;
  getOrCreateDemoUser(displayName: string): Promise<PlatformUser>;
  issueLoginTicket(ticketHash: string, userId: string, expiresAt: Date): Promise<void>;
  redeemLoginTicket(
    ticketHash: string,
    sessionHash: string,
    sessionExpiresAt: Date,
  ): Promise<AuthSession | null>;
  validateSession(sessionHash: string): Promise<AuthSession | null>;
  revokeSession(sessionHash: string): Promise<boolean>;
  cleanupAuthArtifacts(): Promise<void>;
}
