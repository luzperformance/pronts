# 12 — Finalizar a consulta

**What to build:** permitir que o médico revise um rascunho, corrija os campos obrigatórios e confirme sua transformação irreversível em registro clínico finalizado.

**Blocked by:** 11 — Criar e editar rascunho clínico.

**Status:** ready-for-agent

- [ ] A interface identifica todos os campos clínicos ausentes ou compostos apenas por espaços antes e depois da resposta do servidor.
- [ ] A finalização exige confirmação explícita informando que o conteúdo original não poderá mais ser editado.
- [ ] Finalização válida apresenta autor e instante definidos pelo servidor e transforma a consulta em visualização somente leitura.
- [ ] Nova tentativa de finalização não duplica dados nem cria estado divergente.
- [ ] Um agendamento ativo vinculado aparece como realizado depois da operação atômica.
- [ ] Tentativas posteriores de editar o registro são rejeitadas e explicadas como conflito de imutabilidade.
- [ ] Testes de fluxo e um cenário Playwright cobrem validação, confirmação, imutabilidade e atualização do agendamento contra Spring/PostgreSQL reais.
