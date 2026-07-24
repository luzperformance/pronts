# PP-006 — Atualizar e alterar estado do paciente

## Resultado

Permitir corrigir o cadastro e inativar ou reativar um paciente sem apagar seu
histórico nem aceitar perda silenciosa por concorrência.

## Dependências

- PP-004.

## Escopo

- implementar `PUT /api/v1/patients/{patientId}`;
- implementar `PATCH /api/v1/patients/{patientId}/status`;
- exigir a versão conhecida pelo cliente nas mutações;
- aplicar bloqueio otimista e mapear conflito para `409`;
- manter CPF canônico, válido e único após alteração;
- preservar identificador e relacionamentos;
- auditar `PATIENT_UPDATED` com nomes de campos, nunca valores;
- auditar `PATIENT_STATUS_CHANGED`.

## Fora do escopo

- exclusão de paciente;
- histórico versionado de todos os valores cadastrais;
- correção clínica ou reabertura de qualquer registro.

## Critérios de aceitação

- atualização válida incrementa a versão;
- duas atualizações sobre a mesma versão não se sobrescrevem;
- CPF duplicado na atualização retorna `409`;
- inativação não remove o paciente nem dados vinculados;
- paciente inativo continua consultável e pesquisável;
- reativação retorna o paciente a `ACTIVE`;
- nenhum valor sensível alterado é copiado para auditoria.

## Estratégia TDD

- fronteira REST para atualização, estado e concorrência usando duas representações da
  mesma versão;
- fronteira de domínio apenas para transição de estado se houver regra substancial;
- nenhum duplo de JPA ou teste unitário do serviço de aplicação.

## Requisitos

RF-010–011; RN-005, RN-008–010, RN-046–047, RN-049; RNF-014, RNF-016;
CA-004.
