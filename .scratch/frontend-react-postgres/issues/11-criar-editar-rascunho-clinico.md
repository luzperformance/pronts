# 11 — Criar e editar rascunho clínico

**What to build:** permitir que o médico inicie uma consulta para um paciente ativo, salve informações ainda incompletas e retome a edição enquanto o registro permanecer em rascunho.

**Blocked by:** 08 — Visualizar a agenda e agendar consulta.

**Status:** ready-for-agent

- [ ] A partir de paciente ativo, o médico pode criar uma consulta com data clínica e campos ainda incompletos.
- [ ] Um agendamento do mesmo paciente pode ser selecionado opcionalmente, sem tornar o vínculo obrigatório.
- [ ] Agendamento de outro paciente e criação para paciente inativo apresentam o conflito seguro retornado pela API.
- [ ] Salvar e reabrir o rascunho preserva os seis campos clínicos, o vínculo opcional e a versão corrente.
- [ ] Conflito de versão não sobrescreve silenciosamente outra alteração e permite recarregar o conteúdo atual.
- [ ] O rascunho não é apresentado como item definitivo do prontuário.
- [ ] Testes de fluxo cobrem criação incompleta, edição, vínculo opcional e conflitos.
