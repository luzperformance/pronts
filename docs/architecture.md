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

## ADR-003 — PostgreSQL versionado durante a expansão para Drizzle

**Situação:** adotada.

O runtime Spring continua executando as 16 migrações Flyway e o Hibernate usa
`ddl-auto=validate`. Em paralelo, o pacote independente `database/` declara o
mesmo schema com Drizzle e mantém um baseline consolidado para PostgreSQL vazio.
Os históricos não são misturados: um banco é preparado por Flyway ou pelo
baseline Drizzle.

A equivalência é verificada em dois PostgreSQL 18 independentes pela comparação
dos catálogos resultantes e por provas reais da separação entre as roles de
migração e runtime.

Node e Drizzle são ferramentas de migração e não entram na imagem ou no processo
Spring. Geração automática de esquema pelo Hibernate, scripts manuais fora de
versão e H2 permanecem rejeitados.

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

## ADR-009 — Implantação Kubernetes local mínima

**Situação:** adotada.

A API usa imagem de múltiplos estágios sem privilégios de superusuário,
`Deployment` de uma réplica com
`Recreate` e PVC `ReadWriteOnce`. PostgreSQL 18 usa `StatefulSet` de uma réplica
e PVC separado. Ambos usam `ClusterIP`; somente o Traefik recebe tráfego da
máquina e termina TLS local.

Docker Compose, Helm, Kustomize, operadores, HPA, múltiplas réplicas, GitOps,
nuvem, banco gerenciado, domínio público e CA pública foram rejeitados pelo
escopo acadêmico.

## Princípios de implementação observados

- serviços de aplicação coordenam regra, transação e auditoria;
- repositórios escondem persistência sem criar CRUD genérico;
- objetos de valor e políticas concentram regras que têm significado próprio;
- nomes explícitos e fluxos previsíveis têm prioridade sobre abstrações;
- nenhuma camada é criada apenas para satisfazer um padrão;
- PVC garante persistência de pod, não cópia de segurança.
