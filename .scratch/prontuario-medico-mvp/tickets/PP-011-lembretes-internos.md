# PP-011 — Lembretes internos

## Resultado

Exibir sob demanda os agendamentos ativos das próximas 24 horas, sem job ou
comunicação externa.

## Dependências

- PP-010.

## Escopo

- implementar `GET /api/v1/appointments/reminders`;
- derivar a janela `[agora, agora + 24h]` com `Clock`;
- incluir somente `SCHEDULED` e `CONFIRMED`;
- retornar resultado paginado e determinístico;
- respeitar UTC persistido e a zona de apresentação configurada.

## Fora do escopo

- notificações, e-mail, SMS, WhatsApp, push ou confirmação de leitura;
- job agendado, fila ou persistência de lembrete;
- janela configurável.

## Critérios de aceitação

- agendamentos elegíveis dentro da janela aparecem;
- estados terminais, eventos passados e eventos após a janela não aparecem;
- os limites temporais são definidos e testados sem depender do relógio real;
- a consulta não modifica agendamento nem cria entidade de lembrete;
- a resposta é paginada e ordenada pelo início e identificador.

## Estratégia TDD

- seam REST com `Clock` fixo e agendamentos criados pela API;
- exemplos explícitos nos limites inferior e superior da janela;
- nenhum teste unitário de query service sem regra.

## Requisitos

RF-032; RN-023–024; RNF-023, RNF-025, RNF-029; CA-007.
