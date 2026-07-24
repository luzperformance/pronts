# PP-012 — Criar e editar rascunho clínico

## Resultado

Permitir iniciar, consultar e editar uma consulta clínica incompleta, ainda fora
do prontuário definitivo.

## Dependências

- PP-006;
- PP-007.

## Escopo

- criar `Consultation` com estado inicial `DRAFT` e versionamento otimista;
- criar migração dos campos clínicos e vínculo opcional 0..1 com agendamento;
- implementar `POST /api/v1/patients/{patientId}/consultations`;
- implementar `GET /api/v1/consultations/{consultationId}`;
- implementar `PUT /api/v1/consultations/{consultationId}`;
- aceitar os seis campos clínicos incompletos no rascunho;
- validar paciente ativo na criação;
- validar que agendamento opcional pertence ao mesmo paciente e ainda não está
  vinculado a outra consulta;
- detectar edição concorrente.

## Fora do escopo

- finalização, adendo, exclusão e exibição no prontuário;
- auditoria do salvamento de cada rascunho, não exigida pela especificação;
- vínculo N:N ou troca destrutiva de paciente.

## Critérios de aceitação

- rascunho incompleto é aceito para paciente ativo;
- paciente inativo retorna `409`;
- vínculo ausente é válido;
- vínculo com agendamento de outro paciente ou já utilizado retorna `409`;
- edição incrementa a versão e versão obsoleta retorna `409`;
- consulta permanece `DRAFT` e não aparece no histórico definitivo;
- detalhes devolvem somente os dados necessários, sem entidade JPA serializada.

## Estratégia TDD

- fronteira REST para criar, consultar, editar e verificar concorrência;
- fronteira de domínio somente para invariantes locais de estado e vínculo;
- não usar duplos de agenda, paciente ou repositório.

## Requisitos

RF-033–035, RF-041; RN-010, RN-025–026, RN-036, RN-038, RN-049;
RNF-015–016, RNF-030–031; CA-008.
