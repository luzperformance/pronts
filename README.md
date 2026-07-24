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
  --env POSTGRES_USER=primeiro_prontuario_migration \
  --env POSTGRES_PASSWORD=local-demo-password \
  postgres:18.4
```

Em outro terminal, crie a role de runtime e aplique o schema versionado somente
pelo Drizzle:

```bash
docker exec \
  --env PGPASSWORD=local-demo-password \
  primeiro-prontuario-postgres \
  psql --username primeiro_prontuario_migration \
    --dbname primeiro_prontuario \
    --command "CREATE ROLE primeiro_prontuario_runtime LOGIN PASSWORD 'local-runtime-password'"
cd database
npm ci
DATABASE_URL='postgresql://primeiro_prontuario_migration:local-demo-password@localhost:5432/primeiro_prontuario' \
  npm run migrate
cd ..
```

Depois inicie o Spring somente com a role de runtime:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/primeiro_prontuario'
export DB_USERNAME='primeiro_prontuario_runtime'
export DB_PASSWORD='local-runtime-password'
export DOCTOR_USERNAME='doctor'
export DOCTOR_PASSWORD='local-demo-doctor-password'
export SESSION_COOKIE_SECURE='false'
./mvnw spring-boot:run
```

`SESSION_COOKIE_SECURE=false` é permitido somente nesse teste HTTP em loopback.
A demonstração Kubernetes usa HTTPS e mantém o cookie seguro. O Spring nunca
cria nem migra o schema; ele apenas valida pelo Hibernate o schema previamente
aplicado pelo Drizzle.

## Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/primeiro_prontuario` | conexão JDBC |
| `DB_USERNAME` | `primeiro_prontuario` | usuário do banco |
| `DB_PASSWORD` | vazio | senha do banco |
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
- schema e migrações Drizzle:
  [`database/README.md`](database/README.md);
- gate manual de schema no Neon sem importação:
  [`docs/neon-production-cutover.md`](docs/neon-production-cutover.md);
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
