# Decisões arquiteturais e patterns

Este registro descreve o que existe no MVP. Não é uma lista de extensões futuras.

## ADR-001 — Monólito modular por capacidade

**Status:** adotada.

A aplicação é um único processo Spring Boot. Pacotes representam capacidades
(`auth`, `patient`, `appointment`, `medicalrecord`, `attachment`, `audit`) e
compartilham somente infraestrutura HTTP estritamente comum.

Consequências: transações locais e deploy simples; não há independência de
escala ou implantação por módulo. Microserviços, mensageria distribuída, service
mesh e saga foram rejeitados porque não resolvem um requisito do MVP.

## ADR-002 — REST e domínio como únicos seams de teste

**Status:** adotada.

Comportamento integrado é verificado pela API REST com a aplicação completa e
PostgreSQL 18/Testcontainers. Regras substanciais sem Spring podem usar a API
pública do domínio. Testes isolados de controller/service/repository, mocks
internos e H2 foram rejeitados porque acoplariam os testes à implementação ou
mudariam a semântica do banco.

## ADR-003 — PostgreSQL versionado por Flyway

**Status:** adotada.

Flyway é o único escritor do schema e o Hibernate usa `ddl-auto=validate`.
Banco vazio recebe todas as migrations; checksum ou histórico incompatível
interrompe o startup. Geração automática de schema, scripts manuais fora de
versão e H2 foram rejeitados.

## ADR-004 — Consistência explícita nas mutações

**Status:** adotada.

Pacientes, agendamentos e consultas usam versão otimista para impedir
sobrescrita silenciosa. As mutações da agenda adquirem um lock pessimista sobre
um calendário singleton antes de consultar conflitos e gravar. Intervalos são
semiabertos.

Lock distribuído, fila externa e granularidade por médico foram rejeitados: há
um médico, uma réplica e um calendário no escopo aprovado.

## ADR-005 — Imutabilidade clínica e auditoria transacional

**Status:** adotada.

Consulta finalizada não é editada; correções entram como adendos append-only.
Eventos de auditoria são gravados na mesma transação da mutação correspondente
e não carregam conteúdo clínico. Event sourcing e CQRS foram rejeitados: o
banco relacional continua sendo a fonte de verdade e a auditoria não recompõe
estado.

## ADR-006 — Metadado relacional e binário em filesystem privado

**Status:** adotada.

Metadados e hash SHA-256 ficam no PostgreSQL; binários ficam em diretório/PVC
privado. Download passa pela API autenticada. Remoção mantém uma lápide e
suporta recuperação da limpeza quando filesystem e transação não podem ser
atômicos.

Armazenamento de objetos, volume compartilhado, exposição direta de arquivos e
binário dentro do PostgreSQL foram rejeitados por ampliarem a infraestrutura ou
o acoplamento sem necessidade acadêmica.

## ADR-007 — Sessão server-side, cookie e CSRF

**Status:** adotada.

O único médico autentica por sessão. `JSESSIONID` é `HttpOnly`, `SameSite=Lax` e
`Secure` em HTTPS; CSRF usa double-submit cookie/header provido pelo Spring
Security. Tokens bearer/JWT, OAuth/OIDC e login de paciente foram rejeitados por
não existirem identidades ou integrações que os justifiquem neste MVP.

## ADR-008 — Problem Details e correlation ID

**Status:** adotada.

Erros públicos usam `application/problem+json`, tipos `urn:problem:*` e
`correlationId`. Respostas e logs evitam segredo, conteúdo clínico e detalhes
internos. Um envelope de erro proprietário e stack traces públicos foram
rejeitados.

## ADR-009 — Deploy Kubernetes local mínimo

**Status:** adotada.

A API usa imagem multi-stage não root, `Deployment` de uma réplica com
`Recreate` e PVC `ReadWriteOnce`. PostgreSQL 18 usa `StatefulSet` de uma réplica
e PVC separado. Ambos usam `ClusterIP`; somente o Traefik recebe tráfego da
máquina e termina TLS local.

Docker Compose, Helm, Kustomize, operadores, HPA, múltiplas réplicas, GitOps,
cloud, banco gerenciado, domínio público e CA pública foram rejeitados pelo
escopo acadêmico.

## Princípios de implementação observados

- application services coordenam regra, transação e auditoria;
- repositories escondem persistência sem criar CRUD genérico;
- value objects e policies concentram regras que têm significado próprio;
- nomes explícitos e fluxos previsíveis têm prioridade sobre abstrações;
- nenhuma camada é criada apenas para satisfazer um pattern;
- PVC garante persistência de pod, não backup.
