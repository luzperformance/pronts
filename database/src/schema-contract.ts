import type pg from "pg";

type SchemaClient = Pick<pg.Client, "query">;

export type SchemaContractEntry = {
  category: string;
  definition: string;
  identity: string;
};

export type SchemaContract = SchemaContractEntry[];

type ContractRow = {
  category: string;
  definition: string;
  identity: string;
};

const schemaContractQuery = `
  WITH application_relations AS (
    SELECT relation.oid, relation.relname
    FROM pg_catalog.pg_class AS relation
    JOIN pg_catalog.pg_namespace AS namespace
      ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname = 'public'
      AND relation.relname <> 'flyway_schema_history'
      AND relation.relkind IN ('r', 'p', 'v', 'm', 'f')
  ),
  contract AS (
    SELECT
      'relation'::text AS category,
      relation.relname::text AS identity,
      json_build_object(
        'kind', relation.relkind,
        'persistence', relation.relpersistence,
        'row_security', relation.relrowsecurity,
        'force_row_security', relation.relforcerowsecurity
      )::text AS definition
    FROM pg_catalog.pg_class AS relation
    JOIN application_relations
      ON application_relations.oid = relation.oid

    UNION ALL

    SELECT
      'column',
      relation.relname || '.' || attribute.attnum || ':' || attribute.attname,
      json_build_object(
        'type', pg_catalog.format_type(
          attribute.atttypid,
          attribute.atttypmod
        ),
        'not_null', attribute.attnotnull,
        'default', pg_catalog.pg_get_expr(
          attribute_default.adbin,
          attribute_default.adrelid,
          true
        ),
        'identity', attribute.attidentity,
        'generated', attribute.attgenerated,
        'collation', CASE
          WHEN attribute.attcollation = attribute_type.typcollation THEN NULL
          ELSE attribute_collation.collname
        END
      )::text
    FROM application_relations AS relation
    JOIN pg_catalog.pg_attribute AS attribute
      ON attribute.attrelid = relation.oid
    JOIN pg_catalog.pg_type AS attribute_type
      ON attribute_type.oid = attribute.atttypid
    LEFT JOIN pg_catalog.pg_attrdef AS attribute_default
      ON attribute_default.adrelid = attribute.attrelid
      AND attribute_default.adnum = attribute.attnum
    LEFT JOIN pg_catalog.pg_collation AS attribute_collation
      ON attribute_collation.oid = attribute.attcollation
    WHERE attribute.attnum > 0
      AND NOT attribute.attisdropped

    UNION ALL

    SELECT
      'constraint',
      relation.relname || '.' || constraint_definition.conname,
      json_build_object(
        'type', constraint_definition.contype,
        'definition', pg_catalog.pg_get_constraintdef(
          constraint_definition.oid,
          true
        ),
        'deferrable', constraint_definition.condeferrable,
        'initially_deferred', constraint_definition.condeferred,
        'validated', constraint_definition.convalidated
      )::text
    FROM application_relations AS relation
    JOIN pg_catalog.pg_constraint AS constraint_definition
      ON constraint_definition.conrelid = relation.oid

    UNION ALL

    SELECT
      'index',
      relation.relname || '.' || index_relation.relname,
      json_build_object(
        'definition', pg_catalog.pg_get_indexdef(
          index_definition.indexrelid,
          0,
          true
        ),
        'unique', index_definition.indisunique,
        'primary', index_definition.indisprimary,
        'valid', index_definition.indisvalid
      )::text
    FROM application_relations AS relation
    JOIN pg_catalog.pg_index AS index_definition
      ON index_definition.indrelid = relation.oid
    JOIN pg_catalog.pg_class AS index_relation
      ON index_relation.oid = index_definition.indexrelid

    UNION ALL

    SELECT
      'sequence',
      sequence_relation.relname,
      json_build_object(
        'data_type', pg_catalog.format_type(
          sequence_definition.seqtypid,
          NULL
        ),
        'start', sequence_definition.seqstart,
        'increment', sequence_definition.seqincrement,
        'minimum', sequence_definition.seqmin,
        'maximum', sequence_definition.seqmax,
        'cache', sequence_definition.seqcache,
        'cycle', sequence_definition.seqcycle
      )::text
    FROM pg_catalog.pg_sequence AS sequence_definition
    JOIN pg_catalog.pg_class AS sequence_relation
      ON sequence_relation.oid = sequence_definition.seqrelid
    JOIN pg_catalog.pg_namespace AS namespace
      ON namespace.oid = sequence_relation.relnamespace
    WHERE namespace.nspname = 'public'

    UNION ALL

    SELECT
      'function',
      procedure.proname || '('
        || pg_catalog.pg_get_function_identity_arguments(procedure.oid)
        || ')',
      json_build_object(
        'kind', procedure.prokind,
        'language', language.lanname,
        'result', pg_catalog.pg_get_function_result(procedure.oid),
        'security_definer', procedure.prosecdef,
        'volatility', procedure.provolatile,
        'parallel', procedure.proparallel,
        'definition', pg_catalog.pg_get_functiondef(procedure.oid)
      )::text
    FROM pg_catalog.pg_proc AS procedure
    JOIN pg_catalog.pg_namespace AS namespace
      ON namespace.oid = procedure.pronamespace
    JOIN pg_catalog.pg_language AS language
      ON language.oid = procedure.prolang
    WHERE namespace.nspname = 'public'

    UNION ALL

    SELECT
      'trigger',
      relation.relname || '.' || trigger.tgname,
      json_build_object(
        'definition', pg_catalog.pg_get_triggerdef(trigger.oid, true),
        'enabled', trigger.tgenabled
      )::text
    FROM application_relations AS relation
    JOIN pg_catalog.pg_trigger AS trigger
      ON trigger.tgrelid = relation.oid
    WHERE NOT trigger.tgisinternal

    UNION ALL

    SELECT
      'policy',
      relation.relname || '.' || policy.polname,
      json_build_object(
        'command', policy.polcmd,
        'permissive', policy.polpermissive,
        'roles', policy.polroles,
        'using', pg_catalog.pg_get_expr(
          policy.polqual,
          policy.polrelid,
          true
        ),
        'check', pg_catalog.pg_get_expr(
          policy.polwithcheck,
          policy.polrelid,
          true
        )
      )::text
    FROM application_relations AS relation
    JOIN pg_catalog.pg_policy AS policy
      ON policy.polrelid = relation.oid
  )
  SELECT category, identity, definition
  FROM contract
  ORDER BY category, identity
`;

