# PP-017 — Baixar anexo

## Resultado

Permitir baixamento autenticado e seguro de anexo ativo, mediado pela aplicação e
sem revelar a localização física.

## Dependências

- PP-016.

## Escopo

- implementar `GET /api/v1/attachments/{attachmentId}/content`;
- autorizar o acesso pela sessão;
- transmitir o binário sem carregar desnecessariamente o arquivo inteiro;
- definir `Content-Type` a partir do tipo detectado e
  `Content-Disposition: attachment`;
- codificar o nome original de modo seguro;
- tratar Markdown como baixamento textual UTF-8, nunca HTML;
- auditar `ATTACHMENT_DOWNLOADED` sem binário, caminho ou nome sensível.

## Fora do escopo

- URL pública, link assinado, CDN, preview ou renderização inline;
- range requests e cache distribuído;
- baixamento de anexo removido, definido em PP-018.

## Critérios de aceitação

- sessão válida baixa conteúdo byte a byte igual ao envio;
- ausência de sessão retorna `401`;
- caminho e chave de armazenamento nunca aparecem em cabeçalhos ou corpo;
- cabeçalho de disposição não permite injeção;
- Markdown é entregue como anexo UTF-8 e não renderizado;
- baixamento inexistente retorna `404` seguro;
- cada baixamento bem-sucedido gera auditoria mínima.

## Estratégia TDD

- fronteira REST executa envio seguido de baixamento real;
- comparar resumo criptográfico calculado fora da implementação, sem acessar o sistema de arquivos;
- provar auditoria exclusivamente por `/api/v1/audit-events`.

## Requisitos

RF-051, RF-053; RN-042–043, RN-046; RNF-013, RNF-019, RNF-022, RNF-027;
CA-013.
