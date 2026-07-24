# Checklist final de demonstração

Este checklist é simultaneamente roteiro e registro de evidência do PP-022.
Marque um item somente após observar o resultado no checkout e no cluster que
será demonstrado.

> Pare imediatamente se houver dado real. Somente dados fictícios ou
> previamente anonimizados são permitidos.

## 1. Checkout e toolchain

- [x] checkout limpo no commit que será demonstrado;
- [x] JDK 21 ativo;
- [x] Docker acessível ao Testcontainers;
- [x] `./mvnw --version` mostra Maven 3.9.16;
- [x] `./mvnw verify` termina com `BUILD SUCCESS`;
- [x] o resumo confirma toda a suíte, Checkstyle e Spotless;
- [x] `git diff --check` não encontra whitespace inválido.

Evidência:

```bash
git status --short
java -version
docker version
./mvnw --version
sg docker -c './mvnw verify'
git diff --check
```

## 2. Contrato e escopo

- [x] [`openapi.yaml`](openapi.yaml) valida como OpenAPI 3.1;
- [x] as 30 operações planejadas aparecem no contrato;
- [x] login, sessão, CSRF, paginação e Problem Details estão descritos;
- [x] controllers possuem as mesmas 30 operações e nenhuma outra em `/api/v1`;
- [x] somente `GET /actuator/health` existe fora da API de negócio;
- [x] `pom.xml`, migrations e manifests não introduzem item fora do
      [`scope-review.md`](scope-review.md).

Validação opcional do documento com CLI baixada sob demanda:

```bash
npx --yes @redocly/cli lint docs/openapi.yaml
```

## 3. Imagem

- [x] build feito a partir do checkout selecionado;
- [x] usuário efetivo é `10001:10001`;
- [x] entrypoint contém somente o JAR de runtime;
- [x] imagem não contém fonte, Maven, credenciais, TLS ou backups.

```bash
docker build --pull --tag primeiro-prontuario-api:0.0.1 .
docker inspect primeiro-prontuario-api:0.0.1 \
  --format '{{.Config.User}} {{json .Config.Entrypoint}}'
docker run --rm --entrypoint sh primeiro-prontuario-api:0.0.1 \
  -ec 'test ! -e /workspace; test ! -e /root/.m2; test -f /app/application.jar'
```

## 4. Cluster do zero

- [x] cluster local e Traefik atendem todos os pré-requisitos do guia;
- [x] namespace anterior foi removido no cluster descartável;
- [x] credenciais locais usam apenas valores fictícios e não estão no Git;
- [x] CA e certificado foram criados localmente;
- [x] a CA foi fornecida somente ao cliente da demonstração por `--cacert`,
      sem alterar a confiança do sistema;
- [x] o cliente resolveu `prontuario.local` para a entrada do Traefik sem
      alterar `/etc/hosts`;
- [x] manifests foram aplicados na ordem documentada;
- [x] PostgreSQL 18 iniciou em banco vazio;
- [x] Flyway aplicou V1–V16 e o Hibernate validou o schema;
- [x] `StatefulSet` do banco e `Deployment` da API ficaram prontos;
- [x] banco e anexos usam PVCs privados e distintos;
- [x] API e PostgreSQL usam somente `ClusterIP`;
- [x] API tem uma réplica e estratégia `Recreate`;
- [x] as três probes usam somente `/actuator/health`.

Siga [`deploy-kubernetes-local.md`](deploy-kubernetes-local.md) sem pular
etapas.

## 5. Smoke autenticado

- [x] HTTP redireciona para HTTPS;
- [x] certificado é aceito com `deploy/tls/ca.crt`, sem `--insecure`;
- [x] health responde apenas `{"status":"UP"}`;
- [x] login cria `JSESSIONID` `Secure`, `HttpOnly` e `SameSite=Lax`;
- [x] cookie/header CSRF autorizam mutações;
- [x] paciente fictício é criado e pesquisado;
- [x] agenda recebe um agendamento sem conflito;
- [x] consulta vinculada é criada e finalizada;
- [x] adendo append-only aparece na consulta e no prontuário;
- [x] anexo Markdown fictício é enviado, baixado e comparado byte a byte;
- [x] auditoria contém paciente, agenda, finalização, adendo, prontuário e
      anexo sem conteúdo clínico;
