# Rastreabilidade — especificação para tickets

Esta matriz garante cobertura da especificação aprovada. Um requisito aparecer em
mais de um ticket significa que a primeira fatia introduz a capacidade e a última
fecha sua validação integrada; não autoriza duplicar a regra.

## Requisitos funcionais

| Requisitos | Ticket responsável |
|---|---|
| RF-001–002, RF-006 | PP-002 |
| RF-003–005 | PP-003 |
| RF-007–009 | PP-004 |
| RF-010–011 | PP-006 |
| RF-012–017 | PP-005 |
| RF-018–020, RF-023–025 | PP-007 |
| RF-021 | PP-008 |
| RF-022, RF-031 | PP-009 |
| RF-026–030 | PP-010 |
| RF-032 | PP-011 |
| RF-033–035, RF-041 | PP-012 |
| RF-036–038 | PP-013 |
| RF-039–040 | PP-014 |
| RF-042–044 | PP-015 |
| RF-045–050 | PP-016 |
| RF-051 | PP-017 |
| RF-052 | PP-018 |
| RF-053 | PP-016, PP-017, PP-018 |
| RF-054–058 | PP-002 introduz a trilha mínima; PP-019 completa |
| RF-059–060 | PP-002 introduz; PP-020 verifica em toda a API |
| RF-061 | PP-001 |
| RF-062 | PP-002 e todas as fatias REST |
| RF-063 | PP-022 |

## Requisitos não funcionais

| Requisitos | Ticket responsável |
|---|---|
| RNF-001–006 | PP-001 estabelece; PP-022 revisa |
| RNF-007–010 | PP-002, PP-003, PP-020 |
| RNF-011–012 | PP-002, PP-021, PP-022 |
| RNF-013 | todos os produtores; PP-020 fecha a revisão |
| RNF-014 | PP-002 e PP-019 |
| RNF-015 | PP-013, PP-015, PP-018 |
| RNF-016 | PP-006, PP-008, PP-010, PP-012, PP-020 |
| RNF-017–018 | PP-001 e toda fatia com migração; PP-022 valida |
| RNF-019–022 | PP-016–018; PP-020 revisa limites |
| RNF-023 | PP-005, PP-007, PP-011, PP-014, PP-019, PP-020 |
| RNF-024 | PP-001, PP-005, PP-008, PP-014, PP-022 |
| RNF-025 | PP-007 e PP-011 |
| RNF-026 | PP-004 |
| RNF-027–028 | PP-002 e PP-020 |
| RNF-029 | PP-007, PP-011, PP-016, PP-018 |
| RNF-030–031 | toda fatia de comportamento, PP-004–PP-020 |
| RNF-032–033 | PP-001, PP-020, PP-021, PP-022 |
| RNF-034–035 | PP-021–022 |
| RNF-036–037 | PP-021 |
| RNF-038–039 | PP-020 introduz; PP-021 valida pelo Traefik |
| RNF-040 | PP-021 |

## Regras de negócio

| Regras | Ticket responsável |
|---|---|
| RN-001–002 | PP-002–003 |
| RN-003–006 | PP-004 |
| RN-007 | PP-005 |
| RN-008–009 | PP-006 |
| RN-010 | PP-006, PP-007, PP-012 |
| RN-011–013 | PP-007 |
| RN-014–015 | PP-008 e PP-010 |
| RN-016–018 | PP-008–010 |
| RN-019–022 | PP-010 |
| RN-023–024 | PP-011 |
| RN-025–026 | PP-012 |
| RN-027–030 | PP-013 |
| RN-031–034 | PP-015 |
| RN-035 | PP-014 |
| RN-036, RN-038 | PP-012 |
| RN-037 | PP-013 |
| RN-039–042 | PP-016 |
| RN-043 | PP-017 |
| RN-044–045 | PP-018 |
| RN-046 | todos os produtores de auditoria; PP-019 revisa |
| RN-047 | toda mutação auditada; PP-019 valida rollback |
| RN-048 | PP-002 e PP-019 |
| RN-049 | PP-004, PP-006, PP-008, PP-010, PP-012, PP-013, PP-020 |

## Critérios de aceitação

| Critério | Ticket responsável |
|---|---|
| CA-001 | PP-002–003 e PP-020 |
| CA-002 | PP-004 |
| CA-003 | PP-005 |
| CA-004 | PP-006 |
| CA-005 | PP-007–008 |
| CA-006 | PP-010 |
| CA-007 | PP-009 e PP-011 |
| CA-008 | PP-012 |
| CA-009 | PP-013 |
| CA-010 | PP-014 |
| CA-011 | PP-015 |
| CA-012 | PP-016 |
| CA-013 | PP-017 |
| CA-014 | PP-018 |
| CA-015 | PP-002 introduz; PP-019 completa |
| CA-016 | PP-001, PP-002, PP-020, PP-022 |
| CA-017 | PP-021 e PP-022 |

