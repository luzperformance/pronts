# PP-017 — Baixar anexo

## Resultado

Permitir download autenticado e seguro de anexo ativo, mediado pela aplicação e
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
- tratar Markdown como download textual UTF-8, nunca HTML;
- auditar `ATTACHMENT_DOWNLOADED` sem binário, caminho ou nome sensível.

## Fora do escopo

- URL pública, link assinado, CDN, preview ou renderização inline;
- range requests e cache distribuído;
- download de anexo removido, definido em PP-018.

## Critérios de aceitação

- sessão válida baixa conteúdo byte a byte igual ao upload;
- ausência de sessão retorna `401`;
- caminho e chave de storage nunca aparecem em headers ou corpo;
- header de disposição não permite injeção;
- Markdown é entregue como anexo UTF-8 e não renderizado;
- download inexistente retorna `404` seguro;
- cada download bem-sucedido gera auditoria mínima.

## Estratégia TDD

- seam REST executa upload seguido de download real;
- comparar hash calculado fora da implementação, sem acessar o filesystem;
- provar auditoria exclusivamente por `/api/v1/audit-events`.

## Requisitos

RF-051, RF-053; RN-042–043, RN-046; RNF-013, RNF-019, RNF-022, RNF-027;
CA-013.
