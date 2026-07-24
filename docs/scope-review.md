# Revisão final de escopo

Revisão baseada nos controladores, migrações, `pom.xml`, manifestos e contrato
OpenAPI da cópia de trabalho.

## Superfície HTTP

Há 30 operações em `/api/v1`, todas presentes em
[`openapi.yaml`](openapi.yaml):

| Capacidade | Operações |
|---|---:|
| autenticação e sessão | 4 |
| pacientes | 5 |
| agenda e lembretes | 6 |
| bloqueios | 3 |
| consulta, finalização e adendo | 5 |
| prontuário cronológico | 1 |
| anexos | 5 |
| auditoria | 1 |

Fora da versão existe somente `GET /actuator/health`. Não existem rotas de
interface web, administração, cadastro de médicos, autenticação de paciente, relatório,
exportação, comunicação, prescrição, nuvem ou armazenamento público.

## Persistência

As migrações V1–V16 cobrem marcador de esquema, médico único, auditoria apenas
de inserção, paciente, agenda, bloqueio, consulta, adendo e anexo. Não há tabelas de
multitenância, CRM, faturamento, mensageria ou integrações externas.

## Dependências de execução

As dependências diretas se limitam a Actuator, JPA, Flyway, Security, Validation,
Web MVC, driver PostgreSQL e suporte Flyway para PostgreSQL. Dependências de
teste se limitam ao Spring Boot Starter Test, Spring Security Test e
Testcontainers/PostgreSQL. Não há H2, OpenAPI em execução, interface web,
broker de mensagens, SDK de nuvem ou cliente de serviço externo.

O OpenAPI é um artefato estático versionado para não adicionar uma rota de
documentação, interface de usuário ou dependência de execução fora do MVP.

## Topologia

Os manifestos criam um namespace, dois PVCs privados e distintos, PostgreSQL 18,
API, recursos `Service` `ClusterIP` e dois recursos `Ingress` Traefik para
redirecionamento/TLS. Não criam
`NodePort`, `LoadBalancer`, `hostPort`, Helm, operador, HPA, múltiplas réplicas
ou recurso de nuvem.

## Limites declarados

Este software é acadêmico, para um único médico, e aceita somente dados
fictícios ou anonimizados. Não oferece certificação regulatória, alta
disponibilidade, recuperação de desastres multirregião, suporte operacional,
privacidade validada para produção ou autorização para dados reais.
