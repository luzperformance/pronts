# PP-008 — Impedir conflitos de agenda

## Resultado

Tornar confiável, inclusive sob concorrência, a garantia de que o calendário
único não aceita dois agendamentos ativos sobrepostos.

## Dependências

- PP-007.

## Escopo

- criar a linha técnica que representa o calendário único;
- obter trava pessimista curta antes de toda mutação de intervalo da agenda;
- verificar sobreposição dentro da mesma transação da trava e gravação;
- considerar cancelados como não bloqueantes e os demais estados como
  bloqueantes;
- tratar intervalos como início inclusivo e fim exclusivo;
- mapear conflito para `409` em Problem Details;
- cobrir disputa real entre duas requisições.

## Fora do escopo

- bloqueios de agenda, adicionados em PP-009;
- restrição PostgreSQL avançada entre tabelas;
- fila, bloqueio distribuído ou granularidade por médico.

## Critérios de aceitação

- um intervalo sobreposto a agendamento ativo retorna `409`;
- intervalos que apenas se tocam são aceitos;
- agendamento cancelado não bloqueia o horário;
- duas requisições concorrentes pela mesma lacuna produzem um sucesso e um
  conflito, nunca dois sucessos;
- a trava dura apenas a transação da mutação;
- o fluxo continua correto após reinício da aplicação.

## Estratégia TDD

- fronteira de domínio para sobreposição e adjacência de `TimeInterval`;
- fronteira REST com duas requisições realmente concorrentes e PostgreSQL;
- não simular concorrência com duplos nem testar a anotação de bloqueio isoladamente.

## Requisitos

RF-021; RN-014–015, RN-018, RN-049; RNF-016, RNF-018, RNF-024, RNF-030–031;
CA-005.
