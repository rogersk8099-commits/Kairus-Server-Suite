import type { Prisma, PrismaClient } from "@prisma/client";

export interface IdempotencyStore {
  claim(key: string, ttlMs?: number, metadata?: Prisma.InputJsonValue): Promise<boolean>;
  release(key: string): Promise<void>;
}

export class PrismaIdempotencyStore implements IdempotencyStore {
  public constructor(private readonly database: PrismaClient) {}

  public async claim(key: string, ttlMs?: number, metadata: Prisma.InputJsonValue = {}): Promise<boolean> {
    const now = new Date();
    const expiresAt = ttlMs === undefined ? null : new Date(now.getTime() + ttlMs);
    await this.database.discordDedupeClaim.deleteMany({ where: { key, expiresAt: { lte: now } } });
    try {
      await this.database.discordDedupeClaim.create({ data: { key, metadata, expiresAt } });
      return true;
    } catch (error) {
      if ((error as { code?: string }).code === "P2002") return false;
      throw error;
    }
  }

  public async release(key: string): Promise<void> {
    await this.database.discordDedupeClaim.deleteMany({ where: { key } });
  }
}
