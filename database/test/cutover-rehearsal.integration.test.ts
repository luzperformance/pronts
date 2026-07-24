import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import { promisify } from "node:util";
import { PostgreSqlContainer } from "@testcontainers/postgresql";
import pg from "pg";
import test from "node:test";

const execFileAsync = promisify(execFile);
const { Client } = pg;

const databaseName = "primeiro_prontuario";
const ownerRole = "cutover_owner";
const ownerPassword = "ephemeral-owner-password";
const migrationRole = "primeiro_prontuario_migration";
const migrationPassword = "ephemeral-migration-password";
const runtimeRole = "primeiro_prontuario_runtime";
const runtimePassword = "ephemeral-runtime-password";
const baselinePath = new URL("../drizzle/0000_baseline.sql", import.meta.url);

test("the manual gate rehearses the validated SQL on an empty PostgreSQL without importing data", async () => {
  const baselineDigestBefore = await sha256(baselinePath);
  const database = await new PostgreSqlContainer("postgres:18.4")
    .withDatabase(databaseName)
    .withUsername(ownerRole)
    .withPassword(ownerPassword)
    .start();
  const ownerClient = new Client({
    connectionString: database.getConnectionUri(),
  });

  try {
    await ownerClient.connect();
    assert.equal(await countPublicRelations(ownerClient), 0);
    assert.equal(await hasDrizzleHistory(ownerClient), false);

    await createExternalRoles(ownerClient);

    await execFileAsync("npm", ["run", "migrate"], {
      env: {
        ...process.env,
        DATABASE_URL: connectionUriForRole(
          database.getConnectionUri(),
          migrationRole,
          migrationPassword,
        ),
      },
    });

    assert.equal(await sha256(baselinePath), baselineDigestBefore);
    assert.equal(await countDrizzleMigrations(ownerClient), 1);

    const runtimeClient = new Client({
      connectionString: connectionUriForRole(
        database.getConnectionUri(),
        runtimeRole,
        runtimePassword,
      ),
    });
    await runtimeClient.connect();
    try {
      const structuralRows = await runtimeClient.query<{
        calendar_count: string;
        marker_count: string;
      }>(`
        SELECT
          (SELECT count(*) FROM schema_marker) AS marker_count,
          (SELECT count(*) FROM schedule_calendar) AS calendar_count
      `);
      assert.deepEqual(structuralRows.rows[0], {
        calendar_count: "1",
        marker_count: "1",
      });

      const domainRows = await runtimeClient.query<{ row_count: string }>(`
        SELECT (
          (SELECT count(*) FROM doctor_account)
          + (SELECT count(*) FROM patient)
          + (SELECT count(*) FROM appointment)
          + (SELECT count(*) FROM schedule_block)
          + (SELECT count(*) FROM consultation)
          + (SELECT count(*) FROM addendum)
          + (SELECT count(*) FROM attachment)
          + (SELECT count(*) FROM audit_event)
        ) AS row_count
      `);
      assert.equal(domainRows.rows[0]?.row_count, "0");
    } finally {
      await runtimeClient.end();
    }
  } finally {
    await ownerClient.end().catch(() => undefined);
    await database.stop();
  }
});

async function createExternalRoles(ownerClient: pg.Client): Promise<void> {
  await ownerClient.query(`
    CREATE ROLE ${migrationRole}
    LOGIN
    PASSWORD '${migrationPassword}'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS;

    CREATE ROLE ${runtimeRole}
    LOGIN
    PASSWORD '${runtimePassword}'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS;

    REVOKE CONNECT, TEMPORARY
    ON DATABASE ${databaseName}
    FROM PUBLIC;

    GRANT CONNECT, CREATE
    ON DATABASE ${databaseName}
    TO ${migrationRole};

    GRANT CONNECT
    ON DATABASE ${databaseName}
    TO ${runtimeRole};

    GRANT USAGE, CREATE
    ON SCHEMA public
    TO ${migrationRole};
  `);
}

async function countPublicRelations(client: pg.Client): Promise<number> {
  const result = await client.query<{ relation_count: string }>(`
    SELECT count(*) AS relation_count
    FROM pg_catalog.pg_class AS relation
    JOIN pg_catalog.pg_namespace AS namespace
      ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = 'public'
      AND relation.relkind IN ('r', 'p', 'S', 'v', 'm', 'f')
  `);
  return Number(result.rows[0]?.relation_count);
}

async function hasDrizzleHistory(client: pg.Client): Promise<boolean> {
  const result = await client.query<{ history_exists: boolean }>(`
    SELECT to_regclass('drizzle.__drizzle_migrations') IS NOT NULL
      AS history_exists
  `);
  return result.rows[0]?.history_exists ?? false;
}

async function countDrizzleMigrations(client: pg.Client): Promise<number> {
  const result = await client.query<{ migration_count: string }>(`
    SELECT count(*) AS migration_count
    FROM drizzle.__drizzle_migrations
  `);
  return Number(result.rows[0]?.migration_count);
}

function connectionUriForRole(
  ownerConnectionUri: string,
  role: string,
  password: string,
): string {
  const connectionUri = new URL(ownerConnectionUri);
  connectionUri.username = role;
  connectionUri.password = password;
  return connectionUri.toString();
}

async function sha256(file: URL): Promise<string> {
  return createHash("sha256").update(await readFile(file)).digest("hex");
}
