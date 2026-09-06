import type { SetupResourceKind } from "../config/server-blueprint.js";

export interface SetupResourceRecord {
  readonly guildId: string;
  readonly resourceKey: string;
  readonly resourceType: SetupResourceKind;
  readonly discordId: string;
  readonly createdAt?: Date;
  readonly updatedAt?: Date;
}

export interface SetupResourceRepository {
  findByGuildAndKey(guildId: string, resourceKey: string): Promise<SetupResourceRecord | null>;
  upsert(record: Omit<SetupResourceRecord, "createdAt" | "updatedAt">): Promise<SetupResourceRecord>;
}

/**
 * Structural Prisma delegate contract. A real PrismaClient is compatible when
 * its model is named `discordSetupResource` with @@unique([guildId, resourceKey]).
 * This module intentionally does not import PrismaClient so consumers can use
 * either generated Prisma types or a test double.
 */
export interface PrismaSetupResourceRow {
  guildId: string;
  resourceKey: string;
  resourceType: string;
  discordId: string;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface PrismaSetupResourceDelegate {
  findUnique(args: {
    where: { guildId_resourceKey: { guildId: string; resourceKey: string } };
  }): Promise<PrismaSetupResourceRow | null>;
  upsert(args: {
    where: { guildId_resourceKey: { guildId: string; resourceKey: string } };
    create: Omit<PrismaSetupResourceRow, "createdAt" | "updatedAt">;
    update: Pick<PrismaSetupResourceRow, "resourceType" | "discordId">;
  }): Promise<PrismaSetupResourceRow>;
}

export interface PrismaClientWithSetupResources {
  discordSetupResource: PrismaSetupResourceDelegate;
}

export class PrismaSetupResourceRepository implements SetupResourceRepository {
  public constructor(private readonly prisma: PrismaClientWithSetupResources) {}

  public async findByGuildAndKey(guildId: string, resourceKey: string): Promise<SetupResourceRecord | null> {
    const row = await this.prisma.discordSetupResource.findUnique({
      where: { guildId_resourceKey: { guildId, resourceKey } }
    });
    return row ? this.toRecord(row) : null;
  }

  public async upsert(record: Omit<SetupResourceRecord, "createdAt" | "updatedAt">): Promise<SetupResourceRecord> {
    const row = await this.prisma.discordSetupResource.upsert({
      where: { guildId_resourceKey: { guildId: record.guildId, resourceKey: record.resourceKey } },
      create: record,
      update: { resourceType: record.resourceType, discordId: record.discordId }
    });
    return this.toRecord(row);
  }

  private toRecord(row: PrismaSetupResourceRow): SetupResourceRecord {
    return {
      guildId: row.guildId,
      resourceKey: row.resourceKey,
      resourceType: row.resourceType as SetupResourceKind,
      discordId: row.discordId,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt
    };
  }
}

/** Useful only for unit tests and local dry integration checks; not process-shared. */
export class MemorySetupResourceRepository implements SetupResourceRepository {
  private readonly records = new Map<string, SetupResourceRecord>();

  public findByGuildAndKey(guildId: string, resourceKey: string): Promise<SetupResourceRecord | null> {
    return Promise.resolve(this.records.get(this.storageKey(guildId, resourceKey)) ?? null);
  }

  public upsert(record: Omit<SetupResourceRecord, "createdAt" | "updatedAt">): Promise<SetupResourceRecord> {
    const now = new Date();
    const key = this.storageKey(record.guildId, record.resourceKey);
    const existing = this.records.get(key);
    const saved: SetupResourceRecord = {
      ...record,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    };
    this.records.set(key, saved);
    return Promise.resolve(saved);
  }

  private storageKey(guildId: string, resourceKey: string): string {
    return `${guildId}:${resourceKey}`;
  }
}

/**
 * Prisma model required by PrismaSetupResourceRepository:
 *
 * model DiscordSetupResource {
 *   guildId      String
 *   resourceKey  String
 *   resourceType String
 *   discordId    String
 *   createdAt    DateTime @default(now())
 *   updatedAt    DateTime @updatedAt
 *   @@id([guildId, resourceKey])
 * }
 */
