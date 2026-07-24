# PP-009 — Administrar bloqueios

## Resultado

Permitir reservar e liberar intervalos futuros sem paciente, integrando bloqueios
à mesma garantia transacional do calendário.

## Dependências

- PP-008.

## Escopo

- criar `ScheduleBlock` e sua migration;
- implementar `POST /api/v1/schedule-blocks`;
- implementar `GET /api/v1/schedule-blocks` por período;
- implementar `DELETE /api/v1/schedule-blocks/{blockId}`;
- exigir intervalo futuro válido e justificativa;
- usar a trava do calendário antes de verificar e gravar;
- rejeitar conflito bloqueio × agendamento e bloqueio × bloqueio;
- fazer novos agendamentos consultarem bloqueios;
- permitir remoção apenas do bloqueio futuro;
- auditar criação e remoção na mesma transação.

## Fora do escopo

- bloqueio recorrente, dia inteiro ou associado a clínica;
- edição de bloqueio;
- remoção de agendamento por `DELETE`.

## Critérios de aceitação

- bloqueio livre é criado, listado e passa a impedir agendamento;
- bloqueio sobre consulta ativa ou outro bloqueio retorna `409`;
- agendamento sobre bloqueio retorna `409`;
- intervalos adjacentes continuam válidos;
- remoção futura libera o intervalo;
- mutações concorrentes não criam sobreposição;
- criação e remoção geram eventos de auditoria atômicos.

## Estratégia TDD

- seam REST para todo o fluxo bloqueio → conflito → remoção → agendamento;
- caso concorrente usa PostgreSQL e requisições reais;
- reutilizar o seam de domínio de `TimeInterval`, sem duplicar a regra.

## Requisitos

RF-022, RF-031; RN-015–018, RN-046–047, RN-049; RNF-016, RNF-030–031; CA-007.
