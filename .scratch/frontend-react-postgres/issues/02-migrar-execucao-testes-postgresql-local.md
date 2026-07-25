# 02 — Migrar execução e testes para PostgreSQL local

**What to build:** tornar Spring Boot, testes integrados e implantação acadêmica local executáveis somente com Flyway e PostgreSQL 18, preservando todos os comportamentos já expostos pela API.

**Blocked by:** 01 — Expandir o esquema PostgreSQL para Flyway.

**Status:** ready-for-agent

- [ ] A execução local prepara o esquema com Flyway e inicia o Spring Boot usando uma role de runtime com privilégios mínimos.
- [ ] Os testes REST e de concorrência sobem PostgreSQL 18 via Testcontainers e aplicam as migrações sem depender de Node ou Drizzle.
- [ ] O laboratório Kubernetes local executa PostgreSQL 18 persistente no cluster, acessível apenas internamente e com roles de migração e runtime separadas.
- [ ] Reiniciar a API e o PostgreSQL preserva pacientes, agenda, prontuário, auditoria e anexos conforme o contrato de persistência.
- [ ] A suíte completa confirma que o corte de infraestrutura não alterou os contratos REST nem as regras de negócio.
- [ ] Nenhum caminho ativo de execução, teste ou implantação depende de serviço gerenciado ou conexão Neon.
