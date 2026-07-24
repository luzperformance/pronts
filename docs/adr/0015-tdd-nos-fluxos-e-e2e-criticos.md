# TDD nos fluxos e E2E críticos do frontend

O frontend será desenvolvido com TDD em duas fronteiras públicas previamente
acordadas:

- testes de fluxo usam Vitest e React Testing Library para exercitar a aplicação
  renderizada pelo DOM, realizar ações como o usuário e verificar somente
  resultados observáveis na interface;
- testes E2E críticos usam Playwright para exercitar o sistema pelo navegador
  contra Spring Boot e PostgreSQL reais, usando as interfaces públicas da
  aplicação.

Nos testes de fluxo, somente a fronteira HTTP poderá ser simulada. Respostas
simuladas deverão representar exemplos independentes e compatíveis com o
contrato OpenAPI. Componentes, hooks e outros colaboradores internos não serão
mockados.

Cada capacidade será implementada verticalmente em ciclos curtos: um teste
vermelho, a implementação mínima que o torna verde e então o próximo teste.
Não serão escritos lotes antecipados de testes ou funcionalidades especulativas.
Revisão e refatoração ocorrerão depois do ciclo red-green, em etapa separada.

Os testes descreverão comportamento em linguagem do domínio, usarão apenas
interfaces públicas e evitarão snapshots extensos, detalhes de implementação,
asserções tautológicas e consultas diretas ao banco para confirmar resultados.
