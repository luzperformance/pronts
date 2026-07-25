# 01 — Expandir o esquema PostgreSQL para Flyway

**What to build:** disponibilizar, em paralelo ao caminho atual, uma fonte Flyway capaz de criar em PostgreSQL 18 o mesmo esquema e as mesmas proteções esperadas pela API, preparando a migração sem interromper o sistema existente.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Um PostgreSQL 18 vazio pode receber todas as migrações Flyway em ordem e ser validado pelo Hibernate sem criação automática de tabelas.
- [ ] Tabelas, colunas, chaves, restrições, índices, dados técnicos e proteções de auditoria são equivalentes ao esquema atualmente aceito pela suíte.
- [ ] Um teste integrado demonstra a equivalência usando PostgreSQL real, sem H2 nem simulação do banco.
- [ ] O caminho Drizzle existente continua funcional nesta etapa de expansão, mantendo o conjunto anterior de testes verde.
- [ ] Nenhum contrato REST ou comportamento de negócio é alterado por este ticket.
