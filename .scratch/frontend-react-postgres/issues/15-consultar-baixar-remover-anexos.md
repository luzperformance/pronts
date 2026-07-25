# 15 — Consultar, baixar e remover anexos

**What to build:** permitir que o médico confira metadados, baixe conteúdo autenticado e remova um envio indevido mediante justificativa, preservando seu rastro.

**Blocked by:** 14 — Enviar e listar anexos.

**Status:** ready-for-agent

- [ ] O detalhe apresenta somente metadados públicos e nunca expõe o caminho privado do binário.
- [ ] O download autenticado preserva nome e tipo seguros definidos pelo servidor.
- [ ] Markdown é baixado como texto e não é injetado nem renderizado como HTML pela interface.
- [ ] Remoção exige justificativa não vazia e confirmação explícita.
- [ ] Depois da remoção, o conteúdo fica indisponível e a interface diferencia a lápide de um anexo ativo.
- [ ] Nova tentativa de remoção não cria efeitos duplicados nem apaga os metadados preservados.
- [ ] Testes de fluxo e um cenário Playwright cobrem download, remoção e indisponibilidade posterior.
