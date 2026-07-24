# PP-002 — Login e contrato HTTP seguro

## Resultado

Permitir que a conta médica única faça login por credenciais válidas, receba uma
sessão server-side e obtenha erros uniformes e rastreáveis em tentativas inválidas.

## Dependências

- PP-001.

## Escopo

- criar `DoctorAccount` e `AuditEvent` no mínimo necessário ao login;
- provisionar exatamente uma conta por configuração segura e idempotente, sem
  endpoint de cadastro;
- armazenar somente hash adaptativo com salt;
- implementar `POST /api/v1/auth/login`;
- renovar o identificador de sessão no login;
- responder credencial inválida de forma genérica;
- auditar sucesso na transação adequada e falha em transação própria, sem senha;
- introduzir uma leitura autenticada mínima de `/api/v1/audit-events`, paginada
  e filtrável por ação, para tornar os eventos observáveis pelo seam REST;
- introduzir Problem Details, erros por campo e correlation ID;
- sanitizar exceções conhecidas e inesperadas;
- preparar CSRF para clientes baseados em cookie sem desativá-lo globalmente.

## Fora do escopo

- logout, `/auth/me`, autorização completa dos módulos e CORS de produção;
- endpoint de registro, JWT, refresh token, MFA ou recuperação de senha;
- filtros completos e hardening append-only da auditoria, concluídos em PP-019.

## Critérios de aceitação

- credenciais válidas criam sessão e retornam identidade mínima;
- credenciais inválidas sempre retornam a mesma mensagem segura;
- senha ausente ou corpo inválido retorna `400` com campos inválidos;
- a resposta nunca contém hash, senha ou indicação de existência do username;
- cada resposta de erro contém status, tipo, título, detalhe seguro e correlation
  ID;
- sucesso e falha geram `AUTH_LOGIN_SUCCEEDED` ou `AUTH_LOGIN_FAILED`;
- uma falha de autenticação auditada não depende do rollback da tentativa.
- os dois resultados são observáveis pela leitura mínima da auditoria, sem
  payload, senha ou conteúdo sensível.

## Estratégia TDD

- seam REST para login válido, inválido, validação e rotação de sessão;
- seam REST para provar a auditoria somente pela rota pública mínima;
- nenhum mock de `AuthenticationManager`, repository ou auditoria.

## Requisitos

RF-001–002, RF-006, RF-054–055, RF-057–059, RF-060, RF-062; RN-001–002,
RN-046, RN-048;
RNF-007, RNF-009–013, RNF-025, RNF-027–028; CA-001, CA-016.
