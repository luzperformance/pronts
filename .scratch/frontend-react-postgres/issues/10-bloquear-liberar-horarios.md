# 10 — Bloquear e liberar horários

**What to build:** permitir que o médico reserve períodos futuros sem paciente e libere esses períodos quando voltarem a ficar disponíveis.

**Blocked by:** 08 — Visualizar a agenda e agendar consulta.

**Status:** ready-for-agent

- [ ] Bloqueios do período consultado aparecem junto da agenda com distinção clara em relação aos agendamentos.
- [ ] Um novo bloqueio exige início, fim e motivo válidos no futuro.
- [ ] Sobreposição com agendamento ativo ou outro bloqueio apresenta conflito retornado pela API.
- [ ] Intervalos apenas adjacentes continuam aceitos.
- [ ] Remover um bloqueio exige ação explícita e libera o intervalo na visualização atual.
- [ ] Testes de fluxo cobrem listagem, criação, conflito, adjacência e remoção.
