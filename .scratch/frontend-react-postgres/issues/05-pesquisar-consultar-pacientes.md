# 05 — Pesquisar e consultar pacientes

**What to build:** permitir que o médico encontre pacientes pelos dados disponíveis, percorra resultados paginados e abra o cadastro completo de uma pessoa.

**Blocked by:** 04 — Autenticar e navegar no frontend React.

**Status:** ready-for-agent

- [ ] A interface pesquisa isoladamente e em combinação por nome completo, nome da mãe, CPF, telefone, e-mail e estado.
- [ ] Resultados exibem paginação, ordenação determinística e informação suficiente para distinguir pacientes.
- [ ] Uma busca sem correspondências apresenta estado vazio, sem tratar o resultado como erro.
- [ ] Selecionar um resultado abre o cadastro completo e preserva a versão necessária para futuras alterações.
- [ ] Carregamento, falha de rede, sessão expirada e Problem Details possuem estados observáveis e recuperáveis.
- [ ] Testes de fluxo exercitam filtros, paginação, resultado vazio e abertura do cadastro simulando somente a fronteira HTTP.
