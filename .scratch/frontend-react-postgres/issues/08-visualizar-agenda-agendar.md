# 08 — Visualizar a agenda e agendar consulta

**What to build:** permitir que o médico consulte seus compromissos e lembretes, selecione um paciente ativo e crie um agendamento com duração padronizada.

**Blocked by:** 06 — Cadastrar paciente.

**Status:** ready-for-agent

- [ ] A agenda pode ser consultada por período, estado e paciente, mostrando horários na zona configurada.
- [ ] Lembretes exibem somente compromissos agendados ou confirmados das próximas 24 horas.
- [ ] O formulário seleciona apenas paciente ativo e oferece exatamente as durações de 15, 30, 45 e 60 minutos.
- [ ] O término é apresentado como valor derivado, sem campo independente para edição.
- [ ] Horário passado, paciente inativo, sobreposição ou bloqueio retornam mensagens de domínio sem parecer falha interna.
- [ ] O novo agendamento aparece na agenda e nos lembretes quando pertencer à janela.
- [ ] Testes de fluxo cobrem filtros, lembretes, criação válida e conflitos observáveis.
