#!/usr/bin/env bash
set -euo pipefail

readonly PP_CLUSTER_NAME="primeiro-prontuario-local"
readonly PP_CONTEXT_NAME="k3d-${PP_CLUSTER_NAME}"
readonly PP_IMAGE="primeiro-prontuario-api:0.0.1"
readonly PP_NAMESPACE="primeiro-prontuario"
readonly PP_URL="https://prontuario.localhost"
readonly PP_K3D_VERSION="v5.9.0"
readonly PP_K3D_LINUX_AMD64_SHA256="06d8f25bc3a971c4eb29e0ff08429b180402db0f4dec838c9eac427e296800a0"

pp_repository_root="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1
  pwd
)"
readonly pp_repository_root
readonly pp_runtime_directory="$pp_repository_root/.local-runtime"
readonly pp_tools_directory="$pp_repository_root/.local-tools"
readonly pp_k3d="$pp_tools_directory/k3d"
readonly pp_credentials_file="$pp_runtime_directory/credentials.env"
readonly pp_tls_directory="$pp_runtime_directory/tls"

for pp_command in awk base64 cmp curl date docker grep jq kubectl openssl sha256sum; do
  command -v "$pp_command" >/dev/null || {
    echo "Comando obrigatório ausente: $pp_command" >&2
    exit 1
  }
done

docker info >/dev/null 2>&1 || {
  echo "Docker não está acessível. Execute: sg docker -c './scripts/create-local-kubernetes.sh'" >&2
  exit 1
}

mkdir -p "$pp_runtime_directory" "$pp_tools_directory" "$pp_tls_directory"

if [[ ! -x "$pp_k3d" ]]; then
  [[ "$(uname -m)" == "x86_64" ]] || {
    echo "O bootstrap automático do k3d suporta somente Linux x86_64." >&2
    exit 1
  }

  pp_k3d_download="$pp_tools_directory/k3d.download"
  curl --fail --location --silent --show-error \
    "https://github.com/k3d-io/k3d/releases/download/${PP_K3D_VERSION}/k3d-linux-amd64" \
    --output "$pp_k3d_download"
  printf '%s  %s\n' "$PP_K3D_LINUX_AMD64_SHA256" "$pp_k3d_download" |
    sha256sum --check --status
  chmod 0755 "$pp_k3d_download"
  mv "$pp_k3d_download" "$pp_k3d"
fi

if ! "$pp_k3d" cluster list --no-headers |
  awk '{print $1}' |
  grep --fixed-strings --line-regexp --quiet "$PP_CLUSTER_NAME"; then
  "$pp_k3d" cluster create "$PP_CLUSTER_NAME" \
    --agents 0 \
    --port "127.0.0.1:80:80@loadbalancer" \
    --port "127.0.0.1:443:443@loadbalancer" \
    --servers 1 \
    --wait
fi

"$pp_k3d" kubeconfig merge "$PP_CLUSTER_NAME" --kubeconfig-switch-context
kubectl config use-context "$PP_CONTEXT_NAME" >/dev/null

for ((pp_attempt = 1; pp_attempt <= 60; pp_attempt++)); do
  if kubectl get ingressclass traefik >/dev/null 2>&1 &&
    kubectl get crd middlewares.traefik.io >/dev/null 2>&1; then
    break
  fi
  if [[ "$pp_attempt" == "60" ]]; then
    echo "Traefik não ficou disponível no cluster local." >&2
    exit 1
  fi
  sleep 2
done

docker build --pull --tag "$PP_IMAGE" "$pp_repository_root"
"$pp_k3d" image import "$PP_IMAGE" --cluster "$PP_CLUSTER_NAME"

