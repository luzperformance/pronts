# Pacote de banco

Este pacote usa Node e Drizzle somente para declarar e migrar o schema
PostgreSQL. Ele não participa da compilação, da imagem ou do processo runtime da
API Spring.

Durante a etapa de expansão, o Flyway continua sendo o migrador usado pelo
Spring. O baseline Drizzle é um caminho paralelo para bancos vazios; não o
execute sobre um banco já versionado pelo Flyway.

## Pré-requisitos

- Node 24;
- PostgreSQL 18 vazio;
- uma conexão direta com uma role de migração capaz de criar objetos;
- a role `primeiro_prontuario_runtime` criada externamente, sem senha ou segredo
  em arquivos versionados.

A criação das roles e a concessão de acesso ao banco não pertencem à migração.
O baseline concede à role runtime apenas uso do schema e DML sobre tabelas e
sequences atuais e futuras. Ele não cria contas PostgreSQL, a conta do médico
nem dados clínicos.

## Gerar

O schema declarativo fica em `src/schema.ts`. Gerar uma próxima migração não
precisa de conexão:

```bash
npm ci
npm run generate
```

Objetos não representados pelo schema declarativo, como funções, triggers,
linhas estruturais e privilégios, devem continuar em SQL explícito versionado.

## Migrar

Forneça a conexão direta exclusivamente ao comando:

```bash
DATABASE_URL='postgresql://migration-role:password@host:5432/database?sslmode=require' \
  npm run migrate
```

Não salve a URL em `.env`, configuração do Spring ou artefatos do repositório.

## Verificar

Os testes exigem Docker. A suíte completa cria roles e bancos PostgreSQL 18
descartáveis, sem usar credenciais ou valores reais do Neon:

```bash
npm test
```

A prova focada de equivalência pode ser executada separadamente:

```bash
npm run verify:equivalence
```

Ela prepara dois PostgreSQL 18 independentes. Um recebe as migrações Flyway
V1–V16 pelo Flyway 12.4.0; o outro recebe o baseline Drizzle. A comparação
falha por objeto divergente e cobre relações, colunas, tipos, nulabilidade,
defaults, constraints, índices, sequences, funções, triggers e políticas.

A mesma prova cria as roles de bootstrap, migration e runtime apenas no arranjo
descartável. Ela confirma que migration não é superusuária nem cria roles, mas
consegue criar e alterar objetos. A runtime conecta, usa o schema, executa DML
e usa sequences atuais ou futuras, mas recebe `42501` ao tentar DDL persistente
ou temporário ou assumir a role de migration.
