# Lista final de verificação

Esta lista representa a arquitetura vigente. Marque ações operacionais somente
depois de observar o resultado no checkout e no ambiente que será demonstrado.

> Pare imediatamente se houver dado real. Somente dados fictícios ou previamente
> anonimizados são permitidos.

## 1. Construção e testes

- [x] `npm ci` e `npm run typecheck` terminam sem erro em `database/`;
- [x] `npm test` valida o schema Drizzle em PostgreSQL 18/Testcontainers;
- [x] `./mvnw verify` termina com testes, Checkstyle e Spotless verdes;
- [x] `bash -n scripts/smoke-kubernetes.sh` termina sem erro;
- [x] `git diff --check` não encontra espaços em branco inválidos.

## 2. Schema e runtime

- [x] `database/drizzle/` contém o único SQL executável de migração;
- [x] o pacote Spring não depende nem configura Flyway;
- [x] o Hibernate usa `ddl-auto=validate` e não gera schema;
- [x] PostgreSQL 18/Testcontainers permanece na suíte automatizada;
- [x] a role de migração é usada somente pelo gate manual;
- [x] a API recebe apenas a role runtime;
- [x] Node e Drizzle não entram na imagem nem no processo Spring.

Antes do corte, execute literalmente
[`neon-production-cutover.md`](neon-production-cutover.md). Migração, deploy e
restauração são operações distintas.

## 3. Cluster

- [x] não há `Service`, `StatefulSet`, pod, PVC ou credencial destinada a
      PostgreSQL in-cluster;
- [x] o Secret separa URL/usuário/senha runtime das credenciais do médico;
- [x] credenciais proprietária e de migração não aparecem nos manifests;
- [x] a API usa uma réplica, estratégia `Recreate` e `Service` `ClusterIP`;
- [x] o PVC `primeiro-prontuario-attachments` permanece privado e independente
      do Neon;
- [x] liveness exclui o banco e startup concede tempo para a conexão inicial;
- [x] readiness inclui o indicador JDBC;
- [ ] o schema Neon foi migrado pelo gate antes do deploy;
- [ ] a API ficou pronta usando endpoint JDBC direto com TLS obrigatório.

Siga [`deploy-kubernetes-local.md`](deploy-kubernetes-local.md) sem adicionar
PostgreSQL ao cluster.

## 4. Smoke autenticado

- [ ] HTTP redireciona para HTTPS;
- [ ] certificado é aceito sem `--insecure`;
- [ ] autenticação, cookie e CSRF funcionam;
- [ ] paciente, agenda, consulta, adendo, prontuário, anexo e auditoria passam;
- [ ] a recriação do pod da API preserva dados no Neon e binários no PVC;
- [x] o script não executa comando em pod PostgreSQL nem tenta reiniciá-lo.

```bash
./scripts/smoke-kubernetes.sh
```

## 5. Cópia e recuperação

- [x] o procedimento reconhece Neon e PVC de anexos como fronteiras separadas;
- [x] nenhuma etapa usa pod PostgreSQL;
- [x] credenciais administrativas permanecem fora do cluster;
- [ ] o ponto recuperável Neon e o arquivo de anexos pertencem à mesma janela de
      API parada;
- [ ] o SHA-256 do arquivo de anexos foi conferido;
- [ ] readiness, smoke e download de anexo passaram após a recuperação.

Use [`backup-restore.md`](backup-restore.md). O SQL Drizzle reconstrói schema
vazio, mas não substitui cópia de dados.

## 6. Evidência de retirada do legado

Em 24 de julho de 2026, antes da remoção, a equivalência Flyway V1–V16 × Drizzle
e a integração Spring sobre schema Drizzle foram aprovadas em PostgreSQL 18.4
descartável. Essa evidência autorizou retirar Flyway, V1–V16 e a topologia
PostgreSQL in-cluster.

Registre abaixo somente resultados executados no checkout final da retirada:

| Verificação | Resultado observado |
| --- | --- |
| contrato de remoção | verde, 3 testes focados |
| pacote `database/` | verde, typecheck e 3 testes PostgreSQL 18 |
| construção Maven completa | verde, 205 testes, Spotless e Checkstyle |
| manifests e smoke | YAML e sintaxe Bash verdes; validação contra API Kubernetes requer cluster |
