# Gate manual de schema no Neon production

Este procedimento promove **somente o schema** para a única branch permanente
`production`. Não cria branch de desenvolvimento, staging ou preview por pull
request e não importa banco, dump, volume, arquivo ou dado de PostgreSQL local,
Docker ou Testcontainers.

O ticket #5 executa apenas a validação e o ensaio descartável descritos na
primeira parte deste documento. As etapas que acessam `production` ficam
preparadas para um corte futuro expressamente autorizado. Não execute essas
etapas no Neon para concluir este ticket.

## Responsabilidades e segredos

Há três credenciais distintas:

| Credencial | Uso permitido | Destino proibido |
| --- | --- | --- |
| proprietária do banco | criar as duas roles e conceder os privilégios iniciais em uma sessão manual | aplicação, Kubernetes, imagem, Git e automação |
| `primeiro_prontuario_migration` | variável `DATABASE_URL` de um único `npm run migrate` manual | Spring, Kubernetes, imagem, Git e execução automática |
| `primeiro_prontuario_runtime` | `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` do Spring após o gate | migração e frontend |

As duas roles limitadas são criadas por SQL, fora de `database/drizzle/`.
Criá-las pelo Console, CLI ou API do Neon é proibido neste procedimento, pois
esses caminhos concedem associação a `neon_superuser`. Usuários proprietários,
senhas, URLs e connection strings reais não podem aparecer em arquivo
versionado, evidência, log, issue, comentário ou histórico de shell.

## Gate 1 — validar e ensaiar sem Neon

Parta do commit candidato ao corte. Os artefatos em `database/drizzle/` devem
estar sem alteração local e não podem ser regenerados entre esta validação e a
migração manual:

```bash
git diff --exit-code -- database/drizzle database/package.json \
  database/package-lock.json
git rev-parse HEAD
sha256sum database/drizzle/0000_baseline.sql \
  database/drizzle/meta/_journal.json \
  database/package-lock.json
```

Registre somente o commit e os três resumos SHA-256 no gate. Depois execute:

```bash
cd database
npm ci
npm run typecheck
sg docker -c 'npm run rehearse:cutover'
cd ..
sg docker -c './mvnw verify'
```

`rehearse:cutover` sempre cria seu próprio PostgreSQL 18 descartável. O comando
primeiro compara Flyway V1–V16 e o baseline Drizzle em bancos independentes;
depois cria outro banco vazio, provisiona roles efêmeras fora da migração,
aplica o mesmo baseline versionado com a role de migração e acessa o resultado
com a role de runtime. O ensaio falha se houver histórico anterior, se o
artefato mudar durante a execução ou se aparecer qualquer conta de médico ou
dado clínico. Ele não aceita URL externa e não executa importação.

O gate permanece fechado se qualquer comando falhar. Não use `repair`,
`baseline`, geração Hibernate, alteração manual do baseline ou exclusão de
objetos para forçar um resultado verde.

## Gate 2 — decisão humana para o corte futuro

Antes de abrir qualquer conexão real, o operador responsável deve conferir:

- [ ] o resultado integral do Gate 1 está verde no mesmo commit;
- [ ] os resumos SHA-256 conferem com o artefato candidato;
- [ ] o Console mostra somente a branch permanente `production` como alvo;
- [ ] o banco de destino é PostgreSQL 18 e deve estar vazio;
- [ ] não há tarefa de importação, restauração ou cópia de dados no corte;
- [ ] nenhum deploy Spring está em andamento;
- [ ] nenhuma action, hook ou pipeline executará migração após merge;
- [ ] o responsável pelo corte autorizou continuar.

Branch e banco são conferidos visualmente no Console porque o nome da branch
Neon não é exposto pela sessão PostgreSQL. No modal **Connect**, selecione
`production`, a role e o banco corretos, desligue **Connection pooling** e use
uma URL com TLS. O host direto não contém `-pooler`. Migração de schema não deve
usar PgBouncer.

Sem todos os itens confirmados, encerre sem criar roles, migrar ou implantar.

## Gate 3 — pré-condição e criação manual das roles

Esta etapa é reservada ao corte futuro autorizado. Abra uma sessão `psql`
direta com a credencial proprietária sem escrever a URL no histórico:

```bash
read -rsp 'URL direta da role proprietária: ' CUTOVER_OWNER_URL
printf '\n'
psql "$CUTOVER_OWNER_URL" -X --set ON_ERROR_STOP=1
```

Na sessão, confirme o alvo vazio antes de qualquer mutação:

