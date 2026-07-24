# Revisão final de escopo

Revisão baseada nos controllers, migrations, `pom.xml`, manifests e contrato
OpenAPI do checkout.

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
frontend, administração, cadastro de médicos, login de paciente, relatório,
exportação, comunicação, prescrição, cloud ou storage público.

## Persistência

As migrations V1–V16 cobrem marcador de schema, médico único, auditoria
append-only,
paciente, agenda, bloqueio, consulta, adendo e anexo. Não há tabelas de
multitenancy, CRM, faturamento, mensageria ou integrações externas.

## Dependências de runtime

As dependências diretas se limitam a Actuator, JPA, Flyway, Security, Validation,
Web MVC, driver PostgreSQL e suporte Flyway para PostgreSQL. Dependências de
teste se limitam ao starter de testes, Spring Security Test e
Testcontainers/PostgreSQL. Não há H2, OpenAPI runtime, frontend, broker, SDK de
cloud ou cliente de serviço externo.

O OpenAPI é um artefato estático versionado para não adicionar uma rota de
documentação, UI ou dependência de runtime fora do MVP.

## Topologia

Os manifests criam um namespace, dois PVCs privados e distintos, PostgreSQL 18,
API, Services `ClusterIP` e dois Ingresses Traefik para redirect/TLS. Não criam
`NodePort`, `LoadBalancer`, `hostPort`, Helm, operador, HPA, múltiplas réplicas
ou recurso de cloud.

## Limites declarados

Este software é acadêmico, para um único médico, e aceita somente dados
fictícios ou anonimizados. Não oferece certificação regulatória, alta
disponibilidade, disaster recovery multirregião, suporte operacional,
privacidade validada para produção ou autorização para dados reais.
