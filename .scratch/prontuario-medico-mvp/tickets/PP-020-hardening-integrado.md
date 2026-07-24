# PP-020 — Hardening integrado

## Resultado

Fechar as lacunas transversais de segurança, privacidade, observabilidade e
limites operacionais com a API completa exercitada de ponta a ponta.

## Dependências

- PP-019.

## Escopo

- configurar cookies `HttpOnly`, `Secure` em produção e `SameSite` compatível;
- fechar CORS por padrão e aceitar somente origem configurada;
- revisar CSRF de todas as mutações;
- impor limites máximos de paginação e upload de forma consistente;
- estruturar logs com correlation ID e sanitização;
- impedir log de corpos, CPF, conteúdo clínico e anexos;
- revisar Problem Details de todos os conflitos e falhas inesperadas;
- validar que stack trace, SQL e detalhes de infraestrutura nunca saem na API;
- revisar queries paginadas e regressões N+1;
- configurar forwarded headers para a fronteira interna do proxy, garantindo
  origem HTTPS e cookies seguros sem confiar em acesso público arbitrário;
- documentar configuração por ambiente e transporte HTTPS atrás do Traefik;
- executar testes de concorrência de paciente, agenda e rascunho em conjunto.

## Fora do escopo

- WAF, SIEM, antivírus, MFA ou criptografia de campo;
- criação dos manifests Kubernetes e configuração do Ingress, entregues em
  PP-021;
- alta disponibilidade ou observabilidade distribuída;
- nova funcionalidade de negócio ou abstração arquitetural preventiva.

## Critérios de aceitação

- toda mutação com cookie exige CSRF;
- origem não configurada é rejeitada;
- perfil de produção emite cookie com atributos seguros;
- requisição HTTPS encaminhada pelo proxy continua sendo reconhecida como segura;
- nenhuma amostra de log contém os dados sensíveis semeados pelos testes;
- todas as respostas de erro têm correlation ID e detalhe sanitizado;
- limites são coerentes em todas as listagens e no upload;
- build acusa configuração secreta ou insegura conhecida conforme as verificações
  adotadas;
- suíte de concorrência não apresenta perda silenciosa ou sobreposição.

## Estratégia TDD

- seam REST cobre matriz representativa de todos os módulos;
- testes capturam logs estruturados como saída observável;
- não criar testes isolados de filtros, interceptors ou exception handlers.

## Requisitos

RF-005, RF-059–060; RN-046, RN-049; RNF-007–013, RNF-016, RNF-019–023,
RNF-027–029, RNF-032–033, RNF-038–039; CA-001, CA-016.
