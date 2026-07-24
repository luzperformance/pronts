# Implantação Kubernetes local

Este laboratório executa a API e o PostgreSQL 18 dentro de um cluster Kubernetes
local já existente. O Traefik instalado no cluster é a única entrada acessível
pela máquina. A API e o PostgreSQL permanecem em `Service` do tipo `ClusterIP`.

O procedimento não instala nem administra Kubernetes, Traefik ou a classe de
armazenamento. Use um cluster descartável ou dedicado à demonstração. São
necessários Docker, `kubectl`, OpenSSL, Bash, `curl`, `jq`, `base64`, `cmp` e um
mecanismo da distribuição do cluster para importar imagens locais.

Antes de começar, confirme:

```bash
kubectl cluster-info
kubectl get ingressclass traefik
kubectl get crd middlewares.traefik.io
kubectl get storageclass
```

O `IngressClass` deve se chamar `traefik`, o CRD
`middlewares.traefik.io` deve existir e alguma `StorageClass` padrão deve
provisionar PVCs `ReadWriteOnce`. Os pontos de entrada do Traefik devem se chamar
`web` e `websecure` e estar acessíveis pela máquina local. Se esses nomes forem
diferentes, adapte somente os manifestos de Ingress ao cluster documentado.

Também confirme que a cópia de trabalho não contém credenciais nem artefatos de uma
demonstração anterior:

```bash
git status --short
git grep -n 'REPLACE_WITH_LOCAL_' -- deploy/kubernetes
```

## 1. Construir e disponibilizar a imagem

Parta de uma cópia de trabalho limpa e execute:

```bash
docker build --pull --tag primeiro-prontuario-api:0.0.1 .
docker inspect primeiro-prontuario-api:0.0.1 \
  --format '{{.Config.User}} {{json .Config.Entrypoint}}'
```

O resultado deve começar com `10001:10001`. As imagens de construção e execução estão
fixadas por resumo criptográfico, Maven e Maven Wrapper têm versão fixa, o Wrapper valida a
soma de verificação da distribuição e o JAR usa carimbo de data e hora reproduzível. O estágio final
contém somente o JRE e o JAR; não recebe código-fonte, Maven ou credenciais.

Disponibilize essa imagem ao cluster pelo mecanismo já oferecido pela ferramenta
local. Por exemplo, um cluster `kind` usa `kind load docker-image`; um cluster
`k3d` usa `k3d image import`. A criação e a administração do cluster continuam
fora deste ticket.

## 2. Criar credenciais locais

O arquivo versionado contém somente nomes de chaves e marcadores:

```bash
cp deploy/kubernetes/credentials.example.env \
  deploy/kubernetes/credentials.local.env
```

Edite `credentials.local.env` com valores exclusivos desta demonstração. O
arquivo local é ignorado pelo Git. Crie o `Secret` sem gravar seus valores em um
manifesto:

```bash
kubectl apply -f deploy/kubernetes/00-namespace.yaml
kubectl create secret generic primeiro-prontuario-credentials \
  --namespace primeiro-prontuario \
  --from-env-file=deploy/kubernetes/credentials.local.env \
  --dry-run=client \
  --output yaml | kubectl apply -f -
```

Não use dados, senhas ou anexos reais neste ambiente de estudo.

## 3. Criar certificado TLS local

Crie uma CA e um certificado somente na máquina de demonstração. A pasta inteira
é ignorada pelo Git:

```bash
mkdir -p deploy/tls
openssl genrsa -out deploy/tls/ca.key 3072
openssl req -x509 -new -sha256 -days 3650 \
  -key deploy/tls/ca.key \
  -subj '/CN=Primeiro Prontuario Local CA' \
  -out deploy/tls/ca.crt
openssl genrsa -out deploy/tls/server.key 3072
openssl req -new -sha256 \
  -key deploy/tls/server.key \
  -subj '/CN=prontuario.local' \
  -addext 'subjectAltName=DNS:prontuario.local' \
  -out deploy/tls/server.csr
openssl x509 -req -sha256 -days 825 \
  -in deploy/tls/server.csr \
  -CA deploy/tls/ca.crt \
  -CAkey deploy/tls/ca.key \
  -CAcreateserial \
  -copy_extensions copy \
  -out deploy/tls/server.crt
chmod 600 deploy/tls/ca.key deploy/tls/server.key
kubectl create secret tls primeiro-prontuario-local-tls \
  --namespace primeiro-prontuario \
  --cert=deploy/tls/server.crt \
  --key=deploy/tls/server.key \
  --dry-run=client \
  --output yaml | kubectl apply -f -
```

Confie somente em `deploy/tls/ca.crt`, apenas nesta máquina. Em uma distribuição
Linux baseada em Debian/Ubuntu:

```bash
sudo cp deploy/tls/ca.crt \
  /usr/local/share/ca-certificates/primeiro-prontuario-local.crt
sudo update-ca-certificates
```

Após a demonstração, remova esse arquivo do diretório de CAs e execute
`sudo update-ca-certificates` novamente. Navegadores com repositório próprio de
certificados também precisam importar e depois remover a mesma CA local.

Associe `prontuario.local` ao endereço pelo qual o Traefik do cluster já é
acessível. Em clusters que publicam o Traefik no loopback, a entrada é:

```text
127.0.0.1 prontuario.local
```

Não crie `NodePort`, `LoadBalancer`, `hostPort` ou `port-forward` para a API ou
para o PostgreSQL.

## 4. Aplicar a topologia

Depois dos dois Secrets:

```bash
kubectl apply -f deploy/kubernetes/10-configmap.yaml
kubectl apply -f deploy/kubernetes/20-attachment-pvc.yaml
kubectl apply -f deploy/kubernetes/30-postgresql.yaml
kubectl apply -f deploy/kubernetes/40-api.yaml
kubectl apply -f deploy/kubernetes/50-traefik-middleware.yaml
kubectl apply -f deploy/kubernetes/60-ingress.yaml
kubectl rollout status statefulset/postgresql \
  --namespace primeiro-prontuario \
  --timeout=180s
kubectl rollout status deployment/primeiro-prontuario-api \
  --namespace primeiro-prontuario \
  --timeout=180s
```

O PostgreSQL é exatamente a série 18 e recebe um PVC próprio pelo
`volumeClaimTemplates` do `StatefulSet`. Os binários recebem o PVC
`primeiro-prontuario-attachments`. Eles são privados, distintos e
`ReadWriteOnce`; nunca monte um no caminho do outro.

As sondas de inicialização, prontidão e vitalidade da API usam somente
`/actuator/health`. A rota responde sem componentes ou detalhes. A
sonda de prontidão só fica saudável quando a aplicação iniciou e o indicador da
fonte de dados confirma a conexão com o PostgreSQL.

Os cabeçalhos encaminhados ficam desabilitados por padrão. Este `Deployment` ativa o perfil
`prod`, que usa a integração nativa do Tomcat e aceita os cabeçalhos somente quando
o endereço do proxy corresponde à expressão privada
`TRUSTED_PROXY_NETWORKS`. Ajuste essa expressão se o pod do Traefik usar outra
rede privada. O `Service` da API não tem exposição pública; uma conexão direta
fora da rede confiável não consegue transformar um cabeçalho forjado em origem
HTTPS. O Traefik informa o esquema HTTPS e a aplicação emite os cookies de
sessão e CSRF com `Secure`.

Em banco vazio, a inicialização aplica V1–V16 e o Hibernate valida o resultado. Se a
soma de verificação ou o histórico Flyway for incompatível, o pod não fica pronto; não use
`repair`, `baseline` ou `ddl-auto` para mascarar a divergência.

## 5. Executar o teste de fumaça com persistência

O teste de fumaça usa a fronteira REST existente. Ele verifica redirecionamento para HTTPS,
certificado, saúde, autenticação, atributos do cookie, CSRF, paciente, agenda,
consulta, finalização, adendo, prontuário, anexo, baixamento e auditoria. Em
seguida recria separadamente o pod da API e o pod do PostgreSQL e repete consulta
e baixamento:

```bash
chmod +x scripts/smoke-kubernetes.sh
./scripts/smoke-kubernetes.sh
```

Inspecione também a fronteira de rede e os volumes:

```bash
kubectl get service,pvc,ingress --namespace primeiro-prontuario
kubectl get deployment,statefulset --namespace primeiro-prontuario
kubectl get pods --namespace primeiro-prontuario \
  --output custom-columns=NAME:.metadata.name,USER:.spec.containers[*].securityContext.runAsUser
```

Os PVCs preservam dados durante a recriação de pods, mas **PVC não é cópia de segurança**.
O procedimento manual e o ensaio descartável de cópia conjunta do PostgreSQL e
dos anexos estão em [`backup-restore.md`](backup-restore.md). Automatizar
cópias de segurança, criptografia de infraestrutura e recuperação fica fora deste
laboratório.

## 6. Remover a demonstração

Depois de preservar somente a evidência fictícia necessária, remova o namespace
do cluster descartável:

```bash
kubectl delete namespace primeiro-prontuario
```

Remova também a entrada de `prontuario.local`, a confiança na CA conforme a
seção 3, os arquivos em `deploy/tls/`, as credenciais locais e quaisquer
capturas em `backups/`. A chave privada da CA nunca deve sair da máquina de
demonstração.
