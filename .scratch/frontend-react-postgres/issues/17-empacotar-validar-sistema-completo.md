# 17 — Empacotar e validar o sistema completo

**What to build:** entregar uma demonstração acadêmica única em que o navegador acessa o frontend servido pelo Spring Boot, os fluxos usam PostgreSQL 18 local e toda a jornada principal pode ser validada sem Neon.

**Blocked by:** 03 — Remover integralmente Neon e Drizzle; 07 — Atualizar e alterar o estado do paciente; 09 — Reagendar e transicionar agendamento; 10 — Bloquear e liberar horários; 13 — Ler o prontuário e adicionar adendo; 15 — Consultar, baixar e remover anexos; 16 — Consultar a auditoria.

**Status:** ready-for-agent

- [ ] A construção reproduzível gera o frontend e o inclui no artefato Spring sem adicionar Node ao processo ou à imagem final de execução.
- [ ] Rotas da aplicação React funcionam após acesso direto e recarregamento, enquanto API, saúde e recursos estáticos mantêm suas regras de segurança.
- [ ] A implantação Kubernetes local executa uma imagem da aplicação, PostgreSQL 18 e volumes privados, todos atrás do Traefik com TLS local.
- [ ] Um percurso Playwright real cobre login, paciente, agenda, rascunho, finalização, prontuário, adendo, anexo, auditoria e logout.
- [ ] O percurso utiliza Spring Boot e PostgreSQL/Testcontainers ou PostgreSQL Kubernetes reais, sem simular API, sessão, CSRF ou persistência.
- [ ] Testes de fluxo, E2E, construção Java, análise estática, formatação e verificação do contrato OpenAPI ficam verdes no mesmo checkout.
- [ ] Documentação em português explica desenvolvimento, construção, execução, implantação, backup e restauração usando apenas PostgreSQL local.
- [ ] Arquivos rastreados e artefatos ativos não contêm configuração, dependência ou instrução de uso do Neon.
