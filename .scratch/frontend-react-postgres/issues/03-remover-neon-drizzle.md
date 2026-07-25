# 03 — Remover integralmente Neon e Drizzle

**What to build:** concluir a contração da migração, deixando como única arquitetura de banco Spring Boot, Flyway e PostgreSQL 18 local, sem artefatos operacionais ou documentação que conduzam ao Neon ou Drizzle.

**Blocked by:** 02 — Migrar execução e testes para PostgreSQL local.

**Status:** ready-for-agent

- [ ] O pacote Node de banco, as migrações Drizzle e seus testes auxiliares são removidos depois de comprovada a adoção do Flyway.
- [ ] Manifestos, exemplos de credenciais, testes de contrato e scripts não contêm caminhos de execução ligados ao Neon.
- [ ] Documentação de execução, implantação, backup, restauração, arquitetura e checklist descreve somente PostgreSQL 18 local.
- [ ] Dependências e arquivos rastreados de primeira parte não contêm referências remanescentes a Neon ou Drizzle.
- [ ] A construção Java, a suíte PostgreSQL/Testcontainers e o laboratório Kubernetes local permanecem verdes após a remoção.
