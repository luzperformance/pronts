# PP-003 — Ciclo completo da sessão

## Resultado

Completar o ciclo autenticado do médico: consultar a identidade atual, obter CSRF,
encerrar a sessão e negar por padrão toda rota de negócio anônima.

## Dependências

- PP-002.

## Escopo

- implementar `GET /api/v1/auth/me`;
- implementar `GET /api/v1/auth/csrf`;
- implementar `POST /api/v1/auth/logout`;
- invalidar a sessão no encerramento e auditar `AUTH_LOGOUT`;
- configurar expiração por inatividade e proteção contra fixação de sessão;
- estabelecer negação padrão para `/api/v1/**`;
- diferenciar `401` sem sessão de `403` autenticado sem permissão;
- manter apenas autenticação, CSRF necessário ao cliente e saúde como exceções anônimas.

## Fora do escopo

- múltiplos perfis, pacientes autenticados ou autorização por organização;
- JWT, token de renovação, encerramento global ou administração de sessões;
- configuração final de origem e cookie de produção, tratada em PP-020.

## Critérios de aceitação

- `/auth/me` retorna o médico da sessão e nunca o resumo criptográfico;
- uma sessão encerrada não volta a acessar recurso protegido;
- encerramento repetido não recria sessão nem duplica efeitos sensíveis;
- mutação autenticada sem CSRF retorna `403`;
- rota protegida sem sessão retorna `401` em Problem Details;
- encerramento gera auditoria sem conteúdo sensível;
- saúde continua anônima e sem detalhes.

## Estratégia TDD

- fronteira REST com cliente que preserva cookies;
- casos de autenticação → identidade → encerramento → acesso negado;
- caso separado para CSRF ausente e válido;
- nenhum teste isolado do filtro ou da configuração Spring Security.

## Requisitos

RF-003–005; RN-001–002; RNF-007–010, RNF-013, RNF-027; CA-001.
