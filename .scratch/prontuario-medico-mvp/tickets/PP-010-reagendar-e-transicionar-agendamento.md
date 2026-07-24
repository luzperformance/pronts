# PP-010 — Reagendar e transicionar agendamento

## Resultado

Permitir reagendar compromissos ativos e registrar seu desfecho por uma máquina
de estados pequena, explícita e irreversível.

## Dependências

- PP-008.

## Escopo

- implementar `PUT /api/v1/appointments/{appointmentId}/schedule`;
- implementar `PATCH /api/v1/appointments/{appointmentId}/status`;
- aplicar bloqueio otimista por versão conhecida;
- repetir no reagendamento todas as regras de paciente, duração, passado,
  sobreposição e trava do calendário;
- permitir somente as transições de RN-020;
- impedir reabertura de estado terminal;
- auditar `APPOINTMENT_RESCHEDULED` e `APPOINTMENT_STATUS_CHANGED`.

## Fora do escopo

- correção administrativa de estado terminal;
- edição genérica do agendamento;
- padrão Estado, barramento de comandos ou histórico de eventos completo.

## Critérios de aceitação

- apenas `SCHEDULED` e `CONFIRMED` podem ser reagendados;
- novo horário conflitante retorna `409` sem alterar o original;
- cada transição permitida funciona e cada transição não prevista retorna `409`;
- `COMPLETED`, `CANCELLED` e `NO_SHOW` não reabrem;
- duas mutações sobre a mesma versão não se sobrescrevem;
- cancelamento libera o horário para novo agendamento;
- mutação e auditoria confirmam ou falham juntas.

## Estratégia TDD

- fronteira de domínio para a tabela completa de transições;
- fronteira REST para reagendamento, conflito, versão e efeitos no calendário;
- um comportamento por ciclo vermelho → verde, sem duplo de componentes internos.

## Requisitos

RF-026–030; RN-014, RN-018–022, RN-046–047, RN-049; RNF-015–016,
RNF-030–031; CA-006.
