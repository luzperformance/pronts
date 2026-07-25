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
import { captureSchemaContract, compareSchemaContracts } from "../src/schema-contract.js";

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
