# PP-018 — Remover anexo

## Resultado

Permitir remover logicamente um anexo com justificativa, apagar o binário e
preservar uma lápide auditável e imutável.

## Dependências e gates

- PP-016;
- G-02 resolvido. O contrato recomendado é `410 Gone`.

## Escopo

- implementar `DELETE /api/v1/attachments/{attachmentId}`;
- exigir justificativa não vazia;
- aplicar transição `ACTIVE → REMOVED`;
- registrar autor, justificativa e instante do servidor;
- apagar o binário por protocolo consistente com o commit do banco;
- conservar identificadores mínimos, tipo, tamanho e SHA-256;
- excluir removidos da listagem padrão;
- fazer metadados indicarem a lápide sem dados desnecessários;
- tornar remoção repetida idempotente;
- auditar `ATTACHMENT_REMOVED`.

## Fora do escopo

- restauração, lixeira acessível, retenção configurável ou exclusão da lápide;
- remoção do paciente ou consulta vinculada;
- endpoint administrativo de limpeza.

## Critérios de aceitação

- justificativa em branco retorna `400`;
- após remoção, conteúdo retorna o status fixado por G-02;
- o binário deixa de estar acessível;
- lápide preserva hash e metadados mínimos acordados;
- nova remoção não duplica efeitos nem destrói a lápide;
- falha de I/O ou banco não deixa estado silenciosamente divergente;
- remoção e auditoria mantêm a atomicidade observável possível do protocolo.

## Estratégia TDD

- seam de domínio para transição e imutabilidade da lápide;
- seam REST para upload → remoção → listagem → metadados → download;
- falhas do adapter são exercitadas pela fronteira real controlável, sem mockar
  application service ou repository.

## Requisitos

RF-052–053; RN-044–047; RNF-014–015, RNF-019, RNF-029–031; CA-014.