if [[ ! -f "$pp_credentials_file" ]]; then
  umask 077
  {
    printf 'bootstrap-username=primeiro_prontuario_admin\n'
    printf 'bootstrap-password=%s\n' "$(openssl rand -hex 24)"
    printf 'migration-username=primeiro_prontuario_migration\n'
    printf 'migration-password=%s\n' "$(openssl rand -hex 24)"
    printf 'database-url=jdbc:postgresql://postgresql:5432/primeiro_prontuario\n'
    printf 'database-username=primeiro_prontuario_runtime\n'
    printf 'database-password=%s\n' "$(openssl rand -hex 24)"
    printf 'doctor-username=doctor\n'
    printf 'doctor-password=%s\n' "$(openssl rand -hex 24)"
  } >"$pp_credentials_file"
fi

kubectl apply -f "$pp_repository_root/deploy/kubernetes/00-namespace.yaml"
kubectl create secret generic primeiro-prontuario-credentials \
  --namespace "$PP_NAMESPACE" \
  --from-env-file="$pp_credentials_file" \
  --dry-run=client \
  --output yaml |
  kubectl apply -f -

if [[ ! -f "$pp_tls_directory/ca.crt" ||
  ! -f "$pp_tls_directory/server.crt" ||
  ! -f "$pp_tls_directory/server.key" ]]; then
  umask 077
  openssl genrsa -out "$pp_tls_directory/ca.key" 3072
  openssl req -x509 -new -sha256 -days 3650 \
    -key "$pp_tls_directory/ca.key" \
    -subj "/CN=Primeiro Prontuario Local CA" \
    -out "$pp_tls_directory/ca.crt"
  openssl genrsa -out "$pp_tls_directory/server.key" 3072
  openssl req -new -sha256 \
    -key "$pp_tls_directory/server.key" \
    -subj "/CN=prontuario.localhost" \
    -addext "subjectAltName=DNS:prontuario.localhost" \
    -out "$pp_tls_directory/server.csr"
  openssl x509 -req -sha256 -days 825 \
    -in "$pp_tls_directory/server.csr" \
    -CA "$pp_tls_directory/ca.crt" \
    -CAkey "$pp_tls_directory/ca.key" \
    -CAcreateserial \
    -copy_extensions copy \
    -out "$pp_tls_directory/server.crt"
fi

kubectl create secret tls primeiro-prontuario-local-tls \
  --namespace "$PP_NAMESPACE" \
  --cert="$pp_tls_directory/server.crt" \
  --key="$pp_tls_directory/server.key" \
  --dry-run=client \
  --output yaml |
  kubectl apply -f -

kubectl apply -f "$pp_repository_root/deploy/kubernetes/local/10-configmap.yaml"
kubectl apply -f "$pp_repository_root/deploy/kubernetes/20-attachment-pvc.yaml"
kubectl apply -f "$pp_repository_root/deploy/kubernetes/local/25-postgresql-init.yaml"
kubectl apply -f "$pp_repository_root/deploy/kubernetes/local/30-postgresql.yaml"
kubectl rollout status statefulset/postgresql \
  --namespace "$PP_NAMESPACE" \
  --timeout=180s

kubectl apply -f "$pp_repository_root/deploy/kubernetes/40-api.yaml"
kubectl apply -f "$pp_repository_root/deploy/kubernetes/50-traefik-middleware.yaml"
kubectl apply -f "$pp_repository_root/deploy/kubernetes/local/60-ingress.yaml"
kubectl rollout restart deployment/primeiro-prontuario-api \
  --namespace "$PP_NAMESPACE"
kubectl rollout status deployment/primeiro-prontuario-api \
  --namespace "$PP_NAMESPACE" \
  --timeout=300s

env \
  PP_URL="$PP_URL" \
  PP_CA_CERT="$pp_tls_directory/ca.crt" \
  "$pp_repository_root/scripts/smoke-kubernetes.sh"

printf 'Contexto Kubernetes: %s\n' "$PP_CONTEXT_NAME"
printf 'URL local: %s\n' "$PP_URL"
printf 'CA local: %s\n' "$pp_tls_directory/ca.crt"
