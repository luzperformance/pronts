# 16 — Consultar a auditoria

**What to build:** permitir que o médico investigue eventos sensíveis pela interface em uma trilha paginada e estritamente somente leitura.

**Blocked by:** 04 — Autenticar e navegar no frontend React.

**Status:** ready-for-agent

- [ ] A listagem é ordenada do evento mais recente para o mais antigo e permite percorrer as páginas.
- [ ] Período, ação, resultado, tipo de alvo e identificador de alvo podem ser filtrados isoladamente ou em conjunto.
- [ ] Cada linha apresenta ator disponível, ação, alvo, resultado, instante e identificador de correlação.
- [ ] Nenhum retorno ou componente apresenta senha, CPF, valores cadastrais alterados, conteúdo clínico, carga útil, binário ou caminho físico.
- [ ] A tela não oferece alteração, exclusão ou exportação da auditoria.
- [ ] Estado vazio, filtro inválido, falha de autorização e sessão expirada possuem respostas observáveis.
- [ ] Testes de fluxo cobrem filtros, paginação, ordenação e ausência de dados sensíveis.
