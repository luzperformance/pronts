# 09 — Reagendar e transicionar agendamento

**What to build:** permitir que o médico altere o horário de compromissos ativos e registre confirmação, realização, cancelamento ou não comparecimento conforme o estado atual.

**Blocked by:** 08 — Visualizar a agenda e agendar consulta.

**Status:** ready-for-agent

- [ ] Somente compromissos agendados ou confirmados oferecem a ação de reagendamento.
- [ ] O reagendamento reutiliza as durações permitidas e apresenta conflitos de horário ou bloqueio retornados pela API.
- [ ] A interface oferece apenas transições válidas para o estado atual.
- [ ] Estados realizados, cancelados e de não comparecimento são apresentados como terminais e não oferecem reabertura.
- [ ] Conflito de versão provoca recarregamento seguro do agendamento, sem esconder a alteração concorrente.
- [ ] A agenda reflete imediatamente o novo horário ou estado confirmado pelo servidor.
- [ ] Testes de fluxo cobrem reagendamento, transições válidas, conflito e impossibilidade de reabrir estado terminal.
