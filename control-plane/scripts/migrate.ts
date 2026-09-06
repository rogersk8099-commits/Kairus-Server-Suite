import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import pg from "pg";

async function main() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) throw new Error("DATABASE_URL is required to run migrations");
  const migrationsPath = join(process.cwd(), "migrations");
  const migrations = (await readdir(migrationsPath)).filter((file) => file.endsWith(".sql")).sort();
  const pool = new pg.Pool({ connectionString: databaseUrl, ssl: databaseUrl.includes("localhost") ? undefined : { rejectUnauthorized: false } });
  try {
    await pool.query("CREATE TABLE IF NOT EXISTS schema_migrations (id TEXT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW())");
    for (const filename of migrations) {
      const applied = await pool.query("SELECT 1 FROM schema_migrations WHERE id = $1", [filename]);
      if (applied.rowCount) continue;
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        await client.query(await readFile(join(migrationsPath, filename), "utf8"));
        await client.query("INSERT INTO schema_migrations (id) VALUES ($1)", [filename]);
        await client.query("COMMIT");
        console.log(`Applied ${filename}`);
      } catch (error) {
        await client.query("ROLLBACK").catch(() => undefined);
        throw error;
      } finally { client.release(); }
    }
  } finally { await pool.end(); }
}

main().catch((error: unknown) => { console.error(error); process.exitCode = 1; });
