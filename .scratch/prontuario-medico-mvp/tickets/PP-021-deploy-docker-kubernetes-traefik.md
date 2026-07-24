# PP-021 — Implantação simples com Docker, Kubernetes e Traefik

## Resultado

Implantar a API containerizada em uma topologia Kubernetes local mínima,
persistente e acessível na máquina de desenvolvimento somente por HTTPS através
do Traefik.

## Dependências

- PP-020.

## Escopo

- criar imagem Docker de múltiplos estágios, reproduzível e executada como
  usuário sem privilégios de superusuário;
- excluir ferramentas de construção, código-fonte e credenciais da imagem final;
- executar toda a topologia de demonstração em cluster Kubernetes local, sem
  depender de nuvem ou serviço gerenciado;
- criar manifestos diretos para uma réplica da API em `Deployment`;
- usar estratégia `Recreate`, compatível com sessão local, uma réplica e PVC
  `ReadWriteOnce`;
- criar `Service` `ClusterIP`, sem exposição direta;
- separar configuração não sensível em `ConfigMap` e credenciais em referências
  a `Secret`, mantendo somente exemplos sem valores reais no repositório;
- montar PVC privado para anexos;
- criar a topologia local do PostgreSQL 18, no mesmo cluster, em `StatefulSet`
  com uma réplica, `Service` interno e PVC próprio;
- configurar sondas de inicialização, prontidão e vitalidade no mesmo
  `/actuator/health`, sem detalhes sensíveis;
- criar `Ingress` para um Traefik disponível no cluster local, com
  redirecionamento HTTP → HTTPS e terminação TLS;
- configurar nome de host local e referência a `Secret` TLS sem versionar a chave
  privada;
- validar cabeçalhos encaminhados e cookies `Secure` atrás do Traefik;
- executar teste de fumaça que inclua reinício de pod e persistência.

## Fora do escopo

- instalar ou administrar o cluster ou o Traefik;
- nuvem, hospedagem gerenciada, banco externo, DNS público ou certificado
  emitido por autoridade pública;
- Helm, Kustomize, operadores, HPA, múltiplas réplicas, GitOps ou malha de
  serviços;
- alta disponibilidade, atualização gradual sem indisponibilidade ou recuperação
  multirregião;
- publicar PostgreSQL, anexos ou Actuator detalhado;
- colocar valores reais de Secret no repositório.

## Critérios de aceitação

- a imagem inicia a API como usuário sem privilégios de superusuário e passa no teste de fumaça;
- o `Deployment` mantém uma réplica e usa `Recreate`;
- somente o Traefik é acessível a partir da máquina local; API e PostgreSQL usam
  `ClusterIP`;
- o pod fica pronto apenas depois de aplicação e banco estarem disponíveis;
- as três sondas não criam rotas operacionais adicionais;
- configuração não sensível e secreta respeitam as fronteiras definidas;
- anexos e dados do PostgreSQL sobrevivem à recriação dos respectivos pods;
- HTTP redireciona para HTTPS e o nome de host apresenta o certificado local
  configurado;
- a documentação explica como confiar no certificado apenas na máquina de
  demonstração;
- autenticação, cookie de sessão e CSRF funcionam pela URL HTTPS local;
- a aplicação não confia em cabeçalhos encaminhados vindos de uma rota pública direta;
- os manifestos são pequenos, legíveis e não introduzem os componentes excluídos;
- a documentação deixa explícito que PVC não substitui cópia de segurança.

## Estratégia de validação

- construir a imagem a partir de uma cópia de trabalho limpa;
- subir os manifestos em cluster Kubernetes local descartável compatível com
  Traefik;
- executar a fronteira REST pela URL HTTPS local;
- recriar pods e repetir consulta e baixamento previamente persistidos;
- inspecionar imagem e manifestos por execução sem privilégios de superusuário, exposição e ausência de
  segredos;
- não criar uma terceira fronteira de teste para Kubernetes.

## Requisitos

RNF-008, RNF-012, RNF-019, RNF-033–040; CA-017.
