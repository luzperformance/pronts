# PP-015 — Adicionar adendo

## Resultado

Permitir acrescentar uma correção ou complementação imutável a uma consulta
finalizada, sem alterar ou ocultar o registro original.

## Dependências

- PP-013.

## Escopo

- criar `Addendum` e sua migration sem operações de update/delete;
- implementar `POST /api/v1/consultations/{consultationId}/addenda`;
- exigir conteúdo e justificativa não vazios;
- aceitar adendo somente para consulta `FINALIZED`;
- atribuir autor e instante no servidor;
- fazer os detalhes da consulta e o prontuário exibirem original e adendos;
- auditar `ADDENDUM_ADDED` na mesma transação, sem copiar o texto.

## Fora do escopo

- editar, remover ou reordenar adendo;
- transformar adendo em nova versão da consulta;
- reabrir o registro clínico original.

## Critérios de aceitação

- rascunho rejeita adendo com `409`;
- conteúdo ou justificativa em branco retorna `400`;
- adendo válido recebe autor e instante do servidor;
- o conteúdo original permanece byte a byte inalterado;
- não existem rotas de edição ou remoção;
- detalhes e prontuário exibem os adendos de modo determinístico;
- adendo e auditoria confirmam ou falham juntos.

## Estratégia TDD

- seam de domínio para criação válida, branco e imutabilidade;
- seam REST para integração com detalhes, prontuário e auditoria;
- não testar construtores JPA ou contar chamadas internas.

## Requisitos

RF-042–044; RN-031–034, RN-046–047; RNF-014–015, RNF-030–031; CA-011.