export async function captureSchemaContract(
  client: SchemaClient,
): Promise<SchemaContract> {
  const result = await client.query<ContractRow>(schemaContractQuery);
  return result.rows;
}

export function compareSchemaContracts(
  flywayContract: SchemaContract,
  drizzleContract: SchemaContract,
): string[] {
  const flywayEntries = indexContract(flywayContract);
  const drizzleEntries = indexContract(drizzleContract);
  const identities = new Set([
    ...flywayEntries.keys(),
    ...drizzleEntries.keys(),
  ]);
  const differences: string[] = [];

  for (const identity of [...identities].sort()) {
    const flywayDefinition = flywayEntries.get(identity);
    const drizzleDefinition = drizzleEntries.get(identity);

    if (flywayDefinition === undefined) {
      differences.push(
        `Only Drizzle contains ${identity}: ${drizzleDefinition}`,
      );
    } else if (drizzleDefinition === undefined) {
      differences.push(
        `Drizzle is missing ${identity}: ${flywayDefinition}`,
      );
    } else if (flywayDefinition !== drizzleDefinition) {
      differences.push(
        [
          `Different definition for ${identity}`,
          `Flyway: ${flywayDefinition}`,
          `Drizzle: ${drizzleDefinition}`,
        ].join("\n"),
      );
    }
  }

  return differences;
}

function indexContract(contract: SchemaContract): Map<string, string> {
  return new Map(
    contract.map(({ category, definition, identity }) => [
      `${category}:${identity}`,
      definition,
    ]),
  );
}
