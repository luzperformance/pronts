# 14 — Enviar e listar anexos

**What to build:** permitir que o médico envie documentos relacionados ao paciente, opcionalmente contextualizados por uma consulta, e consulte seus metadados ativos.

**Blocked by:** 11 — Criar e editar rascunho clínico.

**Status:** ready-for-agent

- [ ] O seletor aceita PDF, JPG, PNG e Markdown e informa o limite público de 10 MiB.
- [ ] O médico pode enviar o anexo somente com paciente ou vinculá-lo opcionalmente a uma consulta do mesmo paciente.
- [ ] Tipo detectado incompatível, tamanho excessivo e vínculo inválido apresentam as respostas seguras da API.
- [ ] Nome informado pelo usuário é exibido apenas como metadado e nunca revela chave ou caminho físico.
- [ ] Envio válido atualiza a listagem de anexos ativos com tipo, tamanho, resumo criptográfico e contexto disponível.
- [ ] A interface não renderiza Markdown recebido como HTML.
- [ ] Testes de fluxo cobrem envio válido, tipos rejeitados, limite, vínculo opcional e listagem.
