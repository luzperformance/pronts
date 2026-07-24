import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { PostgreSqlContainer } from "@testcontainers/postgresql";
import pg from "pg";
import test from "node:test";

const execFileAsync = promisify(execFile);
const { Client } = pg;

const runtimeRole = "primeiro_prontuario_runtime";
const runtimePassword = "ephemeral-test-password";

test("the public Drizzle commands create the current schema on PostgreSQL 18", async () => {
  const container = await new PostgreSqlContainer("postgres:18.4")
    .withDatabase("primeiro_prontuario")
    .withUsername("primeiro_prontuario_migration")
    .withPassword("ephemeral-migration-password")
    .start();

  const migrationClient = new Client({
    connectionString: container.getConnectionUri(),
  });

  try {
    await migrationClient.connect();
    await migrationClient.query(
      `CREATE ROLE ${runtimeRole} LOGIN PASSWORD '${runtimePassword}'`,
    );

    const commandEnvironment = { ...process.env };
    delete commandEnvironment.DATABASE_URL;

    await execFileAsync("npm", ["run", "generate"], {
      env: commandEnvironment,
    });
    await execFileAsync("npm", ["run", "migrate"], {
      env: {
        ...commandEnvironment,
        DATABASE_URL: container.getConnectionUri(),
      },
    });

    const tables = await migrationClient.query<{ table_name: string }>(`
      SELECT table_name
      FROM information_schema.tables
      WHERE table_schema = 'public'
        AND table_type = 'BASE TABLE'
      ORDER BY table_name
    `);
    assert.deepEqual(
      tables.rows.map(({ table_name }) => table_name),
      [
        "addendum",
        "appointment",
        "attachment",
        "audit_event",
        "consultation",
        "doctor_account",
        "patient",
        "schedule_block",
        "schedule_calendar",
        "schema_marker",
      ],
    );

    const infrastructureRows = await migrationClient.query<{
      marker_count: string;
      calendar_count: string;
    }>(`
      SELECT
        (SELECT count(*) FROM schema_marker) AS marker_count,
        (SELECT count(*) FROM schedule_calendar) AS calendar_count
    `);
    assert.deepEqual(infrastructureRows.rows[0], {
      marker_count: "1",
      calendar_count: "1",
    });

    const domainRows = await migrationClient.query<{ row_count: string }>(`
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

    const protectedTables = await migrationClient.query<{
      event_object_table: string;
      trigger_name: string;
    }>(`
      SELECT DISTINCT event_object_table, trigger_name
      FROM information_schema.triggers
      WHERE trigger_schema = 'public'
      ORDER BY event_object_table, trigger_name
    `);
    assert.deepEqual(protectedTables.rows, [
      {
        event_object_table: "addendum",
        trigger_name: "addendum_append_only",
      },
      {
        event_object_table: "audit_event",
        trigger_name: "audit_event_append_only",
      },
    ]);

    const runtimePrivileges = await migrationClient.query<{
      can_delete: boolean;
      can_insert: boolean;
      can_select: boolean;
      can_update: boolean;
    }>(`
      SELECT
        has_table_privilege(
          '${runtimeRole}',
          'public.patient',
          'SELECT'
        ) AS can_select,
        has_table_privilege(
          '${runtimeRole}',
          'public.patient',
          'INSERT'
        ) AS can_insert,
        has_table_privilege(
          '${runtimeRole}',
          'public.patient',
          'UPDATE'
        ) AS can_update,
        has_table_privilege(
          '${runtimeRole}',
          'public.patient',
          'DELETE'
        ) AS can_delete
    `);
    assert.deepEqual(runtimePrivileges.rows[0], {
      can_select: true,
      can_insert: true,
      can_update: true,
      can_delete: true,
    });

    const runtimeClient = new Client({
      database: container.getDatabase(),
      host: container.getHost(),
      password: runtimePassword,
      port: container.getPort(),
      user: runtimeRole,
    });
    await runtimeClient.connect();
    try {
      await assert.rejects(
        runtimeClient.query("CREATE TABLE forbidden_runtime_ddl (id INTEGER)"),
        /permission denied for schema public/,
      );
    } finally {
      await runtimeClient.end();
    }
  } finally {
    await migrationClient.end().catch(() => undefined);
    await container.stop();
  }
});
