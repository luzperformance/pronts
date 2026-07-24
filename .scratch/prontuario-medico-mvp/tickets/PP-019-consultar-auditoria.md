# PP-019 — Completar a auditoria consultável e apenas de inserção

## Resultado

Completar a trilha de auditoria de todos os fluxos sensíveis e disponibilizar
pesquisa paginada, segura e estritamente somente leitura.

## Dependências

- PP-009;
- PP-011;
- PP-014;
- PP-015;
- PP-017;
- PP-018.

## Escopo

- evoluir `GET /api/v1/audit-events` criado como walking skeleton em PP-002;
- filtrar por período, ação, resultado, tipo de alvo e identificador;
- ordenar do evento mais recente para o mais antigo com desempate estável;
- revisar todos os produtores previstos na especificação;
- manter whitelist fechada de `safeMetadata`;
- garantir ausência de operações de atualização/exclusão na aplicação;
- reforçar a política apenas de inserção por permissões e desenho de migração compatíveis;
- validar que mutações de negócio e evento confirmam na mesma transação;
- validar que falhas de autenticação usam transação própria.

## Fora do escopo

- CSV, PDF, relatórios, exportação ou dashboard;
- alteração, exclusão ou reprocessamento de eventos;
- eventos de domínio, AOP, mensageria ou fluxo assíncrono.

## Critérios de aceitação

- somente sessão autenticada consulta eventos;
- todos os filtros funcionam isolados e combinados;
- página é limitada, recente primeiro e determinística;
- retorno não contém senha, conteúdo clínico, CPF, carga útil, binário ou caminho;
- todos os eventos da seção 15.3 são observáveis após seus fluxos;
- não existem rotas REST mutáveis para auditoria;
- tentativa interna de atualização/exclusão é rejeitada pela proteção adotada;
- não há formato de exportação.

## Estratégia TDD

- fronteira REST cria eventos pelas rotas de negócio e os consulta pela API;
- testes de rollback usam uma falha pública observável e confirmam ausência do
  evento de sucesso;
- nenhum teste acessa diretamente a tabela para provar comportamento público.

## Requisitos

RF-054–058; RN-046–048; RNF-013–014, RNF-023; CA-015.
