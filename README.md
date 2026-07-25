# Primeiro Prontuário

MVP acadêmico de prontuário para um único médico, implementado como API REST
Spring Boot com PostgreSQL 18. O projeto não possui interface web.

> Use somente dados fictícios ou previamente anonimizados. Este MVP não é
> certificado para uso clínico, regulatório ou com dados pessoais reais.

## Começar pela cópia de trabalho

Pré-requisitos para construção e testes:

- JDK 21;
- Node 24;
- Docker acessível pelo usuário, usado por Testcontainers;
- Bash e acesso à internet para a primeira resolução do Maven Wrapper.

O Wrapper baixa a versão fixada do Maven e `verify` executa compilação, testes
com PostgreSQL 18 real, Checkstyle e verificação de formatação:

```bash
./mvnw verify
```

Se o socket Docker estiver disponível somente ao grupo `docker` nesta sessão:

```bash
sg docker -c './mvnw verify'
```

Para apenas aplicar a formatação Java configurada:

```bash
./mvnw spotless:apply
```

## Executar a API localmente

Para uma execução HTTP descartável fora do Kubernetes, inicie um PostgreSQL 18
local com valores exclusivamente fictícios:

```bash
docker run --rm --name primeiro-prontuario-postgres \
  --publish 127.0.0.1:5432:5432 \
  --env POSTGRES_DB=primeiro_prontuario \
  --env POSTGRES_USER=primeiro_prontuario_admin \
  --env POSTGRES_PASSWORD=local-bootstrap-password \
  postgres:18.4
```

Em outro terminal, crie as roles distintas. A role de migração recebe DDL e
privilégios padrão sobre os objetos que criar; a role de runtime recebe somente
uso do schema e DML:

```bash
docker exec \
  --env PGPASSWORD=local-bootstrap-password \
  primeiro-prontuario-postgres \
  psql --username primeiro_prontuario_admin \
    --dbname primeiro_prontuario \
    --command "CREATE ROLE primeiro_prontuario_migration LOGIN PASSWORD 'local-migration-password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS" \
    --command "CREATE ROLE primeiro_prontuario_runtime LOGIN PASSWORD 'local-runtime-password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS" \
    --command "REVOKE CONNECT, TEMPORARY ON DATABASE primeiro_prontuario FROM PUBLIC" \
    --command "GRANT CONNECT, CREATE ON DATABASE primeiro_prontuario TO primeiro_prontuario_migration" \
    --command "GRANT CONNECT ON DATABASE primeiro_prontuario TO primeiro_prontuario_runtime" \
    --command "REVOKE CREATE ON SCHEMA public FROM PUBLIC" \
    --command "GRANT USAGE, CREATE ON SCHEMA public TO primeiro_prontuario_migration" \
    --command "GRANT USAGE ON SCHEMA public TO primeiro_prontuario_runtime" \
    --command "ALTER DEFAULT PRIVILEGES FOR ROLE primeiro_prontuario_migration IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO primeiro_prontuario_runtime" \
    --command "ALTER DEFAULT PRIVILEGES FOR ROLE primeiro_prontuario_migration IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO primeiro_prontuario_runtime"
```

Depois inicie o Spring. O Flyway usa a role de migração e aplica V1–V16; JPA e
todo o runtime da API usam a role restrita:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/primeiro_prontuario'
export DB_USERNAME='primeiro_prontuario_runtime'
export DB_PASSWORD='local-runtime-password'
export MIGRATION_DB_URL="$DB_URL"
export MIGRATION_DB_USERNAME='primeiro_prontuario_migration'
export MIGRATION_DB_PASSWORD='local-migration-password'
export DOCTOR_USERNAME='doctor'
export DOCTOR_PASSWORD='local-demo-doctor-password'
export SESSION_COOKIE_SECURE='false'
./mvnw spring-boot:run
```

`SESSION_COOKIE_SECURE=false` é permitido somente nesse teste HTTP em loopback.
A demonstração Kubernetes usa HTTPS e mantém o cookie seguro. O Flyway prepara
o schema antes de o Hibernate validá-lo; o Hibernate não cria nem altera tabelas.

## Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/primeiro_prontuario` | conexão JDBC |
| `DB_USERNAME` | `primeiro_prontuario_runtime` | role de runtime do banco |
| `DB_PASSWORD` | vazio | senha do banco |
| `MIGRATION_DB_URL` | valor de `DB_URL` | JDBC usado somente pelo Flyway |
| `MIGRATION_DB_USERNAME` | valor de `DB_USERNAME` | role usada somente pelo Flyway |
| `MIGRATION_DB_PASSWORD` | valor de `DB_PASSWORD` | senha usada somente pelo Flyway |
| `DOCTOR_USERNAME` | sem padrão | usuário único provisionado na inicialização |
| `DOCTOR_PASSWORD` | sem padrão | senha do usuário único |
| `APP_TIME_ZONE` | `America/Sao_Paulo` | interpretação das datas locais da agenda |
| `ATTACHMENT_STORAGE_DIRECTORY` | `./data/attachments` | diretório privado dos binários |
| `SESSION_TIMEOUT` | `30m` | duração máxima da sessão |
| `SESSION_COOKIE_SECURE` | `true` | exige HTTPS para o cookie de sessão |
| `APP_CORS_ALLOWED_ORIGIN` | vazio | única origem exata autorizada, se houver |
| `FORWARD_HEADERS_STRATEGY` | `NONE` | confiança em cabeçalhos encaminhados; alterada só no perfil de implantação |
| `SPRING_PROFILES_ACTIVE` | vazio | use `prod` no Kubernetes |
| `TRUSTED_PROXY_NETWORKS` | redes privadas locais | proxies confiáveis no perfil `prod` |

Não grave valores reais em arquivos versionados, histórico de shell, exemplos
ou capturas de tela.

## Documentação verificável

- contrato completo: [`docs/openapi.yaml`](docs/openapi.yaml);
- migrações Flyway executadas pela API:
  [`src/main/resources/db/migration`](src/main/resources/db/migration);
- configuração de execução: [`docs/runtime-configuration.md`](docs/runtime-configuration.md);
- sessão, cookie, CSRF, CORS e exemplos: [`docs/http-api.md`](docs/http-api.md);
- Docker, Kubernetes local, Traefik e TLS:
  [`docs/deploy-kubernetes-local.md`](docs/deploy-kubernetes-local.md);
- laboratório integralmente local, incluindo cluster e PostgreSQL:
  [`docs/deploy-kubernetes-tudo-local.md`](docs/deploy-kubernetes-tudo-local.md);
- cópia de segurança e restauração conjunta:
  [`docs/backup-restore.md`](docs/backup-restore.md);
- decisões e padrões: [`docs/architecture.md`](docs/architecture.md);
- revisão de escopo: [`docs/scope-review.md`](docs/scope-review.md);
- evidências e demonstração: [`docs/final-checklist.md`](docs/final-checklist.md).

## Limites do MVP

Há um único médico e nenhuma autenticação de paciente. O MVP cobre cadastro e pesquisa
de pacientes, agenda e bloqueios, rascunho/finalização/adendo clínico, anexos e
auditoria interna. Não cobre interface web, múltiplos médicos, multitenância, CRM,
comunicação externa, relatórios, prescrição, documentos do paciente, nuvem,
alta disponibilidade, certificação regulatória ou coleta de dados reais.