```sql
SELECT current_database(), current_user,
       current_setting('server_version_num')::integer / 10000
         AS postgres_major;

SELECT count(*) AS public_relations
FROM pg_catalog.pg_class AS relation
JOIN pg_catalog.pg_namespace AS namespace
  ON namespace.oid = relation.relnamespace
WHERE namespace.nspname = 'public'
  AND relation.relkind IN ('r', 'p', 'S', 'v', 'm', 'f');

SELECT to_regclass('drizzle.__drizzle_migrations')
         AS drizzle_history;

SELECT rolname
FROM pg_catalog.pg_roles
WHERE rolname IN (
  'primeiro_prontuario_migration',
  'primeiro_prontuario_runtime'
);
```

O resultado obrigatório é PostgreSQL `18`, `public_relations = 0`,
`drizzle_history` nulo e nenhuma das duas roles existente. Qualquer outro
resultado fecha o gate. Saia com `\quit`, não apague nem adapte o alvo.

Com a pré-condição verde, crie as roles e os grants em uma única transação.
As roles nascem sem senha; `\password` solicita os valores sem colocá-los no
texto SQL, no histórico do `psql` ou no log do servidor:

```sql
SELECT current_database() AS database_name \gset

BEGIN;

CREATE ROLE primeiro_prontuario_migration
LOGIN PASSWORD NULL
NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

CREATE ROLE primeiro_prontuario_runtime
LOGIN PASSWORD NULL
NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

REVOKE CONNECT, TEMPORARY
ON DATABASE :"database_name"
FROM PUBLIC;

GRANT CONNECT, CREATE
ON DATABASE :"database_name"
TO primeiro_prontuario_migration;

GRANT CONNECT
ON DATABASE :"database_name"
TO primeiro_prontuario_runtime;

GRANT USAGE, CREATE
ON SCHEMA public
TO primeiro_prontuario_migration;

COMMIT;

\password primeiro_prontuario_migration
\password primeiro_prontuario_runtime
```

Verifique que nenhuma role limitada herdou administração:

```sql
SELECT
  rolname,
  rolsuper,
  rolcreatedb,
  rolcreaterole,
  rolreplication,
  rolbypassrls,
  pg_has_role(rolname, 'neon_superuser', 'MEMBER')
    AS member_of_neon_superuser
FROM pg_catalog.pg_roles
WHERE rolname IN (
  'primeiro_prontuario_migration',
  'primeiro_prontuario_runtime'
)
ORDER BY rolname;
```

Todos os booleanos devem ser `false`. Encerre a sessão com `\quit`. De volta à
shell, remova imediatamente a URL proprietária:

```bash
unset CUTOVER_OWNER_URL
```

As duas senhas ficam somente no gerenciador de segredos aprovado. Monte
manualmente duas URLs diretas distintas para o mesmo endpoint, banco e branch.
Não crie `.env` nem arquivo de credenciais de migração.

## Gate 4 — aplicar exatamente o SQL validado

Esta etapa é reservada ao corte futuro autorizado. No mesmo checkout e sem
executar `npm run generate`, repita `git rev-parse` e `sha256sum` do Gate 1.
Interrompa se um único valor divergir.

Forneça a URL da role de migração somente ao processo manual:

```bash
cd database
read -rsp 'URL direta da role de migração: ' CUTOVER_MIGRATION_URL
printf '\n'
case "$CUTOVER_MIGRATION_URL" in
  *-pooler*)
    echo 'Gate fechado: a migração exige endpoint direto.' >&2
    unset CUTOVER_MIGRATION_URL
    exit 1
    ;;
  *sslmode=require*|*sslmode=verify-full*) ;;
  *)
    echo 'Gate fechado: a URL deve exigir TLS.' >&2
    unset CUTOVER_MIGRATION_URL
    exit 1
    ;;
esac
DATABASE_URL="$CUTOVER_MIGRATION_URL" npm run migrate
CUTOVER_MIGRATION_STATUS=$?
unset CUTOVER_MIGRATION_URL
if [ "$CUTOVER_MIGRATION_STATUS" -ne 0 ]; then
  echo 'Gate fechado: a migração falhou; o deploy continua proibido.' >&2
  exit "$CUTOVER_MIGRATION_STATUS"
fi
unset CUTOVER_MIGRATION_STATUS
cd ..
```

Esse é o único comando que recebe a credencial de migração. O Spring permanece
parado e não recebe essa URL. Merge, startup, GitHub Actions, Kubernetes e
qualquer outro mecanismo automático não podem executar a migração.

