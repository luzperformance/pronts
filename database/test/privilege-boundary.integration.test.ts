import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import {
  PostgreSqlContainer,
  type StartedPostgreSqlContainer,
} from "@testcontainers/postgresql";
import pg from "pg";
import test from "node:test";
import { probeDatabasePrivileges } from "../src/privilege-contract.js";

const execFileAsync = promisify(execFile);
const { Client } = pg;

const databaseName = "primeiro_prontuario";
const bootstrapRole = "bootstrap_admin";
const bootstrapPassword = "ephemeral-bootstrap-password";
const migrationRole = "primeiro_prontuario_migration";
const migrationPassword = "ephemeral-migration-password";
const runtimeRole = "primeiro_prontuario_runtime";
const runtimePassword = "ephemeral-runtime-password";

test("Drizzle migration and runtime credentials enforce the approved privilege boundary", async () => {
  const database = await new PostgreSqlContainer("postgres:18.4")
    .withDatabase(databaseName)
    .withUsername(bootstrapRole)
    .withPassword(bootstrapPassword)
    .start();

  try {
    await createExternalRoles(database.getConnectionUri());
    await execFileAsync("npm", ["run", "migrate"], {
      env: {
        ...process.env,
        DATABASE_URL: connectionUriForRole(
          database,
          migrationRole,
          migrationPassword,
        ),
      },
    });

    const migrationClient = new Client({
      connectionString: connectionUriForRole(
        database,
        migrationRole,
        migrationPassword,
      ),
    });
    const runtimeClient = new Client({
      database: database.getDatabase(),
      host: database.getHost(),
      password: runtimePassword,
      port: database.getPort(),
      user: runtimeRole,
    });
    await Promise.all([migrationClient.connect(), runtimeClient.connect()]);

    try {
      assert.deepEqual(
        await probeDatabasePrivileges({
          migrationClient,
          migrationRole,
          runtimeClient,
        }),
        {
          migrationCanCreateAndAlter: true,
          migrationCanCreateRole: false,
          migrationIsSuperuser: false,
          runtimeCanConnect: true,
          runtimeCanDeleteFutureRows: true,
          runtimeCanInsertFutureRows: true,
          runtimeCanSelectFutureRows: true,
          runtimeCanUpdateFutureRows: true,
          runtimeCanUseFutureSequence: true,
          runtimeCanUseSchema: true,
          runtimeHasCurrentTableDml: true,
          runtimeCreateSqlState: "42501",
          runtimeAlterSqlState: "42501",
          runtimeDropSqlState: "42501",
          runtimeTemporaryCreateSqlState: "42501",
          runtimeAssumeMigrationRoleSqlState: "42501",
        },
      );
    } finally {
      await Promise.all([migrationClient.end(), runtimeClient.end()]);
    }
  } finally {
    await database.stop();
  }
});

async function createExternalRoles(connectionString: string): Promise<void> {
  const client = new Client({ connectionString });
  await client.connect();
  try {
    await client.query(`
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
  } finally {
    await client.end();
  }
}

function connectionUriForRole(
  container: StartedPostgreSqlContainer,
  role: string,
  password: string,
): string {
  const connectionUri = new URL(container.getConnectionUri());
  connectionUri.username = role;
  connectionUri.password = password;
  return connectionUri.toString();
}
