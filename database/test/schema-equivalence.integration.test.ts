import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { resolve } from "node:path";
import test from "node:test";
import { promisify } from "node:util";
import {
  PostgreSqlContainer,
  type StartedPostgreSqlContainer,
} from "@testcontainers/postgresql";
import pg from "pg";
import { GenericContainer, Network, Wait } from "testcontainers";
import {
  captureSchemaContract,
  compareSchemaContracts,
} from "../src/schema-contract.js";
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

test("Flyway V1-V16 and the Drizzle baseline expose the same schema contract", async () => {
  const network = await new Network().start();
  const flywayDatabase = await new PostgreSqlContainer("postgres:18.4")
    .withDatabase(databaseName)
    .withUsername(bootstrapRole)
    .withPassword(bootstrapPassword)
    .withNetwork(network)
    .withNetworkAliases("flyway-postgres")
    .start();
  const drizzleDatabase = await new PostgreSqlContainer("postgres:18.4")
    .withDatabase(databaseName)
    .withUsername(bootstrapRole)
    .withPassword(bootstrapPassword)
    .start();

  try {
    await Promise.all([
      createExternalRoles(flywayDatabase.getConnectionUri()),
      createExternalRoles(drizzleDatabase.getConnectionUri()),
    ]);

    const flyway = await new GenericContainer("flyway/flyway:12.4.0")
      .withNetwork(network)
      .withBindMounts([
        {
          mode: "ro",
          source: resolve(
            process.cwd(),
            "../src/main/resources/db/migration",
          ),
          target: "/flyway/sql",
        },
      ])
      .withCommand([
        `-url=jdbc:postgresql://flyway-postgres:5432/${databaseName}`,
        `-user=${migrationRole}`,
        `-password=${migrationPassword}`,
        "-connectRetries=60",
        "migrate",
      ])
      .withWaitStrategy(Wait.forOneShotStartup())
      .start();
    await flyway.stop();

    await execFileAsync("npm", ["run", "migrate"], {
      env: {
        ...process.env,
        DATABASE_URL: connectionUriForRole(
          drizzleDatabase,
          migrationRole,
          migrationPassword,
        ),
      },
    });

    const flywayClient = new Client({
      connectionString: connectionUriForRole(
        flywayDatabase,
        migrationRole,
        migrationPassword,
      ),
    });
    const drizzleClient = new Client({
      connectionString: connectionUriForRole(
        drizzleDatabase,
        migrationRole,
        migrationPassword,
      ),
    });
    await Promise.all([flywayClient.connect(), drizzleClient.connect()]);

    try {
      const [flywayContract, drizzleContract] = await Promise.all([
        captureSchemaContract(flywayClient),
        captureSchemaContract(drizzleClient),
      ]);

      assert.deepEqual(
        compareSchemaContracts(flywayContract, drizzleContract),
        [],
      );
    } finally {
      await Promise.all([flywayClient.end(), drizzleClient.end()]);
    }
  } finally {
    await Promise.all([flywayDatabase.stop(), drizzleDatabase.stop()]);
    await network.stop();
  }
});

test("migration and runtime credentials enforce the approved privilege boundary", async () => {
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

      GRANT CONNECT, CREATE
      ON DATABASE ${databaseName}
      TO ${migrationRole};

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
