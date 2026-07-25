# 13 — Ler o prontuário e adicionar adendo

**What to build:** permitir que o médico percorra o histórico clínico definitivo de um paciente e acrescente uma correção justificada sem alterar o conteúdo original.

**Blocked by:** 12 — Finalizar a consulta.

**Status:** ready-for-agent

- [ ] O prontuário mostra somente consultas finalizadas, em ordem cronológica estável e com paginação.
- [ ] Filtros por intervalo de datas limitam os resultados mantendo início inclusivo e fim exclusivo do contrato.
- [ ] Cada consulta exibe seu conteúdo original e os adendos associados sem aparência de edição do registro finalizado.
- [ ] A inclusão de adendo exige conteúdo e justificativa e mostra autor e instante retornados pelo servidor.
- [ ] Adendos não possuem ações de alteração ou remoção.
- [ ] Estado vazio, sessão expirada e erros Problem Details são apresentados sem revelar detalhes internos.
- [ ] Testes de fluxo e um cenário Playwright cobrem leitura cronológica e inclusão imutável de adendo.
