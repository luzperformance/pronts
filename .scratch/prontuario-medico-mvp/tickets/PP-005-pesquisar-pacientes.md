# PP-005 — Pesquisar pacientes

## Resultado

Permitir localizar pacientes por filtros combináveis, paginação limitada e
ordenação determinística.

## Dependências

- PP-004.

## Escopo

- implementar `GET /api/v1/patients`;
- aceitar `fullName`, `motherName`, `cpf`, `phone`, `email` e `status`;
- permitir combinação dos filtros;
- usar Specification somente para evitar explosão de combinações;
- normalizar CPF e aplicar busca textual sem diferença de caixa;
- validar `page`, `size` e ordenação por lista branca;
- definir tamanho padrão, limite máximo e desempate por identificador.

## Fora do escopo

- busca textual em conteúdo clínico;
- fuzzy search, ranking, trigram ou motor externo;
- exportação e listagem não paginada.

## Critérios de aceitação

- cada filtro funciona isoladamente e em combinação;
- nomes e e-mail ignoram diferença entre maiúsculas e minúsculas;
- CPF usa correspondência exata após normalização;
- telefone e status filtram corretamente;
- uma busca vazia retorna página vazia com `200`;
- paginação inválida ou ordenação fora da lista branca retorna `400`;
- a ordem não oscila entre requisições com os mesmos dados;
- pacientes inativos continuam pesquisáveis quando existirem.

## Estratégia TDD

- seam REST com massa pequena e explícita criada pela própria API;
- exemplos cobrem combinação, caixa, página vazia e desempate;
- não testar internamente a Specification ou emitir assertions sobre SQL.

## Requisitos

RF-012–017; RN-007, RN-009; RNF-023–024; CA-003.
