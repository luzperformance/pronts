# PP-013 — Finalizar consulta

## Resultado

Finalizar explicitamente um rascunho completo, congelar seu conteúdo e concluir
o agendamento ativo relacionado na mesma transação.

## Dependências

- PP-010;
- PP-012.

## Escopo

- implementar `POST /api/v1/consultations/{consultationId}/finalization`;
- exigir os seis campos clínicos, tratando espaços como ausência;
- registrar autor e instante fornecidos pelo servidor;
- aplicar transição atômica `DRAFT → FINALIZED`;
- tornar qualquer edição posterior impossível pelo domínio e API;
- marcar o agendamento relacionado ativo como `COMPLETED` na mesma transação;
- tornar nova finalização idempotente sem duplicar efeitos;
- auditar `CONSULTATION_FINALIZED` sem conteúdo clínico.

## Fora do escopo

- reabertura, exclusão, edição de consulta finalizada ou versionamento destrutivo;
- adendos e prontuário cronológico;
- evento assíncrono, AOP de auditoria ou padrão Estado.

## Critérios de aceitação

- resposta `400` enumera todos os campos ausentes;
- finalização válida fixa estado, autor e instante;
- segunda finalização não duplica dados ou auditoria;
- `PUT` posterior retorna `409`;
- não existe rota de exclusão da consulta;
- agendamento ativo vinculado se torna `COMPLETED`;
- falha em qualquer parte reverte finalização, conclusão do agendamento e
  auditoria;
- conteúdo clínico não aparece em logs ou eventos.

## Estratégia TDD

- fronteira de domínio para completude, espaços, transição e bloqueio de mutação;
- fronteira REST para atomicidade observável, idempotência e integração com agenda;
- PostgreSQL real, sem duplo de auditoria ou agendamento.

## Requisitos

RF-036–038; RN-027–030, RN-037, RN-046–047, RN-049; RNF-013–016,
RNF-030–031; CA-009.
