# PP-002 — Autenticação e contrato HTTP seguro

## Resultado

Permitir que a conta médica única se autentique por credenciais válidas, receba uma
sessão mantida no servidor e obtenha erros uniformes e rastreáveis em tentativas inválidas.

## Dependências

- PP-001.

## Escopo

- criar `DoctorAccount` e `AuditEvent` no mínimo necessário à autenticação;
- provisionar exatamente uma conta por configuração segura e idempotente, sem
  rota de cadastro;
- armazenar somente resumo criptográfico adaptativo com sal criptográfico aleatório;
- implementar `POST /api/v1/auth/login`;
- renovar o identificador de sessão na autenticação;
- responder credencial inválida de forma genérica;
- auditar sucesso na transação adequada e falha em transação própria, sem senha;
- introduzir uma leitura autenticada mínima de `/api/v1/audit-events`, paginada
  e filtrável por ação, para tornar os eventos observáveis pela fronteira REST;
- introduzir Problem Details, erros por campo e identificador de correlação;
- sanitizar exceções conhecidas e inesperadas;
- preparar CSRF para clientes baseados em cookie sem desativá-lo globalmente.

## Fora do escopo

- encerramento de sessão, `/auth/me`, autorização completa dos módulos e CORS de produção;
- rota de registro, JWT, token de renovação, MFA ou recuperação de senha;
- filtros completos e reforço da auditoria apenas de inserção, concluídos em PP-019.

## Critérios de aceitação

- credenciais válidas criam sessão e retornam identidade mínima;
- credenciais inválidas sempre retornam a mesma mensagem segura;
- senha ausente ou corpo inválido retorna `400` com campos inválidos;
- a resposta nunca contém resumo criptográfico, senha ou indicação de existência do nome de usuário;
- cada resposta de erro contém código de estado, tipo, título, detalhe seguro e identificador
  de correlação;
- sucesso e falha geram `AUTH_LOGIN_SUCCEEDED` ou `AUTH_LOGIN_FAILED`;
- uma falha de autenticação auditada não depende do rollback da tentativa.
- os dois resultados são observáveis pela leitura mínima da auditoria, sem
  carga útil, senha ou conteúdo sensível.

## Estratégia TDD

- fronteira REST para autenticação válida, inválida, validação e rotação de sessão;
- fronteira REST para provar a auditoria somente pela rota pública mínima;
- nenhum duplo de `AuthenticationManager`, repositório ou auditoria.

## Requisitos

RF-001–002, RF-006, RF-054–055, RF-057–059, RF-060, RF-062; RN-001–002,
RN-046, RN-048;
RNF-007, RNF-009–013, RNF-025, RNF-027–028; CA-001, CA-016.
