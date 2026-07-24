# PP-021 — Deploy simples com Docker, Kubernetes e Traefik

## Resultado

Implantar a API containerizada em uma topologia Kubernetes local mínima,
persistente e acessível na máquina de desenvolvimento somente por HTTPS através
do Traefik.

## Dependências

- PP-020.

## Escopo

- criar imagem Docker multi-stage, reproduzível e executada como usuário não
  root;
- excluir ferramentas de build, código-fonte e credenciais da imagem final;
- executar toda a topologia de demonstração em cluster Kubernetes local, sem
  depender de cloud ou serviço gerenciado;
- criar manifests diretos para uma réplica da API em `Deployment`;
- usar estratégia `Recreate`, compatível com sessão local, uma réplica e PVC
  `ReadWriteOnce`;
- criar `Service` `ClusterIP`, sem exposição direta;
- separar configuração não sensível em `ConfigMap` e credenciais em referências
  a `Secret`, mantendo somente exemplos sem valores reais no repositório;
- montar PVC privado para anexos;
- criar a topologia local do PostgreSQL 18, no mesmo cluster, em `StatefulSet`
  com uma réplica, `Service` interno e PVC próprio;
- configurar probes de startup, readiness e liveness no mesmo
  `/actuator/health`, sem detalhes sensíveis;
- criar `Ingress` para um Traefik disponível no cluster local, com
  redirecionamento HTTP → HTTPS e terminação TLS;
- configurar host local e referência a `Secret` TLS sem versionar a chave
  privada;
- validar forwarded headers e cookies `Secure` atrás do Traefik;
- executar smoke test que inclua reinício de pod e persistência.

## Fora do escopo

- instalar ou administrar o cluster ou o Traefik;
- cloud, hospedagem gerenciada, banco externo, DNS público ou certificado
  emitido por autoridade pública;
- Helm, Kustomize, operadores, HPA, múltiplas réplicas, GitOps ou service
  mesh;
- alta disponibilidade, rolling update sem indisponibilidade ou recuperação
  multirregião;
- publicar PostgreSQL, anexos ou Actuator detalhado;
- colocar valores reais de Secret no repositório.

## Critérios de aceitação

- a imagem inicia a API como usuário não root e passa no smoke test;
- o `Deployment` mantém uma réplica e usa `Recreate`;
- somente o Traefik é acessível a partir da máquina local; API e PostgreSQL usam
  `ClusterIP`;
- o pod fica pronto apenas depois de aplicação e banco estarem disponíveis;
- as três probes não criam endpoints operacionais adicionais;
- configuração não sensível e secreta respeitam as fronteiras definidas;
- anexos e dados do PostgreSQL sobrevivem à recriação dos respectivos pods;
- HTTP redireciona para HTTPS e o host apresenta o certificado local
  configurado;
- a documentação explica como confiar no certificado apenas na máquina de
  demonstração;
- login, cookie de sessão e CSRF funcionam pela URL HTTPS local;
- a aplicação não confia em forwarded headers vindos de uma rota pública direta;
- os manifests são pequenos, legíveis e não introduzem os componentes excluídos;
- a documentação deixa explícito que PVC não substitui backup.

## Estratégia de validação

- construir a imagem a partir de checkout limpo;
- subir os manifests em cluster Kubernetes local descartável compatível com
  Traefik;
- executar o seam REST pela URL HTTPS local;
- recriar pods e repetir consulta e download previamente persistidos;
- inspecionar imagem e manifests por execução não root, exposição e ausência de
  segredos;
- não criar um terceiro seam de teste para Kubernetes.

## Requisitos

RNF-008, RNF-012, RNF-019, RNF-033–040; CA-017.
