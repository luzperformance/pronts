# Cópia de segurança e restauração conjunta

O estado recuperável é um par inseparável:

1. cópia lógica do PostgreSQL, incluindo `flyway_schema_history` e metadados dos anexos;
2. cópia do PVC de anexos, incluindo `content/` e `staging/`.

Restaurar somente um lado pode produzir metadados sem binário ou binários sem
metadado. Os comandos abaixo são manuais e adequados apenas ao cluster
descartável de demonstração. Não constituem política de cópia de segurança de produção,
criptografia, retenção, agendamento ou recuperação de desastres.

> Execute somente com dados fictícios ou anonimizados. O diretório `backups/` é
> ignorado pelo Git, mas continua contendo material sensível da demonstração.

## Criar uma captura coordenada

Confirme que o namespace está saudável e escolha um diretório novo:

```bash
kubectl get deployment,statefulset,pvc --namespace primeiro-prontuario
pp_backup_dir="backups/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$pp_backup_dir"
set -o pipefail
```

Interrompa a única API antes de copiar qualquer lado. O banco continua ativo,
mas sem escritores da aplicação:

```bash
kubectl scale deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --replicas=0
kubectl rollout status deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --timeout=180s
```

Registre um inventário independente dos anexos ativos e gere a cópia lógica no formato
personalizado do PostgreSQL:

```bash
kubectl exec postgresql-0 --namespace primeiro-prontuario -- \
  sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" psql \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --tuples-only --no-align \
    --command "SELECT id, storage_key, sha256 FROM attachment WHERE status = '\''ACTIVE'\'' ORDER BY id"' |
  dd of="$pp_backup_dir/active-attachments.txt" status=none

kubectl exec postgresql-0 --namespace primeiro-prontuario -- \
  sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --format=custom \
    --no-owner' |
  dd of="$pp_backup_dir/database.dump" status=none
```

Crie um pod temporário com o PVC de anexos. Ele usa a mesma imagem, UID e grupo
da API e não recebe credenciais:

```bash
kubectl apply -f - <<'YAML'
apiVersion: v1
kind: Pod
metadata:
  name: attachment-maintenance
  namespace: primeiro-prontuario
spec:
  restartPolicy: Never
  automountServiceAccountToken: false
  securityContext:
    fsGroup: 10001
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: maintenance
      image: primeiro-prontuario-api:0.0.1
      command: [sh, -c, "sleep 3600"]
      securityContext:
        allowPrivilegeEscalation: false
        capabilities:
          drop: [ALL]
        runAsNonRoot: true
        runAsUser: 10001
      volumeMounts:
        - name: attachments
          mountPath: /var/lib/primeiro-prontuario/attachments
  volumes:
    - name: attachments
      persistentVolumeClaim:
        claimName: primeiro-prontuario-attachments
YAML
kubectl wait pod/attachment-maintenance \
  --namespace primeiro-prontuario \
  --for=condition=Ready \
  --timeout=120s
kubectl exec -i attachment-maintenance --namespace primeiro-prontuario -- \
  tar -C /var/lib/primeiro-prontuario/attachments -czf - application |
  dd of="$pp_backup_dir/attachments.tar.gz" status=none
kubectl delete pod attachment-maintenance \
  --namespace primeiro-prontuario \
  --wait=true
```

Finalize o conjunto com somas de verificação e reative a API:

```bash
(
  cd "$pp_backup_dir"
  sha256sum database.dump attachments.tar.gz active-attachments.txt \
    >SHA256SUMS
)
kubectl scale deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --replicas=1
kubectl rollout status deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --timeout=180s
```

Não altere individualmente os três arquivos protegidos por `SHA256SUMS`.

## Restaurar em ambiente descartável

Nunca ensaie sobre o único ambiente que precisa ser preservado. Use um cluster
local descartável ou apague o namespace apenas após confirmar que a captura é
da demonstração fictícia correta.

Verifique o conjunto antes de modificar o destino:

```bash
pp_backup_dir='backups/AAAAmmddTHHMMSSZ'
(cd "$pp_backup_dir" && sha256sum --check SHA256SUMS)
```

Prepare um namespace vazio seguindo
[`deploy-kubernetes-local.md`](deploy-kubernetes-local.md), mas aplique somente
namespace, Secret de credenciais, ConfigMap, PVC de anexos e PostgreSQL. Não
inicie a API ainda:

```bash
kubectl apply -f deploy/kubernetes/00-namespace.yaml
# recrie primeiro-prontuario-credentials como documentado no guia de implantação
kubectl apply -f deploy/kubernetes/10-configmap.yaml
kubectl apply -f deploy/kubernetes/20-attachment-pvc.yaml
kubectl apply -f deploy/kubernetes/30-postgresql.yaml
kubectl rollout status statefulset/postgresql \
  --namespace primeiro-prontuario \
  --timeout=180s
```

Restaure a cópia lógica no banco vazio:

```bash
dd if="$pp_backup_dir/database.dump" status=none |
  kubectl exec -i postgresql-0 --namespace primeiro-prontuario -- \
  sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --clean --if-exists --no-owner --exit-on-error'
```

Recrie o pod `attachment-maintenance` do procedimento de cópia de segurança e restaure o
arquivo no PVC vazio:

```bash
kubectl wait pod/attachment-maintenance \
  --namespace primeiro-prontuario \
  --for=condition=Ready \
  --timeout=120s
dd if="$pp_backup_dir/attachments.tar.gz" status=none |
  kubectl exec -i attachment-maintenance --namespace primeiro-prontuario -- \
    tar -C /var/lib/primeiro-prontuario/attachments -xzf -
```

Compare cada resumo criptográfico ativo do banco com o binário restaurado:

```bash
dd if="$pp_backup_dir/active-attachments.txt" status=none |
  kubectl exec -i attachment-maintenance --namespace primeiro-prontuario -- \
  sh -ec '
    while IFS="|" read -r attachment_id storage_key expected_sha; do
      [ -z "$attachment_id" ] && continue
      actual_sha="$(sha256sum \
        "/var/lib/primeiro-prontuario/attachments/application/content/$storage_key" |
        cut -d " " -f 1)"
      [ "$actual_sha" = "$expected_sha" ] || exit 1
    done
  '
kubectl delete pod attachment-maintenance \
  --namespace primeiro-prontuario \
  --wait=true
```

Só depois aplique API, middleware e Ingress:

```bash
kubectl apply -f deploy/kubernetes/40-api.yaml
kubectl apply -f deploy/kubernetes/50-traefik-middleware.yaml
kubectl apply -f deploy/kubernetes/60-ingress.yaml
kubectl rollout status deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --timeout=180s
./scripts/smoke-kubernetes.sh
```

O ensaio está aprovado apenas quando `pg_restore`, a conferência de todos os
SHA-256 e o teste de fumaça autenticado terminarem com código zero.

## Falhas que invalidam o ensaio

- a cópia lógica e o arquivo tar não pertencem ao mesmo período de API parada;
- `SHA256SUMS` falha;
- falta qualquer `storage_key` ativo ou o resumo criptográfico diverge;
- Flyway rejeita o histórico restaurado;
- API ou PostgreSQL foram expostos fora de `ClusterIP`;
- o teste usou dados reais;
- o procedimento restaurou somente banco ou somente binários.
