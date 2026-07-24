# Cópia de segurança e recuperação

O estado recuperável atravessa duas fronteiras independentes:

1. banco PostgreSQL mantido no Neon, com metadados dos anexos;
2. binários dos anexos no PVC `primeiro-prontuario-attachments`.

O cluster não executa PostgreSQL e não possui credencial proprietária ou de
migração. Portanto, nenhum procedimento de cópia ou recuperação pode executar
`pg_dump`, `pg_restore` ou `psql` dentro de pod PostgreSQL. O SQL Drizzle
versionado reconstrói schema vazio, mas não substitui cópia dos dados.

Este documento delimita o procedimento manual do MVP acadêmico. Retenção,
criptografia de infraestrutura, automação, recuperação multirregião e política
de desastre continuam fora do escopo.

> Execute somente com dados fictícios ou previamente anonimizados. Identificadores
> operacionais podem ser registrados; URLs, usuários, senhas e conteúdo clínico
> não podem entrar no Git, em evidências ou no histórico de shell.

## Captura coordenada

1. Confirme que a API está pronta e anote um instante UTC.
2. Escale a única réplica da API para zero, impedindo novas mutações.
3. Pelo mecanismo autorizado do Neon, crie ou marque o ponto recuperável do
   banco `production`. Não use a role runtime para administração.
4. Copie o conteúdo do PVC de anexos com um pod temporário sem credenciais.
5. Registre juntos o instante UTC, a referência opaca do ponto Neon e o SHA-256
   do arquivo de anexos.
6. Remova o pod temporário e reative a API.

```bash
kubectl scale deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --replicas=0

pp_backup_dir="backups/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$pp_backup_dir"

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
  tar -C /var/lib/primeiro-prontuario/attachments -czf - application \
  >"$pp_backup_dir/attachments.tar.gz"
sha256sum "$pp_backup_dir/attachments.tar.gz" \
  >"$pp_backup_dir/attachments.sha256"
kubectl delete pod attachment-maintenance \
  --namespace primeiro-prontuario \
  --wait=true

kubectl scale deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --replicas=1
kubectl rollout status deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --timeout=180s
```

O registro do ponto Neon deve ficar no cofre operacional junto do arquivo de
anexos, não em um manifesto Kubernetes. Se qualquer lado falhar durante a janela
da API parada, descarte a captura incompleta e reative a aplicação.

## Recuperação coordenada

Faça o ensaio somente em destino descartável e com autorização explícita:

1. mantenha a API parada;
2. restaure o banco pelo mecanismo autorizado do Neon para o ponto registrado;
3. confirme que o destino usa PostgreSQL 18 e contém o histórico
   `drizzle.__drizzle_migrations`;
4. restaure `attachments.tar.gz` no PVC vazio usando o mesmo pod temporário;
5. aplique os manifests da API, middleware e Ingress;
6. aguarde liveness e readiness; a readiness comprova a conexão JDBC;
7. execute o smoke autenticado e confira leitura de metadados e binários.

O Spring recebe somente a nova conexão da role runtime. Não execute
`npm run migrate` sobre a restauração e não disponibilize credencial proprietária
ou de migração ao cluster.

```bash
(cd "$pp_backup_dir" && sha256sum --check attachments.sha256)

kubectl apply -f deploy/kubernetes/00-namespace.yaml
# recrie primeiro-prontuario-credentials como documentado no guia de implantação
kubectl apply -f deploy/kubernetes/10-configmap.yaml
kubectl apply -f deploy/kubernetes/20-attachment-pvc.yaml

# recrie attachment-maintenance com o manifesto da seção anterior
kubectl wait pod/attachment-maintenance \
  --namespace primeiro-prontuario \
  --for=condition=Ready \
  --timeout=120s
tar -xOzf "$pp_backup_dir/attachments.tar.gz" >/dev/null
kubectl exec -i attachment-maintenance --namespace primeiro-prontuario -- \
  tar -C /var/lib/primeiro-prontuario/attachments -xzf - \
  <"$pp_backup_dir/attachments.tar.gz"
kubectl delete pod attachment-maintenance \
  --namespace primeiro-prontuario \
  --wait=true

kubectl apply -f deploy/kubernetes/40-api.yaml
kubectl apply -f deploy/kubernetes/50-traefik-middleware.yaml
kubectl apply -f deploy/kubernetes/60-ingress.yaml
kubectl rollout status deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --timeout=180s
./scripts/smoke-kubernetes.sh
```

## Condições que invalidam o ensaio

- o ponto Neon e o arquivo de anexos não pertencem à mesma janela de API parada;
- o SHA-256 do arquivo de anexos falha;
- o destino não contém o histórico Drizzle esperado;
- banco ou anexos foram recuperados isoladamente;
- uma credencial proprietária ou de migração entrou no cluster;
- o procedimento tentou iniciar ou acessar PostgreSQL dentro do cluster;
- readiness, smoke autenticado ou download de anexo falhou;
- o teste usou dados reais.
