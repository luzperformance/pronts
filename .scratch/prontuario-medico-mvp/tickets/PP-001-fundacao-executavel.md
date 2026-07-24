# PP-001 — Fundação executável

## Resultado

Disponibilizar uma aplicação Spring Boot vazia, reproduzível e verificável, que
inicia com PostgreSQL, aplica Flyway e responde somente pela rota de saúde.

## Dependências e gates

- nenhuma dependência de ticket;
- G-01 resolvido antes de criar o projeto.

## Escopo

- congelar patches compatíveis de Java 21, Spring Boot 4.1, Maven e PostgreSQL
  18;
- criar o projeto Maven, seu processo de construção e a classe de entrada;
- adicionar apenas as dependências previstas na especificação;
- configurar PostgreSQL por variáveis de ambiente, Flyway e
  `ddl-auto=validate`;
- criar a migração inicial mínima para controle técnico do esquema;
- configurar Testcontainers PostgreSQL para a fronteira REST;
- expor `GET /actuator/health` sem detalhes sensíveis;
- estabelecer módulos raiz por funcionalidade, sem subpacotes vazios;
- configurar formatação e análise estática básica no processo de construção.

## Fora do escopo

- entidades de negócio, conta médica, autenticação ou rotas `/api/v1`;
- Docker Compose e ambiente público de demonstração;
- observabilidade completa, CORS ou reforço final de segurança.

## Critérios de aceitação

- `./mvnw verify` usa Java 21 e termina com sucesso;
- a integração inicia PostgreSQL 18 limpo, executa Flyway e valida o esquema;
- a aplicação falha ao iniciar quando o esquema é incompatível;
- `GET /actuator/health` retorna disponibilidade sem credenciais, componentes ou
  dados clínicos;
- não existe H2 nem geração mutável de esquema pelo Hibernate;
- nenhuma credencial real é versionada.

## Estratégia TDD

- começar pelo teste REST de saúde contra a aplicação completa;
- acrescentar o teste de inicialização em banco vazio;
- validar falha de esquema por configuração de teste isolada;
- não criar teste unitário para configuração Spring.

## Requisitos

RF-061; RNF-001–005, RNF-017–018, RNF-032–033; CA-016.
