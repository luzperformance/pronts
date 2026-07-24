# PP-022 — Documentação e validação final

## Resultado

Entregar o MVP reproduzível, documentado e verificável por outra pessoa sem
conhecimento implícito do ambiente do autor.

## Dependências

- PP-021.

## Escopo

- publicar OpenAPI completo da versão `/api/v1`;
- documentar autenticação, cookie, CSRF, CORS e exemplos seguros;
- documentar construção, testes, execução local e variáveis de ambiente;
- documentar a imagem Docker, os manifestos, o cluster Kubernetes local e os
  pré-requisitos do Traefik;
- fornecer ambiente local reproduzível com PostgreSQL 18 dentro do cluster e
  volumes privados distintos para banco e anexos;
- documentar nome de host local, criação do `Secret` TLS e confiança do certificado
  apenas na máquina de demonstração;
- documentar cópia de segurança e restauração conjunta de banco e binários;
- documentar que somente dados fictícios ou anonimizados são permitidos;
- registrar decisões arquiteturais, padrões adotados e rejeitados;
- revisar que nenhuma rota, esquema ou dependência fora do escopo entrou;
- executar construção, análise estática, formatação e suíte integral;
- executar teste de fumaça do zero, inclusive migrações, envio e baixamento;
- produzir lista final de verificação da demonstração sem dados reais.

## Fora do escopo

- automação da instalação ou administração do cluster e do Traefik;
- nuvem, serviço gerenciado, domínio público ou autoridade certificadora
  pública;
- automação avançada de cópia de segurança ou recuperação multirregião;
- interface web, coleta de dados reais, certificação regulatória ou novas funcionalidades.

## Critérios de aceitação

- uma pessoa parte de uma cópia de trabalho limpa e inicia o sistema seguindo a documentação;
- uma pessoa aplica os manifestos no cluster documentado sem depender de
  conhecimento implícito do autor;
- OpenAPI descreve autenticação por sessão, CSRF, paginação e Problem Details;
- todas as rotas implementadas estão documentadas e não há rotas fantasmas;
- restauração conjunta é descrita e ensaiada em ambiente descartável;
- `./mvnw verify` executa todas as verificações e termina verde;
- aplicação inicia em banco vazio e rejeita esquema incompatível;
- teste de fumaça pelo nome de host local confirma redirecionamento HTTP, certificado TLS,
  sessão e persistência após recriação de pod;
- teste de fumaça autenticado percorre paciente, agenda, consulta, adendo, anexo e
  auditoria;
- documentação declara limites do MVP e proibição de dados reais.

## Estratégia TDD e validação

- nenhum comportamento novo é criado neste ticket;
- corrigir somente lacunas reveladas por testes públicos ou documentação;
- preservar as duas fronteiras; não adicionar testes por camada para elevar cobertura;
- registrar evidência dos comandos e resultados na lista final de verificação.

## Requisitos

RF-061–063; RNF-001–006, RNF-008, RNF-012, RNF-017–018, RNF-024,
RNF-032–040; CA-016–017.
