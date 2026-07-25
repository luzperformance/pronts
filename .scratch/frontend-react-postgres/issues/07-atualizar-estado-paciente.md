# 07 — Atualizar e alterar o estado do paciente

**What to build:** permitir que o médico corrija dados cadastrais e inative ou reative um paciente sem apagar seu histórico.

**Blocked by:** 06 — Cadastrar paciente.

**Status:** ready-for-agent

- [ ] O cadastro existente pode ser editado mantendo identificador e versão de concorrência.
- [ ] Uma atualização concluída apresenta imediatamente os valores e a nova versão retornados pelo servidor.
- [ ] Conflito de versão informa que os dados mudaram e oferece recarregamento seguro, sem sobrescrever silenciosamente outra alteração.
- [ ] Inativar e reativar exigem ação explícita e atualizam o estado visível do paciente.
- [ ] Paciente inativo continua pesquisável e com acesso ao histórico, mas a interface não oferece novos agendamentos ou consultas como se ele estivesse ativo.
- [ ] Testes de fluxo cobrem edição, conflito otimista, inativação e reativação.
