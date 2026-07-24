# PP-014 — Ler prontuário cronológico

## Resultado

Exibir o histórico clínico definitivo de um paciente em ordem cronológica,
preservando o conteúdo original e auditando o acesso.

## Dependências

- PP-013.

## Escopo

- implementar `GET /api/v1/patients/{patientId}/medical-record`;
- incluir somente consultas `FINALIZED`;
- filtrar por `from` e `to`;
- paginar e ordenar por data clínica e, em empate, criação e identificador;
- retornar conteúdo original por projeção controlada;
- evitar N+1 e carregamento acidental de todo o agregado;
- auditar `MEDICAL_RECORD_VIEWED` sem conteúdo clínico ou filtros sensíveis.

## Fora do escopo

- rascunhos, busca textual, cópia ou exportação do prontuário;
- relatório, PDF ou endpoint para pacientes;
- edição por meio da projeção de leitura.

## Critérios de aceitação

- rascunhos nunca aparecem;
- finalizados aparecem na ordem definida;
- filtro temporal inclui e exclui corretamente os limites acordados;
- paginação é estável e limitada;
- resposta preserva exatamente o conteúdo finalizado;
- leitura gera auditoria mínima;
- a consulta não produz N+1 para o volume da página.

## Estratégia TDD

- seam REST com consultas criadas e finalizadas pela API;
- provar ordem, filtro e exclusão de rascunho pela resposta pública;
- verificar a regressão N+1 por instrumentação de integração, sem testar
  repository isoladamente.

## Requisitos

RF-039–040; RN-026, RN-030, RN-035, RN-046; RNF-013, RNF-015, RNF-023–024;
CA-010.
