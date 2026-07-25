# 06 — Cadastrar paciente

**What to build:** permitir que o médico registre um paciente fictício ou anonimizado pela interface e continue diretamente para seu cadastro.

**Blocked by:** 05 — Pesquisar e consultar pacientes.

**Status:** ready-for-agent

- [ ] O formulário diferencia claramente campos obrigatórios e opcionais definidos na especificação.
- [ ] Datas civis, CPF, telefone e demais valores são enviados no formato aceito pelo contrato OpenAPI.
- [ ] Validações locais úteis não substituem as validações do servidor, e erros por campo retornados em Problem Details aparecem junto ao campo correspondente.
- [ ] CPF inválido ou duplicado não perde os demais dados já digitados e apresenta a resposta segura da API.
- [ ] Cadastro válido abre o paciente criado e o torna encontrável na pesquisa.
- [ ] Testes de fluxo cobrem cadastro válido, campos ausentes, CPF inválido e conflito de duplicidade.
