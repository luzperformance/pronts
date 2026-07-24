# PP-007 — Agendar e consultar agenda

## Resultado

Permitir criar um agendamento para paciente ativo, calcular seu término e
consultar a agenda por identificador e período.

## Dependências

- PP-004.

## Escopo

- criar `Appointment`, sua tabela e o estado inicial `SCHEDULED`;
- implementar `AppointmentDuration` e `TimeInterval` no domínio;
- injetar `Clock` para validar passado e calcular janelas;
- implementar `POST /api/v1/appointments`;
- implementar `GET /api/v1/appointments/{appointmentId}`;
- implementar `GET /api/v1/appointments` por período, estado e paciente;
- derivar `endsAt` de `startsAt` e duração;
- persistir instantes em UTC e interpretar a zona configurada;
- auditar `APPOINTMENT_CREATED`.

## Fora do escopo

- prevenção de sobreposição, entregue em PP-008;
- bloqueios, reagendamento, transições e lembretes;
- duração livre ou término fornecido pelo cliente.

## Critérios de aceitação

- somente 15, 30, 45 ou 60 minutos são aceitos;
- início no passado retorna conflito seguro;
- término é calculado e devolvido corretamente;
- paciente inativo ou inexistente não recebe novo agendamento;
- listagem exige intervalo válido, é paginada e possui ordem determinística;
- consulta individual retorna o estado inicial;
- criação e auditoria são atômicas.

## Estratégia TDD

- seam de domínio para duração, cálculo e fronteiras de intervalo;
- seam REST com `Clock` controlado para passado, UTC e zona configurada;
- não testar chamadas internas ao relógio ou ao repository.

## Requisitos

RF-018–020, RF-023–025; RN-010–013; RNF-025, RNF-029–031; CA-005.
