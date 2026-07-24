# Kubernetes integralmente local

Este laboratório cria localmente um cluster k3d com Traefik, PostgreSQL 18,
TLS, PVCs, migração Drizzle e a API. Nenhuma conta, banco, domínio ou credencial
da Hostinger ou do Neon é utilizada.

O ambiente usa:

- contexto Kubernetes `k3d-primeiro-prontuario-local`;
- URL `https://prontuario.localhost`;
- resolução automática de `*.localhost`, sem editar `/etc/hosts`;
- credenciais aleatórias em `.local-runtime/credentials.env`, ignoradas pelo
  Git;
- CA e chave TLS locais em `.local-runtime/tls/`, também ignoradas;
- k3d `v5.9.0` em `.local-tools/`, validado por SHA-256 antes da instalação.

## Criar e validar

Docker precisa estar acessível na sessão. Neste ambiente, execute:

```bash
sg docker -c './scripts/create-local-kubernetes.sh'
```

O script é idempotente para o cluster existente. Ele:

1. baixa e valida o binário local do k3d, quando necessário;
2. cria o cluster e seleciona o contexto;
3. constrói e importa a imagem da API;
4. gera credenciais e certificado exclusivamente locais;
5. inicia o PostgreSQL com roles separadas de bootstrap, migração e runtime;
6. aplica o baseline Drizzle por um `port-forward` temporário;
7. implanta API, PVC de anexos, Traefik e TLS;
8. executa o smoke REST, recriando os pods da API e do PostgreSQL para comprovar
   persistência.

O comando termina imprimindo somente o contexto, a URL e o caminho da CA. Ele
não imprime senhas.

Consulte o estado com:

```bash
kubectl config current-context
kubectl get pods,service,pvc,ingress --namespace primeiro-prontuario
curl --cacert .local-runtime/tls/ca.crt \
  https://prontuario.localhost/actuator/health
```

O projeto é somente uma API; não existe interface web. Para um navegador confiar
no certificado, importe apenas `.local-runtime/tls/ca.crt` no repositório local
de certificados do navegador. Nunca importe nem compartilhe as chaves
`.local-runtime/tls/*.key`.

## Remover

O cluster e seus volumes são descartáveis:

```bash
.local-tools/k3d cluster delete primeiro-prontuario-local
```

Depois, se desejar, remova manualmente `.local-runtime/` e `.local-tools/`.
Esses diretórios não são versionados.