## Histórias de usuário

| Histórias da seção 9.1 | Ticket responsável |
|---|---|
| 1–2 | PP-002–003 |
| 3 | PP-004 |
| 4–7 | PP-005 |
| 8–9 | PP-006 |
| 10 | PP-007 |
| 11 | PP-008–009 |
| 12 | PP-009 |
| 13–14 | PP-010 |
| 15 | PP-011 |
| 16 | PP-012 |
| 17–18 | PP-013 |
| 19 | PP-015 |
| 20 | PP-014 |
| 21–22 | PP-016 |
| 23 | PP-017 |
| 24 | PP-018 |
| 25 | PP-002, todos os produtores e PP-019 |
| 26 | PP-019 |
| 27–28 | PP-001 e PP-022 |
| 29 | PP-002 e PP-020 |
| 30 | PP-001 e todas as fatias de comportamento |
| 31 | PP-001 e PP-022 |
| 32 | PP-021 |

## Contratos REST por dono

| Contrato | Ticket que o introduz |
|---|---|
| `GET /actuator/health` | PP-001 |
| `POST /api/v1/auth/login` | PP-002 |
| `GET /api/v1/audit-events` | PP-002; filtros completos em PP-019 |
| `GET /api/v1/auth/me` | PP-003 |
| `GET /api/v1/auth/csrf` | PP-003 |
| `POST /api/v1/auth/logout` | PP-003 |
| `POST /api/v1/patients` | PP-004 |
| `GET /api/v1/patients/{patientId}` | PP-004 |
| `GET /api/v1/patients` | PP-005 |
| `PUT /api/v1/patients/{patientId}` | PP-006 |
| `PATCH /api/v1/patients/{patientId}/status` | PP-006 |
| `POST /api/v1/appointments` | PP-007; conflitos reforçados em PP-008–009 |
| `GET /api/v1/appointments` | PP-007 |
| `GET /api/v1/appointments/{appointmentId}` | PP-007 |
| `POST /api/v1/schedule-blocks` | PP-009 |
| `GET /api/v1/schedule-blocks` | PP-009 |
| `DELETE /api/v1/schedule-blocks/{blockId}` | PP-009 |
| `PUT /api/v1/appointments/{appointmentId}/schedule` | PP-010 |
| `PATCH /api/v1/appointments/{appointmentId}/status` | PP-010 |
| `GET /api/v1/appointments/reminders` | PP-011 |
| `POST /api/v1/patients/{patientId}/consultations` | PP-012 |
| `GET /api/v1/consultations/{consultationId}` | PP-012 |
| `PUT /api/v1/consultations/{consultationId}` | PP-012 |
| `POST /api/v1/consultations/{consultationId}/finalization` | PP-013 |
| `GET /api/v1/patients/{patientId}/medical-record` | PP-014 |
| `POST /api/v1/consultations/{consultationId}/addenda` | PP-015 |
| `POST /api/v1/patients/{patientId}/attachments` | PP-016 |
| `GET /api/v1/patients/{patientId}/attachments` | PP-016 |
| `GET /api/v1/attachments/{attachmentId}` | PP-016 |
| `GET /api/v1/attachments/{attachmentId}/content` | PP-017 |
| `DELETE /api/v1/attachments/{attachmentId}` | PP-018 |

## Dependências arquiteturais verificadas na revisão

- `audit` é introduzido antes dos primeiros fluxos auditados e não depende deles;
- `patient` não depende de agenda, prontuário ou anexos;
- `schedule` depende apenas da identidade do paciente necessária ao caso de uso;
- `medicalrecord` usa paciente e o contrato mínimo de agenda apenas nos fluxos de
  vínculo/finalização;
- `attachment` referencia paciente e consulta sem fazer o módulo clínico depender
  de armazenamento;
- `shared` permanece restrito a Problem Details, identificador de correlação e tipos técnicos
  realmente comuns;
- o Traefik é a única entrada a partir da máquina local; API e PostgreSQL
  permanecem internos ao cluster;
- uma réplica da API é uma restrição consciente de sessão e armazenamento do MVP, não
  uma promessa de alta disponibilidade;
- migrações de tickets paralelos devem reservar números antes do início para
  evitar colisão; não se cria migração apenas para reservar um número.
