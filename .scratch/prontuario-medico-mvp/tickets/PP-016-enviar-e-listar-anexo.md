# PP-016 — Enviar e listar anexo

## Resultado

Permitir enviar um arquivo permitido para armazenamento local privado e consultar
seus metadados sem expor caminho físico.

## Dependências

- PP-012.

## Escopo

- criar `Attachment`, migração e ciclo inicial `ACTIVE`;
- criar contrato pequeno de armazenamento e adaptador de sistema de arquivos local;
- configurar diretório privado por ambiente e diretório temporário nos testes;
- implementar `POST /api/v1/patients/{patientId}/attachments`;
- implementar `GET /api/v1/patients/{patientId}/attachments`;
- implementar `GET /api/v1/attachments/{attachmentId}`;
- aceitar PDF, JPG, PNG e Markdown até 10 MiB;
- verificar conteúdo real, extensão e MIME informado;
- gerar chave interna aleatória e calcular SHA-256;
- validar paciente e consulta opcional do mesmo paciente;
- usar área temporária e compensação para não deixar arquivo órfão conhecido;
- auditar `ATTACHMENT_UPLOADED`.

## Fora do escopo

- baixamento e remoção;
- antivírus, armazenamento de objetos, renderização de Markdown ou deduplicação por resumo criptográfico;
- caminho informado pelo usuário ou diretório público.

## Critérios de aceitação

- os quatro tipos válidos dentro do limite são aceitos;
- extensão falsa, conteúdo inválido e tipo não permitido retornam `415`;
- arquivo acima do limite retorna `413` antes da persistência definitiva;
- nome perigoso permanece apenas metadado e não controla a chave;
- consulta de outro paciente retorna conflito;
- envio sem consulta é aceito;
- lista contém somente ativos e metadados seguros;
- falha entre armazenamento, banco e auditoria executa compensação verificável.

## Estratégia TDD

- fronteira REST de múltiplas partes com dados de teste mínimos reais dos quatro tipos;
- armazenamento local exercitado em diretório temporário, sem duplo;
- casos de compensação usam falha controlada na fronteira externa do armazenamento,
  não duplos de componentes internos.

## Requisitos

RF-045–050, RF-053; RN-039–042, RN-046–047; RNF-019–022, RNF-029–031;
CA-012.
