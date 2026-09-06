import { PrismaClient } from "@prisma/client";

const globalDatabase = globalThis as typeof globalThis & { kairuPrisma?: PrismaClient };

export const prisma = globalDatabase.kairuPrisma ?? new PrismaClient({
  log: process.env.NODE_ENV === "development" ? ["warn", "error"] : ["error"]
});

if (process.env.NODE_ENV !== "production") globalDatabase.kairuPrisma = prisma;
