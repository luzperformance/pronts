# PP-003 — Ciclo completo da sessão

## Resultado

Completar o ciclo autenticado do médico: consultar a identidade atual, obter CSRF,
encerrar a sessão e negar por padrão todo endpoint de negócio anônimo.

## Dependências

- PP-002.

## Escopo

- implementar `GET /api/v1/auth/me`;
- implementar `GET /api/v1/auth/csrf`;
- implementar `POST /api/v1/auth/logout`;
- invalidar a sessão no logout e auditar `AUTH_LOGOUT`;
- configurar expiração por inatividade e proteção contra session fixation;
- estabelecer negação padrão para `/api/v1/**`;
- diferenciar `401` sem sessão de `403` autenticado sem permissão;
- manter apenas login, CSRF necessário ao cliente e saúde como exceções anônimas.

## Fora do escopo

- múltiplas roles, pacientes autenticados ou autorização por tenant;
- JWT, refresh token, logout global ou administração de sessões;
- configuração final de origem e cookie de produção, tratada em PP-020.

## Critérios de aceitação

- `/auth/me` retorna o médico da sessão e nunca o hash;
- uma sessão encerrada não volta a acessar recurso protegido;
- logout repetido não recria sessão nem duplica efeitos sensíveis;
- mutação autenticada sem CSRF retorna `403`;
- endpoint protegido sem sessão retorna `401` em Problem Details;
- logout gera auditoria sem conteúdo sensível;
- saúde continua anônima e sem detalhes.

## Estratégia TDD

- seam REST com cliente que preserva cookies;
- casos de login → me → logout → acesso negado;
- caso separado para CSRF ausente e válido;
- nenhum teste isolado do filtro ou da configuração Spring Security.

## Requisitos

RF-003–005; RN-001–002; RNF-007–010, RNF-013, RNF-027; CA-001.
