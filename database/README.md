# Pacote de banco

Este pacote é o único caminho suportado para declarar e migrar o schema
PostgreSQL. Node e Drizzle não participam da compilação, da imagem ou do processo
runtime da API Spring. O Spring conecta somente com a role de runtime e valida o
schema existente com Hibernate.

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
O gate, o provisionamento manual das roles e a ordem completa do corte estão em
[`../docs/neon-production-cutover.md`](../docs/neon-production-cutover.md).

## Verificar

Os testes exigem Docker. A suíte completa cria roles e bancos PostgreSQL 18
descartáveis, gera e aplica os artefatos Drizzle, valida o corte em banco vazio e
confere a separação de privilégios, sem usar credenciais ou valores reais do
Neon:

```bash
npm test
```

O ensaio focado do gate repete o corte em PostgreSQL 18 vazio, sem aceitar uma
URL externa:

```bash
npm run rehearse:cutover
```

A suíte Spring completa também exige Docker e Node 24. Ela cria roles efêmeras,
aplica os artefatos versionados com `npm run migrate` e executa a API somente
com a role de runtime:

```bash
sg docker -c '../mvnw verify'
```
