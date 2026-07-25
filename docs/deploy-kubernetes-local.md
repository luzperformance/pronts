# Implantação Kubernetes local

Este laboratório executa Traefik, a API, PostgreSQL 18 e os dois volumes
persistentes na máquina de desenvolvimento. Não usa banco gerenciado, conexão
externa nem credencial de nuvem.

A topologia é propositalmente pequena:

- PostgreSQL 18 em `StatefulSet` de uma réplica;
- banco e API expostos apenas por `Service` `ClusterIP`;
- um PVC `ReadWriteOnce` para PostgreSQL e outro para anexos;
- roles separadas de bootstrap, migração e runtime;
- API em `Deployment` de uma réplica com estratégia `Recreate`;
- Traefik como única entrada, com HTTP redirecionado para HTTPS local.

## Caminho automatizado

O caminho suportado cria um cluster k3d descartável, gera credenciais e TLS em
`.local-runtime/`, constrói a imagem, aplica os manifests e executa o smoke:

```bash
sg docker -c './scripts/create-local-kubernetes.sh'
```

O script aplica, nesta ordem:

1. `deploy/kubernetes/00-namespace.yaml`;
2. Secret local criado a partir de `.local-runtime/credentials.env`;
3. `deploy/kubernetes/local/10-configmap.yaml`;
4. `deploy/kubernetes/20-attachment-pvc.yaml`;
5. `deploy/kubernetes/local/25-postgresql-init.yaml`;
6. `deploy/kubernetes/local/30-postgresql.yaml`;
7. `deploy/kubernetes/40-api.yaml`;
8. Traefik, TLS e Ingress local.

O script de inicialização do PostgreSQL cria as roles e revoga de `PUBLIC`
`CONNECT`, `TEMPORARY` e criação no schema. A role de migração recebe somente o
necessário para executar Flyway. Privilégios padrão concedem DML à role de
runtime sobre objetos criados pela migração. A API recebe os dois conjuntos de
credenciais: Flyway usa `MIGRATION_DB_*`; JPA e Hikari usam `DB_*`.

## Verificação

Consulte a topologia sem expor segredos:

```bash
kubectl config current-context
kubectl get pods,service,pvc,ingress --namespace primeiro-prontuario
kubectl get statefulset,deployment --namespace primeiro-prontuario
curl --cacert .local-runtime/tls/ca.crt \
  https://prontuario.localhost/actuator/health
```

O smoke autentica pela API REST, cria paciente, agenda, consulta, adendo e
anexo, consulta auditoria e prontuário e, em seguida, recria os pods da API e do
PostgreSQL. As mesmas consultas e o download do anexo devem continuar
funcionando após cada reinício:

```bash
PP_URL='https://prontuario.localhost' \
PP_CA_CERT='.local-runtime/tls/ca.crt' \
./scripts/smoke-kubernetes.sh
```

## Limites

Use somente dados fictícios. PVC prova persistência durante reinício de pod,
mas não substitui backup. O laboratório não inclui Helm, operador PostgreSQL,
alta disponibilidade, serviço `NodePort`/`LoadBalancer`, domínio público nem
infraestrutura de nuvem.

Para remover todo o ambiente descartável:

```bash
.local-tools/k3d cluster delete primeiro-prontuario-local
```