- [x] recriação do pod da API preserva todas as consultas;
- [x] recriação do pod PostgreSQL preserva banco e download.

```bash
./scripts/smoke-kubernetes.sh
```

## 6. Schema incompatível

- [x] teste público confirma startup em banco vazio;
- [x] teste público altera uma migration já aplicada e confirma que Flyway
      recusa o startup;
- [x] nenhuma instrução recomenda `repair`, `baseline` ou geração Hibernate
      para contornar a falha.

Essas duas provas fazem parte de `ApplicationStartupIntegrationTest` e rodam em
`./mvnw verify`.

## 7. Backup e restauração conjunta

- [x] API foi parada durante o snapshot;
- [x] `database.dump`, `attachments.tar.gz`, inventário e `SHA256SUMS`
      pertencem ao mesmo snapshot;
- [x] checksums locais passaram;
- [x] restauração ocorreu em cluster/namespace descartável vazio;
- [x] banco e binários foram restaurados, nunca apenas um lado;
- [x] todos os hashes do inventário bateram com os binários;
- [x] Flyway aceitou o histórico restaurado;
- [x] smoke autenticado passou após a restauração.

Execute literalmente [`backup-restore.md`](backup-restore.md) e anote o
diretório UTC do snapshot.

## 8. Encerramento seguro

- [x] não há dado real em banco, anexos, logs, screenshots ou backups;
- [x] evidências não contêm senha, cookie, token CSRF ou chave privada;
- [x] namespace descartável foi removido;
- [x] confiança na CA local foi removida (não foi instalada no sistema);
- [x] resolução temporária, TLS, credenciais e backups locais foram apagados;
- [x] limites acadêmicos do MVP foram apresentados.

## Evidências desta execução — 24 de julho de 2026

| Verificação | Resultado observado |
|---|---|
| Rotas planejadas × OpenAPI | verde, 30 × 30, diff vazio |
| `npx --yes @redocly/cli lint docs/openapi.yaml` | válido; 1 aviso de estilo porque o `GET /auth/csrf` público não possui resposta 4xx própria |
| `bash -n scripts/smoke-kubernetes.sh` | verde |
| PP-019 | verde, 6 testes PostgreSQL para filtros, paginação estável, validação, ausência de mutação REST e bloqueio de `UPDATE`/`DELETE` |
| prova de reinício PP-008 | verde: contexto Spring reiniciado sobre o mesmo PostgreSQL; nova requisição REST sobreposta recebeu 409 |
| banco vazio e schema incompatível | verde em `ApplicationStartupIntegrationTest` dentro da suíte integral |
| `sg docker -c './mvnw verify'` | verde: 197 testes, 0 falhas/erros/skips; Spotless limpo; 0 violações Checkstyle; 1 min 1 s |
| build/inspeção da imagem | verde no checkout limpo; imagem `b556224f7011`, usuário `10001:10001`, entrypoint somente `java -jar /app/application.jar`, sem `/workspace` ou cache Maven |
| preflight Kubernetes/Traefik | verde em k3d 5.8.3/k3s 1.31.5: `IngressClass` Traefik, middleware CRD e `local-path` disponíveis |
| aplicação inicial | verde: PostgreSQL 18.4, migrations V1–V16, API pronta e health exato através do Traefik/TLS |
| smoke pelo host local | verde no commit limpo `f0923e4`: HTTP→HTTPS, sessão/CSRF, paciente, agenda, consulta, adendo, anexo, auditoria e dois reinícios |
| snapshot coordenado | `backups/20260724T074854Z`: dump custom 31.862 bytes, 2 anexos ativos, arquivo do PVC e três checksums válidos |
| restauração conjunta descartável | verde em namespace recriado vazio: 1 paciente, 2 agendamentos, 2 anexos e 27 eventos restaurados; hashes conferidos |
| smoke após restauração | verde, incluindo nova recriação dos pods da API e do PostgreSQL |

O cluster, o namespace e todos os artefatos locais desta demonstração são
descartáveis. Nenhuma confiança de CA ou resolução de nome foi instalada no
sistema operacional.
