# PP-022 — Documentação e validação final

## Resultado

Entregar o MVP reproduzível, documentado e verificável por outra pessoa sem
conhecimento implícito do ambiente do autor.

## Dependências

- PP-021.

## Escopo

- publicar OpenAPI completo da versão `/api/v1`;
- documentar login, cookie, CSRF, CORS e exemplos seguros;
- documentar build, testes, execução local e variáveis de ambiente;
- documentar a imagem Docker, os manifests, o cluster Kubernetes local e os
  pré-requisitos do Traefik;
- fornecer ambiente local reproduzível com PostgreSQL 18 dentro do cluster e
  volumes privados distintos para banco e anexos;
- documentar host local, criação do `Secret` TLS e confiança do certificado
  apenas na máquina de demonstração;
- documentar backup e restauração conjunta de banco e binários;
- documentar que somente dados fictícios ou anonimizados são permitidos;
- registrar decisões arquiteturais, patterns adotados e rejeitados;
- revisar que nenhum endpoint, schema ou dependência fora do escopo entrou;
- executar build, análise estática, formatação e suíte integral;
- executar smoke test do zero, inclusive migrations e upload/download;
- produzir checklist final de demonstração sem dados reais.

## Fora do escopo

- automação da instalação ou administração do cluster e do Traefik;
- cloud, serviço gerenciado, domínio público ou autoridade certificadora
  pública;
- automação avançada de backup ou recuperação multirregião;
- frontend, coleção de dados reais, certificação regulatória ou novas features.

## Critérios de aceitação

- uma pessoa parte de checkout limpo e inicia o sistema seguindo a documentação;
- uma pessoa aplica os manifests no cluster documentado sem depender de
  conhecimento implícito do autor;
- OpenAPI descreve autenticação por sessão, CSRF, paginação e Problem Details;
- todas as rotas implementadas estão documentadas e não há rotas fantasmas;
- restauração conjunta é descrita e ensaiada em ambiente descartável;
- `./mvnw verify` executa todas as verificações e termina verde;
- aplicação inicia em banco vazio e rejeita schema incompatível;
- smoke test pelo host local confirma redirecionamento HTTP, certificado TLS,
  sessão e persistência após recriação de pod;
- smoke test autenticado percorre paciente, agenda, consulta, adendo, anexo e
  auditoria;
- documentação declara limites do MVP e proibição de dados reais.

## Estratégia TDD e validação

- nenhum comportamento novo é criado neste ticket;
- corrigir somente lacunas reveladas por testes públicos ou documentação;
- preservar os dois seams; não adicionar testes por camada para elevar cobertura;
- registrar evidência dos comandos e resultados no checklist final.

## Requisitos

RF-061–063; RNF-001–006, RNF-008, RNF-012, RNF-017–018, RNF-024,
RNF-032–040; CA-016–017.
