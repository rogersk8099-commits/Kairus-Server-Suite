import type { PrismaClient } from "@prisma/client";
import type { SetupResourceRecord, SetupResourceRepository } from "./services/setup-resource.repository.js";

export class SetupResourceStore implements SetupResourceRepository {
  public constructor(private readonly database: PrismaClient) {}
  public async findByGuildAndKey(guildId: string, resourceKey: string): Promise<SetupResourceRecord | null> {
    const row = await this.database.discordSetupResource.findUnique({ where: { guildId_resourceKey: { guildId, resourceKey } } });
    return row ? { guildId: row.guildId, resourceKey: row.resourceKey, resourceType: row.resourceType as SetupResourceRecord["resourceType"], discordId: row.discordId, createdAt: row.createdAt, updatedAt: row.updatedAt } : null;
  }
  public async upsert(record: Omit<SetupResourceRecord, "createdAt" | "updatedAt">): Promise<SetupResourceRecord> {
    await this.database.discordGuild.upsert({ where: { guildId: record.guildId }, create: { guildId: record.guildId }, update: {} });
    const row = await this.database.discordSetupResource.upsert({ where: { guildId_resourceKey: { guildId: record.guildId, resourceKey: record.resourceKey } }, create: record, update: { resourceType: record.resourceType, discordId: record.discordId } });
    return { guildId: row.guildId, resourceKey: row.resourceKey, resourceType: row.resourceType as SetupResourceRecord["resourceType"], discordId: row.discordId, createdAt: row.createdAt, updatedAt: row.updatedAt };
  }
}