## Gate 5 — verificar sucesso e liberar o Spring

Use agora somente a URL direta da role de runtime:

```bash
read -rsp 'URL direta da role de runtime: ' CUTOVER_RUNTIME_URL
printf '\n'
psql "$CUTOVER_RUNTIME_URL" -X --set ON_ERROR_STOP=1
```

Na sessão, confirme identidade, privilégios, objetos e ausência de dados:

```sql
SELECT current_user, current_database(),
       current_setting('server_version_num')::integer / 10000
         AS postgres_major;

SELECT
  has_database_privilege(current_user, current_database(), 'CONNECT')
    AS can_connect,
  has_database_privilege(current_user, current_database(), 'TEMPORARY')
    AS can_create_temporary,
  has_schema_privilege(current_user, 'public', 'USAGE')
    AS can_use_schema,
  has_schema_privilege(current_user, 'public', 'CREATE')
    AS can_create_schema_objects;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

SELECT
  (SELECT count(*) FROM schema_marker) AS marker_count,
  (SELECT count(*) FROM schedule_calendar) AS calendar_count,
  (
    (SELECT count(*) FROM doctor_account)
    + (SELECT count(*) FROM patient)
    + (SELECT count(*) FROM appointment)
    + (SELECT count(*) FROM schedule_block)
    + (SELECT count(*) FROM consultation)
    + (SELECT count(*) FROM addendum)
    + (SELECT count(*) FROM attachment)
    + (SELECT count(*) FROM audit_event)
  ) AS domain_row_count;
```

O resultado obrigatório é:

- identidade `primeiro_prontuario_runtime`, banco e PostgreSQL 18 corretos;
- `can_connect` e `can_use_schema` verdadeiros;
- `can_create_temporary` e `can_create_schema_objects` falsos;
- exatamente as dez tabelas públicas versionadas;
- `marker_count = 1`, `calendar_count = 1` e `domain_row_count = 0`.

Encerre a sessão com `\quit`. De volta à shell, limpe a variável:

```bash
unset CUTOVER_RUNTIME_URL
```

Somente após esses resultados o gate humano pode registrar `GO` e liberar o
deploy Spring descrito em
[`deploy-kubernetes-local.md`](deploy-kubernetes-local.md). O cluster recebe
somente a credencial runtime e as credenciais do médico; a credencial
proprietária e a de migração permanecem ausentes.

## Interrupção segura

| Momento da interrupção | Estado seguro e ação |
| --- | --- |
| antes do `COMMIT` das roles | a transação é revertida; feche a sessão e reinicie pela pré-condição |
| depois do `COMMIT`, durante os comandos `\password` | uma ou ambas as roles podem estar sem senha utilizável; não migre, obtenha nova autorização e defina ou rotacione as duas senhas |
| após as roles, antes da migração | o schema continua vazio; preserve as roles, obtenha nova autorização e repita as consultas de alvo vazio e privilégios sem executar novamente os `CREATE ROLE` |
| `npm run migrate` retorna erro | não implante, não importe, não use `repair` e não execute novamente às cegas; preserve a saída sem URL/senha e faça diagnóstico específico |
| migração terminou, verificação falhou | não implante e não faça limpeza automática; preserve o banco para diagnóstico autorizado |
| migração e verificação passaram, deploy não começou | o schema vazio pode aguardar; remova as credenciais da shell e retome o deploy somente com novo `GO` |

Em qualquer falha, o estado padrão é `NO-GO`. Não existe rollback por importação
de dados neste corte.

## Referências operacionais do Neon

- [roles criadas por SQL não recebem `neon_superuser`](https://neon.com/docs/manage/roles#manage-roles-with-sql);
- [migrações de schema devem usar conexão direta](https://neon.com/docs/connect/connection-pooling#when-to-use-pooled-vs-direct-connections);
- [`psql` conecta ao endpoint Neon por TLS](https://neon.com/docs/connect/query-with-psql-editor#connect-to-neon-with-psql).

## Evidência do ensaio do ticket #5

Em 24 de julho de 2026, o ensaio foi executado somente em PostgreSQL 18.4
descartável:

- equivalência Flyway V1–V16 × Drizzle: 2 testes verdes;
- corte em banco vazio, roles externas e ausência de dados importados: 1 teste
  verde;
- `npm run typecheck`: verde;
- `sg docker -c './mvnw verify'`: 203 testes, Spotless e Checkstyle verdes;
- nenhum projeto, branch, role, banco ou dado do Neon foi consultado ou
  modificado.
