# 04 — Autenticar e navegar no frontend React

**What to build:** permitir que o médico abra a aplicação React, autentique-se pela sessão Spring, navegue em uma área protegida e encerre a sessão, tanto no desenvolvimento com Vite quanto no artefato servido pelo Spring Boot.

**Blocked by:** 02 — Migrar execução e testes para PostgreSQL local.

**Status:** ready-for-agent

- [ ] A aplicação React com TypeScript e Vite possui versões fixadas e uma estrutura simples organizada pelos fluxos do produto.
- [ ] Os tipos HTTP são gerados do contrato OpenAPI, sem uma segunda definição manual dos DTOs da API.
- [ ] O cliente HTTP envia cookies, obtém o token CSRF e inclui o cabeçalho informado pelo servidor em toda mutação autenticada.
- [ ] Acesso sem sessão apresenta o login; credenciais válidas abrem a área protegida e credenciais inválidas exibem mensagem genérica.
- [ ] Recarregar a página restaura a identidade pela sessão atual, e sair invalida a sessão e retorna ao login.
- [ ] O Vite encaminha as rotas da API no desenvolvimento; em produção, o Spring serve a aplicação e seus recursos estáticos na mesma origem sem liberar rotas de negócio anonimamente.
- [ ] Testes de fluxo cobrem login, restauração da sessão, falha de autenticação, CSRF e logout pela interface pública.
