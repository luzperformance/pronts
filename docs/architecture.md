# Decisões arquiteturais e padrões

Este registro descreve o que existe no MVP. Não é uma lista de extensões futuras.

## ADR-001 — Monólito modular por capacidade

**Situação:** adotada.

A aplicação é um único processo Spring Boot. Pacotes representam capacidades
(`auth`, `patient`, `appointment`, `medicalrecord`, `attachment`, `audit`) e
compartilham somente infraestrutura HTTP estritamente comum.

Consequências: transações locais e implantação simples; não há independência de
escala ou implantação por módulo. Microserviços, mensageria distribuída, malha
de serviços e saga foram rejeitados porque não resolvem um requisito do MVP.

## ADR-002 — REST e domínio como únicas fronteiras de teste

**Situação:** adotada.

Comportamento integrado é verificado pela API REST com a aplicação completa e
PostgreSQL 18/Testcontainers. Regras substanciais sem Spring podem usar a API
pública do domínio. Testes isolados de controlador/serviço/repositório, duplos
internos e H2 foram rejeitados porque acoplariam os testes à implementação ou
mudariam a semântica do banco.

## ADR-003 — Drizzle como único escritor do schema

**Situação:** adotada.

O pacote independente `database/` e seu SQL Drizzle versionado são o único
caminho suportado de migração. O gate manual aplica o schema antes do deploy com
a role de migração; o Spring não contém ferramenta de migração, usa somente a
role de runtime e mantém `ddl-auto=validate`.

As antigas migrações foram retiradas depois das provas de equivalência e da
integração Spring sobre PostgreSQL 18 preparado pelo Drizzle. PostgreSQL
18/Testcontainers continua validando schema, privilégios e comportamento REST.
Node e Drizzle não entram na imagem nem no processo Spring. Geração automática
de schema pelo Hibernate, scripts não versionados e H2 permanecem rejeitados.

## ADR-004 — Consistência explícita nas mutações

**Situação:** adotada.

Pacientes, agendamentos e consultas usam versão otimista para impedir
sobrescrita silenciosa. As mutações da agenda adquirem um bloqueio pessimista sobre
uma instância única do calendário antes de consultar conflitos e gravar. Intervalos são
semiabertos.

Bloqueio distribuído, fila externa e granularidade por médico foram rejeitados: há
um médico, uma réplica e um calendário no escopo aprovado.

## ADR-005 — Imutabilidade clínica e auditoria transacional

**Situação:** adotada.

Consulta finalizada não é editada; correções entram como adendos imutáveis.
Eventos de auditoria são gravados na mesma transação da mutação correspondente
e não carregam conteúdo clínico. Registro de eventos como fonte e CQRS foram rejeitados: o
banco relacional continua sendo a fonte de verdade e a auditoria não recompõe
estado.

## ADR-006 — Metadado relacional e binário em sistema de arquivos privado

**Situação:** adotada.

Metadados e resumo SHA-256 ficam no PostgreSQL; binários ficam em diretório/PVC
privado. O baixamento passa pela API autenticada. A remoção mantém uma lápide e
suporta recuperação da limpeza quando sistema de arquivos e transação não podem ser
atômicos.

Armazenamento de objetos, volume compartilhado, exposição direta de arquivos e
binário dentro do PostgreSQL foram rejeitados por ampliarem a infraestrutura ou
o acoplamento sem necessidade acadêmica.

## ADR-007 — Sessão no servidor, cookie e CSRF

**Situação:** adotada.

O único médico autentica por sessão. `JSESSIONID` é `HttpOnly`, `SameSite=Lax` e
`Secure` em HTTPS; o CSRF usa envio duplo por cookie/cabeçalho provido pelo Spring
Security. Tokens bearer/JWT, OAuth/OIDC e autenticação de paciente foram rejeitados por
não existirem identidades ou integrações que os justifiquem neste MVP.

## ADR-008 — Problem Details e identificador de correlação

**Situação:** adotada.

Erros públicos usam `application/problem+json`, tipos `urn:problem:*` e
`correlationId`. Respostas e logs evitam segredo, conteúdo clínico e detalhes
internos. Um envelope de erro proprietário e rastreamentos de pilha públicos foram
rejeitados.

## ADR-009 — Kubernetes local com Neon e anexos persistentes

**Situação:** adotada.

A API usa imagem de múltiplos estágios sem privilégios de superusuário,
`Deployment` de uma réplica com `Recreate`, `Service` `ClusterIP` e conexão JDBC
direta com TLS ao Neon. O cluster não executa PostgreSQL nem recebe credenciais
de migração. Somente os anexos usam PVC `ReadWriteOnce`, e somente o Traefik
recebe tráfego da máquina e termina TLS local.

PostgreSQL in-cluster, Docker Compose, Helm, Kustomize, operadores, HPA,
múltiplas réplicas, GitOps, domínio público e CA pública foram rejeitados pelo
escopo acadêmico.

## Princípios de implementação observados

- serviços de aplicação coordenam regra, transação e auditoria;
- repositórios escondem persistência sem criar CRUD genérico;
- objetos de valor e políticas concentram regras que têm significado próprio;
- nomes explícitos e fluxos previsíveis têm prioridade sobre abstrações;
- nenhuma camada é criada apenas para satisfazer um padrão;
- PVC garante persistência de pod, não cópia de segurança.
