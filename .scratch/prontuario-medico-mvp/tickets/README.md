# Tickets do MVP — Primeiro Prontuário

**Status:** proposta para revisão de granularidade e dependências

**Fonte de verdade:** [`../spec.md`](../spec.md)

**Quantidade:** 22 fatias verticais

**Data da decomposição:** 23 de julho de 2026

## Como ler esta decomposição

Cada ticket entrega um comportamento observável pela API, com a migration mínima
necessária e testes escritos por TDD. Os únicos seams permitidos são:

1. API REST integrada, com a aplicação completa e PostgreSQL 18 em
   Testcontainers;
2. API pública do domínio, sem Spring, somente para regras substanciais.

Não fazem parte dos tickets testes isolados de controller, service ou repository,
mocks de componentes internos, H2, camadas vazias, CRUD genérico ou refatorações
laterais não exigidas pela fatia.

Os tickets ainda não foram enviados a um tracker. Estes arquivos são a proposta
revisável anterior à publicação.

## Gates de decisão

As decisões abaixo não impedem revisar a decomposição, mas precisam estar
resolvidas antes do ticket indicado:

| Gate | Decisão | Recomendação | Prazo |
|---|---|---|---|
| G-01 | `groupId`, `artifactId` e pacote raiz | `br.com.primeiroprontuario`, `primeiro-prontuario-api` e `br.com.primeiroprontuario` | antes de PP-001 |
| G-02 | resposta ao download de anexo removido | `410 Gone`, pois a lápide confirma que o recurso existiu | antes de PP-018 |
| G-03 | tracker do repositório | configurar projeto, labels e template antes de publicar estes arquivos como issues | antes da publicação externa |

## Topologia de deploy aprovada

- toda a demonstração executada localmente, sem cloud, domínio público, banco
  externo ou serviço gerenciado;
- imagem Docker multi-stage executada como usuário não root;
- API em `Deployment` de uma réplica com estratégia `Recreate`;
- configuração por `ConfigMap` e referências a `Secret`;
- anexos em PVC `ReadWriteOnce`;
- PostgreSQL 18 dentro do cluster Kubernetes local, em `StatefulSet` de uma
  réplica com PVC próprio;
- API e banco expostos somente por `Service` `ClusterIP`;
- `Ingress` processado por Traefik disponível no cluster local, com
  redirecionamento para HTTPS e terminação TLS por certificado local;
- manifests diretos, sem Helm, Kustomize, operadores, HPA ou GitOps.

## Ordem e dependências

| ID | Fatia | Depende de | Pode iniciar após |
|---|---|---|---|
| [PP-001](PP-001-fundacao-executavel.md) | Fundação executável | — | G-01 |
| [PP-002](PP-002-login-e-contrato-http.md) | Login e contrato HTTP seguro | PP-001 | PP-001 |
| [PP-003](PP-003-ciclo-da-sessao.md) | Ciclo completo da sessão | PP-002 | PP-002 |
| [PP-004](PP-004-cadastrar-e-consultar-paciente.md) | Cadastrar e consultar paciente | PP-003 | PP-003 |
| [PP-005](PP-005-pesquisar-pacientes.md) | Pesquisar pacientes | PP-004 | PP-004 |
| [PP-006](PP-006-atualizar-e-alterar-status-do-paciente.md) | Atualizar e alterar status do paciente | PP-004 | PP-004 |
| [PP-007](PP-007-agendar-e-consultar-agenda.md) | Agendar e consultar agenda | PP-004 | PP-004 |
| [PP-008](PP-008-impedir-conflitos-de-agenda.md) | Impedir conflitos de agenda | PP-007 | PP-007 |
| [PP-009](PP-009-administrar-bloqueios.md) | Administrar bloqueios | PP-008 | PP-008 |
| [PP-010](PP-010-reagendar-e-transicionar-agendamento.md) | Reagendar e transicionar agendamento | PP-008 | PP-008 |
| [PP-011](PP-011-lembretes-internos.md) | Lembretes internos | PP-010 | PP-010 |
| [PP-012](PP-012-criar-e-editar-rascunho-clinico.md) | Criar e editar rascunho clínico | PP-006, PP-007 | ambos |
| [PP-013](PP-013-finalizar-consulta.md) | Finalizar consulta | PP-010, PP-012 | ambos |
| [PP-014](PP-014-ler-prontuario-cronologico.md) | Ler prontuário cronológico | PP-013 | PP-013 |
| [PP-015](PP-015-adicionar-adendo.md) | Adicionar adendo | PP-013 | PP-013 |
| [PP-016](PP-016-enviar-e-listar-anexo.md) | Enviar e listar anexo | PP-012 | PP-012 |
| [PP-017](PP-017-baixar-anexo.md) | Baixar anexo | PP-016 | PP-016 |
| [PP-018](PP-018-remover-anexo.md) | Remover anexo | PP-016, G-02 | ambos |
| [PP-019](PP-019-consultar-auditoria.md) | Consultar auditoria | PP-009, PP-011, PP-014, PP-015, PP-017, PP-018 | todos |
| [PP-020](PP-020-hardening-integrado.md) | Hardening integrado | PP-019 | PP-019 |
| [PP-021](PP-021-deploy-docker-kubernetes-traefik.md) | Deploy simples com Docker, Kubernetes e Traefik | PP-020 | PP-020 |
| [PP-022](PP-022-documentacao-e-validacao-final.md) | Documentação e validação final | PP-021 | PP-021 |

PP-005 e PP-006 podem avançar em paralelo. Depois de PP-008, PP-009 e PP-010
também podem avançar em paralelo. Depois de PP-012, PP-016 pode avançar em
paralelo com o caminho de finalização. Depois de PP-013, PP-014 e PP-015 podem
avançar em paralelo. PP-017 e PP-018 podem avançar em paralelo depois de PP-016.

## Caminho crítico

`PP-001 → PP-002 → PP-003 → PP-004 → PP-007 → PP-008 → PP-010 → PP-013
→ PP-015 → PP-019 → PP-020 → PP-021 → PP-022`

PP-013 também aguarda PP-012. PP-019 é o join das trilhas de agenda,
prontuário e anexos; portanto o ganho de paralelismo depende de iniciar PP-006,
PP-009, PP-012 e PP-016 assim que seus predecessores liberarem.

## Regra de conclusão comum

Um ticket só está concluído quando:

- o teste falhou primeiro pelo motivo esperado e passou após a implementação;
- quando houver migration, ela sobe em banco vazio e o JPA apenas valida o
  schema;
- o fluxo REST está coberto com sessão e CSRF quando aplicável;
- regras substanciais estão no domínio e, quando útil, cobertas pelo seam de
  domínio;
- erros seguem Problem Details e carregam correlation ID;
- auditoria prevista confirma na mesma transação da mutação;
- nenhuma resposta ou log expõe segredo, dado clínico desnecessário ou caminho
  físico;
- o contrato OpenAPI e a documentação afetada foram atualizados;
- o conjunto relevante e o build completo permanecem verdes;
- o diff não introduz itens explicitamente fora do escopo.

## Rastreabilidade

A cobertura de RF, RNF, regras de negócio e critérios de aceitação está em
[`traceability.md`](traceability.md). A matriz é normativa para impedir que um
requisito desapareça durante a publicação dos tickets.
