# Especificação do MVP — Primeiro Prontuário

**Situação:** rascunho da Fase 2, aguardando aprovação
**Objetivo:** API educacional com qualidade de sistema real
**Escopo:** monólito modular em Java e Spring Boot para uso individual por um único mé
Princípios orientadores:

- simplicidade é uma decisão arquitetural, não ausência de arquitetura;
- complexidade inerente ao domínio será explícita;
- complexidade acidental será evitada;
- DRY será aplicado a conhecimento e regras realmente duplicados;
- abstrações prematuras não serão criadas para remover pequenas semelhanças;
- controladores não conterão regras de negócio;
- serviços de aplicação existirão quando houver transação, regra, autorização,
  auditoria ou coordenação real;
- mero repasse será tolerado somente em consultas e CRUDs genuinamente simples,
  como uma faixa orientativa de 15% a 25% dos fluxos;
- padrões de projeto só serão adotados quando resolverem um problema concreto;
- a arquitetura inicial será um monólito modular, sem componentes distribuídos.

### 1.1 Base técnica proposta

- Java 21 LTS;
- Spring Boot 4.1;
- Spring MVC;
- Maven;
- PostgreSQL 18, sempre na versão de correção suportada mais recente da linha escolhida;
- Spring Data JPA;
- Spring Security com sessão HTTP;
- Bean Validation;
- Flyway;
- JUnit 5, Spring Boot Test, Spring Security Test e Testcontainers PostgreSQL;
- armazenamento local privado para binários de anexos, atrás de um contrato
  substituível;
- documentação em português e identificadores técnicos em inglês.

A seleção privilegia uma versão LTS madura do Java, um Spring Boot estável e um
PostgreSQL atualmente suportado. As versões exatas de patches serão congeladas
no primeiro ticket técnico.

## 2. Problema que o sistema resolve

Um médico que trabalha individualmente precisa centralizar os dados cadastrais de
seus pacientes, organizar a agenda e manter um histórico clínico confiável. Um
cadastro comum não é suficiente: registros clínicos finalizados não podem ser
silenciosamente sobrescritos, acessos e alterações sensíveis precisam deixar
rastro e anexos devem permanecer protegidos.

Para o estudante, o problema adicional é encontrar um projeto com complexidade
realista que permita praticar orientação a objetos, SOLID, testes, REST,
persistência e segurança sem exigir microserviços, mensageria ou infraestrutura
hospitalar.

## 3. Escopo do MVP

O MVP incluirá:

1. autenticação e encerramento de sessão do único médico;
2. cadastro, consulta, alteração e inativação de pacientes;
3. pesquisa paginada de pacientes;
4. agenda de consultas clínicas;
5. bloqueios de agenda;
6. prevenção de sobreposição;
7. estados e transições de agendamento;
8. lembretes internos de próximas consultas;
9. criação e edição de consulta em rascunho;
10. finalização irreversível da consulta;
11. prontuário cronológico por paciente;
12. inclusão de adendos sem alteração do conteúdo original;
13. envio, listagem, baixamento e remoção controlada de anexos;
14. vínculo obrigatório do anexo com paciente e opcional com consulta;
15. auditoria interna das ações sensíveis acordadas;
16. validação, tratamento uniforme de erros e paginação;
17. migrações versionadas;
18. testes unitários de domínio e testes de integração pela API REST;
19. documentação de execução, configuração e contrato da API.

## 4. Funcionalidades explicitamente fora do escopo

Não pertencem ao MVP:

- múltiplos médicos, secretárias ou outros perfis operacionais;
- pacientes com autenticação;
- clínica, hospital, unidades, departamentos ou multiempresa;
- multitenância;
- CRM, leads, funil comercial ou consultas de venda;
- prescrições, atestados e solicitações de exames;
- assinatura digital;
- geração de documentos destinados ao paciente;
- relatórios;
- exportação de auditoria;
- cópia ou exportação do prontuário;
- e-mail, SMS, WhatsApp ou push;
- cobrança, pagamentos, faturamento ou convênios integrados;
- telemedicina;
- integração com laboratórios, operadoras ou padrões hospitalares;
- aplicativo de interface web;
- microserviços, Kafka, filas, Kubernetes ou cache distribuído;
- busca textual dentro do conteúdo clínico;
- antivírus automatizado para anexos;
- criptografia de campo gerenciada pela aplicação;
- alta disponibilidade e recuperação multirregião;
- certificação legal ou regulatória para uso real em produção.

O portfólio deverá usar exclusivamente dados fictícios ou anonimizados.

## 5. Perfis de usuário e permissões

### 5.1 Médico

É o único perfil do MVP e pode:

- autenticar-se e encerrar sua sessão;
- administrar pacientes;
- consultar e administrar a agenda;
- criar, editar e finalizar consultas;
- consultar o prontuário;
- acrescentar adendos;
- administrar anexos.

O médico não pode:

- alterar ou excluir uma consulta finalizada;
- alterar ou excluir um adendo;
- apagar o histórico de auditoria;
- transformar diretamente um estado terminal de agendamento em estado ativo;
- acessar conteúdo sem autenticação.

### 5.2 Acesso técnico

Operações de infraestrutura, banco e cópia de segurança não formam um perfil de negócio nem
terão rotas administrativas no MVP. Credenciais de infraestrutura ficarão
fora da aplicação e fora do repositório.

## 6. Requisitos funcionais

### 6.1 Autenticação

- **RF-001:** O sistema deve autenticar o médico por identificador e senha.
- **RF-002:** O sistema deve manter autenticação por sessão HTTP segura.
- **RF-003:** O sistema deve permitir encerramento explícito da sessão.
- **RF-004:** O sistema deve informar a identidade da sessão atual.
- **RF-005:** Todas as rotas de negócio devem exigir autenticação.
- **RF-006:** Sucessos e falhas de autenticação devem ser auditados sem registrar
  a senha.

### 6.2 Pacientes

- **RF-007:** O sistema deve cadastrar paciente com nome completo, nome da mãe,
  data de nascimento, CPF, telefone, e-mail, endereço, contato de emergência,
  convênio, alergias e observações.
- **RF-008:** O sistema deve classificar o paciente como ativo ou inativo.
- **RF-009:** O sistema deve consultar paciente por identificador.
- **RF-010:** O sistema deve alterar os dados cadastrais de um paciente.
- **RF-011:** O sistema deve inativar e reativar paciente sem apagar seu
  histórico.
- **RF-012:** O sistema deve pesquisar pacientes por nome completo.
- **RF-013:** O sistema deve pesquisar pacientes por nome da mãe.
- **RF-014:** O sistema deve pesquisar pacientes por CPF.
- **RF-015:** O sistema deve pesquisar pacientes por telefone ou e-mail.
- **RF-016:** O sistema deve filtrar pacientes por estado.
- **RF-017:** A listagem de pacientes deve ser paginada e possuir ordenação
  determinística.

### 6.3 Agenda

- **RF-018:** O sistema deve agendar consulta clínica para um paciente ativo.
- **RF-019:** Uma consulta de agenda deve aceitar duração de 15, 30, 45 ou 60
  minutos.
- **RF-020:** O sistema deve calcular o término a partir do início e duração.
- **RF-021:** O sistema deve impedir a sobreposição entre consultas não
  canceladas.
- **RF-022:** O sistema deve impedir agendamentos que se sobreponham a bloqueios.
- **RF-023:** O sistema deve listar a agenda por intervalo de datas.
- **RF-024:** O sistema deve filtrar a agenda por estado.
- **RF-025:** O sistema deve consultar um agendamento por identificador.
- **RF-026:** O sistema deve reagendar uma consulta ainda não terminal.
- **RF-027:** O sistema deve confirmar uma consulta agendada.
- **RF-028:** O sistema deve marcar consulta como realizada.
- **RF-029:** O sistema deve cancelar uma consulta.
- **RF-030:** O sistema deve marcar o não comparecimento.
- **RF-031:** O sistema deve criar e remover bloqueios futuros de horário.
- **RF-032:** O sistema deve listar como lembretes internos as consultas
  agendadas ou confirmadas que ocorrerão nas próximas 24 horas.

### 6.4 Prontuário e consultas

- **RF-033:** O sistema deve criar uma consulta em estado de rascunho para um
  paciente ativo.
- **RF-034:** A consulta pode, opcionalmente, referenciar um agendamento do mesmo
  paciente.
- **RF-035:** O sistema deve permitir edição dos campos clínicos enquanto a
  consulta estiver em rascunho.
- **RF-036:** O sistema deve exigir anamnese, queixa, exame físico,
  hipóteses/diagnósticos, conduta e observações na finalização.
- **RF-037:** O sistema deve finalizar a consulta por ação explícita e
  irreversível.
- **RF-038:** Após a finalização, o sistema deve rejeitar qualquer tentativa de
  edição ou exclusão da consulta.
- **RF-039:** O sistema deve listar o prontuário de um paciente em ordem
  cronológica.
- **RF-040:** O sistema deve filtrar consultas por intervalo de datas.
- **RF-041:** O sistema deve consultar os detalhes de uma consulta.
- **RF-042:** O sistema deve acrescentar adendo a uma consulta finalizada.
- **RF-043:** Cada adendo deve possuir conteúdo, justificativa, autor, data e
  hora.
- **RF-044:** O sistema deve preservar e exibir o conteúdo original ao lado dos
  adendos, sem reescrita do registro original.

### 6.5 Anexos

- **RF-045:** O sistema deve receber anexos PDF, JPG, PNG e Markdown.
- **RF-046:** Todo anexo deve estar vinculado a um paciente existente.
- **RF-047:** Um anexo pode, opcionalmente, estar vinculado a uma consulta do
  mesmo paciente.
- **RF-048:** O sistema deve armazenar metadados, tamanho, tipo detectado e resumo criptográfico
  SHA-256 do anexo.
- **RF-049:** O sistema deve listar anexos ativos de um paciente.
- **RF-050:** O sistema deve consultar metadados de um anexo.
- **RF-051:** O sistema deve disponibilizar baixamento autenticado do conteúdo.
- **RF-052:** O sistema deve remover logicamente o anexo mediante justificativa,
  apagar seu binário e preservar uma lápide de metadados para auditoria.
- **RF-053:** Inclusão, baixamento e remoção de anexos devem deixar evento de
  auditoria.

### 6.6 Auditoria

- **RF-054:** O sistema deve registrar eventos de auditoria de modo imutável,
  permitindo apenas inserções.
- **RF-055:** Cada evento deve registrar ator, ação, alvo, instante, resultado e
  identificador de correlação.
- **RF-056:** Devem ser auditadas a autenticação, a falha de autenticação, o
  encerramento de sessão, a criação e alteração de paciente, mudança de estado,
  visualização de prontuário,
  finalização de consulta, inclusão de adendo, operações de anexo e criação,
  alteração, transição ou cancelamento de agendamento.
- **RF-057:** O evento de auditoria não deve armazenar senha, conteúdo clínico,
  conteúdo do arquivo ou carga útil HTTP completa.
- **RF-058:** O MVP deve permitir consulta paginada e somente leitura dos eventos
  de auditoria, sem gerar relatório ou arquivo de exportação.

### 6.7 Contrato REST e operação

- **RF-059:** O sistema deve responder erros usando um contrato uniforme baseado
  em Problem Details.
- **RF-060:** O sistema deve validar corpo, parâmetros, paginação e transições de
  estado.
- **RF-061:** O sistema deve expor apenas uma rota operacional de saúde sem
  dados sensíveis.
- **RF-062:** O sistema deve versionar a API a partir de `/api/v1`.
- **RF-063:** O sistema deve disponibilizar documentação do contrato REST no
  ambiente de desenvolvimento.

## 7. Requisitos não funcionais

- **RNF-001 — Compreensibilidade:** Um desenvolvedor júnior deve conseguir
  localizar uma funcionalidade pelo nome do módulo e seguir seu fluxo da API ao
  domínio sem saltos indiretos desnecessários.
- **RNF-002 — Simplicidade:** Não devem existir interfaces com uma única
  implementação salvo quando representarem fronteira externa ou variação real.
- **RNF-003 — DRY:** Uma regra de negócio deve possuir uma fonte de verdade.
  Duplicação pequena de apresentação ou mapeamento pode ser aceita até que haja
  evidência de uma abstração estável.
- **RNF-004 — Coesão:** Código deve ser organizado por funcionalidade de negócio,
  e não em grandes pastas globais de controladores, serviços e repositórios.
- **RNF-005 — Acoplamento:** Dependências entre módulos devem ser unidirecionais
  e usar apenas contratos públicos mínimos.
- **RNF-006 — Mero repasse:** Métodos sem decisão, transformação, coordenação ou
  regra devem ser exceção consciente; a faixa de 15% a 25% é uma heurística de
  revisão, não uma métrica automática.
- **RNF-007 — Segurança:** Nenhuma rota de negócio deve ser anônima.
- **RNF-008 — Transporte:** Produção deve operar exclusivamente atrás de HTTPS.
- **RNF-009 — Sessão:** Cookies devem usar `HttpOnly`, `Secure` em produção e
  política `SameSite` compatível com a interface web.
- **RNF-010 — CSRF:** Autenticação por cookie deve manter proteção CSRF para
  operações mutáveis.
- **RNF-011 — Senhas:** Senhas devem ser armazenadas somente como resumo
  criptográfico adaptativo com sal criptográfico aleatório.
- **RNF-012 — Segredos:** Senhas, chaves e credenciais não podem ser versionadas.
- **RNF-013 — Privacidade:** Logs comuns não devem conter dados clínicos, CPF,
  anexos ou corpos de requisição.
- **RNF-014 — Auditoria:** Registros de auditoria não podem ser atualizados nem
  removidos por fluxos da aplicação.
- **RNF-015 — Integridade:** Consulta finalizada e adendo devem ser imutáveis.
- **RNF-016 — Concorrência:** Entidades mutáveis relevantes devem detectar
  atualizações concorrentes e rejeitar perda silenciosa de dados.
- **RNF-017 — Banco:** O esquema deve ser administrado exclusivamente por Flyway;
  JPA deve apenas validá-lo.
- **RNF-018 — Banco real em testes:** Testes de persistência devem usar
  PostgreSQL, não H2.
- **RNF-019 — Arquivos:** Binários devem ficar fora de diretório público e nunca
  devem ser servidos diretamente pelo servidor web.
- **RNF-020 — Envio:** O limite inicial por arquivo será de 10 MiB.
- **RNF-021 — Tipos:** O tipo do arquivo deve ser verificado pelo conteúdo,
  complementado por extensão e MIME informado.
- **RNF-022 — Markdown:** Markdown deve ser tratado como baixamento textual UTF-8;
  a API não renderizará HTML do conteúdo no MVP.
- **RNF-023 — Desempenho:** Listagens devem ser paginadas e evitar consultas N+1.
- **RNF-024 — Capacidade:** O MVP deve atender confortavelmente um único médico,
  milhares de pacientes e dezenas de milhares de consultas, sem meta de escala
  hospitalar.
- **RNF-025 — Tempo:** Instantes devem ser persistidos com referência UTC e
  apresentados segundo a zona configurada, inicialmente
  `America/Sao_Paulo`.
- **RNF-026 — Datas civis:** Data de nascimento deve ser tratada como data sem
  horário.
- **RNF-027 — Erros:** Respostas não devem expor rastreamento de pilha, SQL ou detalhes de
  infraestrutura.
- **RNF-028 — Observabilidade:** Cada requisição deve receber um identificador de correlação;
  logs devem ser estruturados e sanitizados.
- **RNF-029 — Testabilidade:** Tempo e armazenamento de arquivos devem ser
  dependências injetáveis nas fronteiras justificadas.
- **RNF-030 — Testes:** Regras complexas devem possuir testes unitários de
  domínio e cada fluxo público deve possuir teste de integração REST.
- **RNF-031 — TDD:** Cada fatia deve seguir vermelho → verde, um comportamento por
  ciclo, sem escrever toda a suíte antes da implementação.
- **RNF-032 — Qualidade:** O processo de construção deve executar testes, análise
  estática básica e verificação de formatação.
- **RNF-033 — Portabilidade:** A aplicação deve ser configurável por variáveis de
  ambiente e iniciar de forma reproduzível.
- **RNF-034 — Cópia de segurança:** Uma instalação real deve incluir cópia de
  segurança conjunta do banco e dos binários; isso será documentado, mas
  automação avançada fica fora do MVP.
- **RNF-035 — Dados de demonstração:** Ambientes de estudo e portfólio devem usar
  somente dados fictícios ou anonimizados.

## 8. Regras de negócio

- **RN-001:** Existe exatamente um médico operacional no MVP.
- **RN-002:** Pacientes não se autenticam nem acessam a API.
- **RN-003:** Nome completo, nome da mãe, data de nascimento, CPF e telefone são
  obrigatórios no cadastro inicial.
- **RN-004:** E-mail, endereço, contato de emergência, convênio, alergias e
  observações são opcionais.
- **RN-005:** CPF deve ser normalizado para dígitos, validado e único.
- **RN-006:** Telefone deve ser normalizado sem perder a forma apropriada para
  exibição.
- **RN-007:** Pesquisa textual de nomes e e-mail não deve diferenciar maiúsculas
  e minúsculas.
- **RN-008:** Paciente não pode ser excluído no MVP.
- **RN-009:** Paciente inativo permanece pesquisável e conserva todo o histórico.
- **RN-010:** Novo agendamento e nova consulta exigem paciente ativo.
- **RN-011:** A duração da agenda deve ser exatamente 15, 30, 45 ou 60 minutos.
- **RN-012:** O horário final é derivado do início e não será informado
  independentemente.
- **RN-013:** Um novo agendamento não pode começar no passado.
- **RN-014:** Consultas canceladas não bloqueiam horário; demais estados
  bloqueiam.
- **RN-015:** Intervalos que apenas se tocam não se sobrepõem: uma consulta pode
  começar no instante em que a anterior termina.
- **RN-016:** Bloqueios de agenda não podem se sobrepor a consulta ativa.
- **RN-017:** Consulta ativa não pode se sobrepor a bloqueio.
- **RN-018:** Toda mutação de agenda deve obter uma trava transacional curta
  sobre o calendário único antes de verificar conflitos e gravar.
- **RN-019:** Reagendamento só é permitido em estado agendada ou confirmada.
- **RN-020:** Transições permitidas são:
  agendada → confirmada, realizada, cancelada ou não compareceu;
  confirmada → realizada, cancelada ou não compareceu.
- **RN-021:** Realizada, cancelada e não compareceu são estados terminais.
- **RN-022:** Corrigir um estado terminal exigirá uma funcionalidade futura; o
  MVP não reabre o agendamento.
- **RN-023:** Lembretes internos incluem apenas consultas agendadas ou confirmadas
  nas próximas 24 horas.
- **RN-024:** O lembrete é calculado por consulta; não existe job, mensagem ou
  confirmação de leitura no MVP.
- **RN-025:** Consulta clínica nasce como rascunho.
- **RN-026:** Rascunho pode ser alterado e ainda não compõe o registro clínico
  definitivo.
- **RN-027:** Finalização exige todos os seis campos clínicos definidos.
- **RN-028:** Campos preenchidos apenas com espaços são considerados ausentes.
- **RN-029:** Finalização é explícita, atômica e irreversível.
- **RN-030:** Consulta finalizada não pode ser editada nem excluída.
- **RN-031:** Adendo só pode ser incluído em consulta finalizada.
- **RN-032:** Adendo exige conteúdo e justificativa não vazios.
- **RN-033:** Adendo não pode editar nem ocultar o texto original.
- **RN-034:** Adendo não pode ser editado nem removido.
- **RN-035:** O prontuário será ordenado pela data clínica da consulta e, em
  empate, pelo instante de criação.
- **RN-036:** Uma consulta pode existir sem agendamento.
- **RN-037:** Se uma consulta finalizada estiver vinculada a agendamento ainda
  ativo, o agendamento será marcado como realizado na mesma transação.
- **RN-038:** O vínculo entre consulta e agendamento deve referenciar o mesmo
  paciente.
- **RN-039:** Todo anexo deve pertencer a um paciente.
- **RN-040:** Consulta de origem do anexo é opcional, mas, quando informada, deve
  pertencer ao mesmo paciente.
- **RN-041:** Arquivos acima de 10 MiB ou fora dos quatro tipos permitidos devem
  ser rejeitados antes da persistência definitiva.
- **RN-042:** Nome original do arquivo é metadado; nunca será usado diretamente
  como caminho físico.
- **RN-043:** Downloads sempre passam por autorização da aplicação.
- **RN-044:** Remoção de anexo exige justificativa.
- **RN-045:** Após remoção, o binário fica indisponível; metadados mínimos, resumo criptográfico,
  autor, justificativa e instante permanecem.
- **RN-046:** Evento de auditoria não pode conter o texto do prontuário ou o
  binário do anexo.
- **RN-047:** A auditoria da mutação de negócio deve ser confirmada na mesma
  transação da mutação; se uma falhar, ambas falham.
- **RN-048:** Falhas de autenticação devem ser auditadas em transação própria.
- **RN-049:** Conflitos de versão, CPF duplicado, sobreposição, transição inválida
  e tentativa de editar registro finalizado devem retornar conflito, não erro
  interno.

## 9. Histórias de usuário e principais casos de uso

### 9.1 Histórias de usuário

1. Como médico, quero autenticar-me, para que somente eu acesse os dados.
2. Como médico, quero encerrar minha sessão, para que outro usuário do dispositivo
   não continue autenticado.
3. Como médico, quero cadastrar um paciente, para que eu possa atendê-lo.
4. Como médico, quero localizar um paciente pelo nome completo, para encontrá-lo
   mesmo sem um documento em mãos.
5. Como médico, quero localizar um paciente pelo nome da mãe, para distinguir
   homônimos.
6. Como médico, quero localizar um paciente por CPF, para obter uma correspondência
   inequívoca.
7. Como médico, quero localizar um paciente por telefone ou e-mail, para usar os
   dados disponíveis durante o contato.
8. Como médico, quero corrigir dados cadastrais, para mantê-los atualizados.
9. Como médico, quero inativar um paciente sem apagar o histórico, para preservar
   registros médicos.
10. Como médico, quero agendar uma consulta com duração padronizada, para organizar
    meu tempo.
11. Como médico, quero ser impedido de criar horários sobrepostos, para não
    assumir dois compromissos simultâneos.
12. Como médico, quero bloquear um intervalo da agenda, para reservar tempo sem
    vinculá-lo a um paciente.
13. Como médico, quero reagendar uma consulta ativa, para acomodar mudanças.
14. Como médico, quero registrar confirmação, realização, cancelamento ou falta,
    para conhecer o resultado do agendamento.
15. Como médico, quero ver as próximas consultas como lembretes internos, para
    preparar meu dia.
16. Como médico, quero iniciar uma consulta em rascunho, para registrar
    informações antes da finalização.
17. Como médico, quero que a finalização exija os campos clínicos, para evitar
    registros incompletos.
18. Como médico, quero que registros finalizados sejam imutáveis, para preservar
    a integridade do prontuário.
19. Como médico, quero acrescentar um adendo justificado, para corrigir ou
    complementar sem apagar o original.
20. Como médico, quero ler o histórico em ordem cronológica, para entender a
    evolução do paciente.
21. Como médico, quero anexar um documento ao paciente, para manter evidências
    relacionadas ao cuidado.
22. Como médico, quero vincular opcionalmente o documento à consulta de origem,
    para preservar seu contexto.
23. Como médico, quero baixar um anexo com autenticação, para que ele não seja
    publicamente acessível.
24. Como médico, quero remover um anexo com justificativa, para corrigir um envio
    indevido sem apagar o rastro.
25. Como responsável pelos dados, quero que acessos e alterações sensíveis sejam
    auditados, para haver responsabilização.
26. Como médico, quero consultar a trilha de auditoria, para verificar quem fez
    uma ação sensível e quando, sem gerar relatórios no MVP.
27. Como desenvolvedor júnior, quero módulos com nomes e limites claros, para
    conseguir compreender e evoluir o sistema.
28. Como desenvolvedor, quero migrações reproduzíveis, para obter o mesmo esquema
    em todos os ambientes.
29. Como desenvolvedor, quero erros REST uniformes, para integrar o futuro
    interface web sem tratar respostas arbitrárias.
30. Como desenvolvedor, quero testes de comportamento em PostgreSQL real, para
    reduzir diferenças entre teste e execução.
31. Como avaliador de portfólio, quero ver decisões simples e justificadas, para
    distinguir engenharia consciente de complexidade ornamental.

### 9.2 Casos de uso principais

- **UC-001 — Autenticar médico:** valida credenciais, cria sessão e registra
  resultado de auditoria.
- **UC-002 — Cadastrar paciente:** valida e normaliza dados, impede CPF duplicado
  e registra auditoria.
- **UC-003 — Pesquisar paciente:** combina filtros opcionais, pagina e ordena.
- **UC-004 — Atualizar paciente:** detecta concorrência, valida alterações e
  registra campos cadastrais modificados sem copiar seus valores sensíveis.
- **UC-005 — Alterar estado do paciente:** inativa ou reativa sem apagar histórico.
- **UC-006 — Agendar consulta:** valida paciente, duração, passado, bloqueio e
  sobreposição.
- **UC-007 — Reagendar consulta:** aplica as mesmas regras de conflito e estado.
- **UC-008 — Transicionar agendamento:** valida a máquina de estados reduzida.
- **UC-009 — Administrar bloqueio:** reserva ou libera intervalo futuro.
- **UC-010 — Consultar lembretes:** deriva a janela das próximas 24 horas.
- **UC-011 — Criar e editar rascunho clínico:** mantém registro ainda mutável.
- **UC-012 — Finalizar consulta:** valida completude, congela conteúdo, atualiza
  agendamento relacionado e audita atomicamente.
- **UC-013 — Ler prontuário:** retorna consultas finalizadas e adendos em ordem.
- **UC-014 — Incluir adendo:** acrescenta correção imutável e auditada.
- **UC-015 — Enviar anexo:** valida tipo, tamanho, vínculo, nome seguro e resumo criptográfico.
- **UC-016 — Baixar anexo:** autoriza e transmite sem revelar caminho físico.
- **UC-017 — Remover anexo:** exige justificativa, apaga binário e preserva
  lápide auditável.
- **UC-018 — Consultar auditoria:** pesquisa eventos por período, ação e alvo,
  com paginação e sem expor conteúdo sensível.

## 10. Critérios de aceitação por funcionalidade

### CA-001 — Autenticação

- Credenciais válidas criam uma sessão autenticada e retornam a identidade do
  médico.
- Credenciais inválidas retornam mensagem genérica e nunca revelam se o
  identificador existe.
- Recurso protegido sem sessão retorna `401` no contrato Problem Details.
- Operação sem autorização retorna `403`.
- Encerramento invalida a sessão.
- Autenticação bem-sucedida, falha de autenticação e encerramento geram auditoria sem senha.

### CA-002 — Cadastro e consulta de paciente

- Cadastro válido retorna `201` e um identificador estável.
- CPF formatado e não formatado são tratados como o mesmo CPF.
- CPF inválido retorna `400`; CPF já cadastrado retorna `409`.
- Campos obrigatórios ausentes retornam erros por campo.
- O paciente criado pode ser consultado pela API.
- A criação gera um evento de auditoria.

### CA-003 — Pesquisa de pacientes

- Cada campo acordado pode ser usado isoladamente.
- Filtros podem ser combinados.
- Nomes e e-mail ignoram diferença entre maiúsculas e minúsculas.
- CPF usa correspondência exata após normalização.
- Resultado é paginado, com limite máximo e ordenação determinística.
- Uma busca sem resultados retorna página vazia, não `404`.

### CA-004 — Alteração e estado de paciente

- Alteração válida preserva o identificador e incrementa a versão.
- Duas alterações concorrentes não sobrescrevem dados silenciosamente.
- Inativação não remove paciente, consultas, anexos ou agenda anterior.
- Novo agendamento ou nova consulta para inativo retorna conflito.
- Reativação volta a permitir novos registros.
- Alteração e mudança de estado são auditadas.

### CA-005 — Criação e consulta de agendamento

- Somente as quatro durações permitidas são aceitas.
- O horário final é calculado corretamente.
- Início no passado é rejeitado.
- Sobreposição com consulta ativa ou bloqueio retorna `409`.
- Intervalos adjacentes são aceitos.
- Agenda pode ser listada por período, estado e paciente.
- Criação gera auditoria.

### CA-006 — Reagendamento e estados

- Apenas agendada ou confirmada pode ser reagendada.
- Reagendamento repete todas as validações de conflito.
- Somente transições definidas em RN-020 são aceitas.
- Estado terminal não pode ser reaberto.
- Atualização concorrente retorna `409`.
- Reagendamento e transições geram auditoria.

### CA-007 — Bloqueios e lembretes

- Bloqueio futuro livre é criado e passa a impedir agendamento.
- Bloqueio conflitante é rejeitado.
- Remoção libera novamente o intervalo.
- Lembretes incluem somente agendada/confirmada nas próximas 24 horas.
- Lembretes não disparam comunicação externa.

### CA-008 — Rascunho clínico

- Rascunho pode ser criado para paciente ativo.
- Rascunho pode ser salvo incompleto.
- Rascunho pode ser alterado enquanto não finalizado.
- Agendamento relacionado, quando informado, deve pertencer ao paciente.
- Rascunho não aparece como registro definitivo no prontuário.

### CA-009 — Finalização da consulta

- Finalização incompleta retorna `400` com todos os campos ausentes.
- Finalização válida congela conteúdo e registra instante e autor.
- Nova tentativa idempotente de finalizar o mesmo registro não duplica dados e
  informa que ele já está finalizado.
- Edição ou exclusão posterior retorna `409`.
- Agendamento ativo relacionado torna-se realizado atomicamente.
- A finalização gera auditoria na mesma transação.

### CA-010 — Prontuário cronológico

- Apenas consultas finalizadas compõem o histórico definitivo.
- Consultas são ordenadas por data clínica e critério de desempate definido.
- Filtro por intervalo limita corretamente o resultado.
- Cada item apresenta o conteúdo original sem mutação e seus adendos.
- A leitura do prontuário gera auditoria sem copiar o conteúdo clínico.

### CA-011 — Adendos

- Somente consulta finalizada aceita adendo.
- Conteúdo e justificativa são obrigatórios.
- Adendo recebe autor e instante do servidor.
- Adendo não altera nenhum campo original.
- Adendo não possui operação de edição ou remoção.
- Inclusão gera auditoria.

### CA-012 — Inclusão de anexo

- PDF, JPG, PNG e Markdown válidos até 10 MiB são aceitos.
- Extensão falsa ou conteúdo inválido é rejeitado.
- Nome com caminho ou caracteres perigosos não controla o caminho físico.
- Consulta de origem de outro paciente é rejeitada.
- Envio sem consulta de origem é aceito.
- Resumo criptográfico e metadados são persistidos.
- Falha entre armazenamento e persistência não deixa arquivo órfão conhecido.
- Inclusão gera auditoria.

### CA-013 — Listagem e baixamento de anexo

- Listagem retorna apenas anexos ativos por padrão.
- Conteúdo só pode ser baixado por sessão autenticada.
- Caminho físico nunca aparece na resposta.
- Baixamento usa tipo seguro e `Content-Disposition` de anexo.
- Markdown não é renderizado pela API.
- Baixamento gera auditoria.

### CA-014 — Remoção de anexo

- Justificativa vazia é rejeitada.
- Após remoção, o conteúdo retorna `404` ou `410` segundo a decisão de contrato.
- O binário é apagado.
- Metadados mínimos e resumo criptográfico permanecem como lápide.
- Nova remoção não apaga a lápide nem duplica efeitos.
- Remoção gera auditoria.

### CA-015 — Consulta de auditoria

- Somente uma sessão autenticada pode consultar eventos.
- A listagem é paginada e ordenada do evento mais recente para o mais antigo.
- Período, ação, resultado, tipo de alvo e identificador podem ser filtrados.
- O retorno não contém conteúdo clínico, senha, carga útil ou binário.
- Não há operação REST de alteração ou exclusão.
- O MVP não oferece CSV, PDF ou outra exportação.

### CA-016 — Erros, migrações e saúde

- Erros conhecidos possuem código de estado, tipo, título, detalhe seguro, identificador de correlação e
  erros por campo quando aplicável.
- Erro inesperado retorna `500` sem rastreamento de pilha.
- A aplicação inicia em banco vazio aplicando migrações em ordem.
- Esquema incompatível impede inicialização.
- A rota de saúde não expõe credenciais nem dados clínicos.

## 11. Modelo inicial de entidades e relacionamentos

### 11.1 Entidades

| Entidade | Responsabilidade | Dados principais |
|---|---|---|
| DoctorAccount | Identidade autenticável única | id, username, passwordHash, active, createdAt |
| Patient | Cadastro e estado do paciente | id, fullName, motherName, birthDate, cpf, contacts, address, insurance, allergies, notes, status, version |
| Appointment | Compromisso clínico na agenda | id, patientId, startsAt, durationMinutes, endsAt, status, notes, version |
| ScheduleBlock | Intervalo indisponível sem paciente | id, startsAt, endsAt, reason, createdAt |
| Consultation | Registro clínico mutável enquanto rascunho e imutável após finalização | id, patientId, appointmentId opcional, clinicalDate, status, seis campos clínicos, finalizedAt, version |
| Addendum | Acréscimo imutável a uma consulta finalizada | id, consultationId, content, reason, authorId, createdAt |
| Attachment | Metadados e ciclo de vida de documento | id, patientId, consultationId opcional, originalName, storageKey, mediaType, size, sha256, status, metadados de remoção |
| AuditEvent | Registro imutável de ação sensível | id, actorId opcional, action, targetType, targetId, outcome, occurredAt, correlationId, safeMetadata |

### 11.2 Objetos de valor e enumerações

- `Cpf`: normalização, validação e forma canônica;
- `AppointmentDuration`: conjunto fechado de 15, 30, 45 ou 60 minutos;
- `TimeInterval`: início inclusivo e fim exclusivo para comparar conflitos;
- `PatientStatus`: `ACTIVE`, `INACTIVE`;
- `AppointmentStatus`: `SCHEDULED`, `CONFIRMED`, `COMPLETED`, `CANCELLED`,
  `NO_SHOW`;
- `ConsultationStatus`: `DRAFT`, `FINALIZED`;
- `AttachmentStatus`: `ACTIVE`, `REMOVED`;
- `AuditOutcome`: `SUCCESS`, `FAILURE`.

Não serão criados objetos de valor para cada texto. Eles serão usados apenas onde
houver invariantes e comportamento próprios.

### 11.3 Relacionamentos

- DoctorAccount 1 → N AuditEvent;
- Patient 1 → N Appointment;
- Patient 1 → N Consultation;
- Patient 1 → N Attachment;
- Appointment 0..1 ↔ 0..1 Consultation no MVP;
- Consultation 1 → N Addendum;
- Consultation 1 → N Attachment, sendo o vínculo opcional no lado do anexo;
- AuditEvent referencia alvos por tipo e identificador, sem foreign key
  polimórfica.

As relações JPA serão carregadas de forma conservadora. Listagens usarão consultas
projetadas quando necessário, evitando serialização direta de entidades e
carregamento acidental de todo o prontuário.

## 12. Estados e ciclos de vida

### 12.1 Paciente

`ACTIVE → INACTIVE → ACTIVE`

- Não existe estado excluído.
- O histórico permanece em ambos os estados.
- Apenas paciente ativo recebe novos agendamentos e consultas.

### 12.2 Agendamento

Estado inicial: `SCHEDULED`.

Transições:

- `SCHEDULED → CONFIRMED`;
- `SCHEDULED → COMPLETED`;
- `SCHEDULED → CANCELLED`;
- `SCHEDULED → NO_SHOW`;
- `CONFIRMED → COMPLETED`;
- `CONFIRMED → CANCELLED`;
- `CONFIRMED → NO_SHOW`.

`COMPLETED`, `CANCELLED` e `NO_SHOW` são terminais. A pequena quantidade de
estados será modelada com enumeração e regras explícitas, não com o padrão Estado.

### 12.3 Consulta clínica

`DRAFT → FINALIZED`

- `DRAFT` é editável e pode estar incompleto.
- `FINALIZED` é irreversível e imutável.
- Correções posteriores são novas entidades Addendum.
- Não existe exclusão, reabertura ou versionamento destrutivo.

### 12.4 Anexo

`ACTIVE → REMOVED`

- `ACTIVE` permite listar e baixar.
- `REMOVED` conserva lápide, mas não o binário.
- Não existe restauração no MVP.

### 12.5 Auditoria

`APPENDED`

- Um evento nasce definitivo.
- Não existem operações de edição ou remoção pela aplicação.

## 13. Rotas REST sugeridas

Todas as rotas, exceto autenticação, proteção CSRF aplicável e saúde, exigem sessão
autenticada. Os nomes são contratos propostos, não implementação.

### 13.1 Autenticação

| Método | Rota | Finalidade |
|---|---|---|
| POST | `/api/v1/auth/login` | Criar sessão |
| POST | `/api/v1/auth/logout` | Encerrar sessão |
| GET | `/api/v1/auth/me` | Consultar médico autenticado |
| GET | `/api/v1/auth/csrf` | Obter token CSRF quando necessário ao cliente |

### 13.2 Pacientes

| Método | Rota | Finalidade |
|---|---|---|
| POST | `/api/v1/patients` | Cadastrar paciente |
| GET | `/api/v1/patients` | Pesquisar e paginar por filtros |
| GET | `/api/v1/patients/{patientId}` | Consultar cadastro |
| PUT | `/api/v1/patients/{patientId}` | Substituir dados cadastrais mutáveis |
| PATCH | `/api/v1/patients/{patientId}/status` | Ativar ou inativar |

Filtros previstos em `GET /patients`: `fullName`, `motherName`, `cpf`, `phone`,
`email`, `status`, `page`, `size` e ordenação permitida por lista de permissões.

### 13.3 Agenda

| Método | Rota | Finalidade |
|---|---|---|
| POST | `/api/v1/appointments` | Agendar consulta |
| GET | `/api/v1/appointments` | Listar por período, estado e paciente |
| GET | `/api/v1/appointments/{appointmentId}` | Consultar agendamento |
| PUT | `/api/v1/appointments/{appointmentId}/schedule` | Reagendar |
| PATCH | `/api/v1/appointments/{appointmentId}/status` | Aplicar transição |
| GET | `/api/v1/appointments/reminders` | Próximas 24 horas |
| POST | `/api/v1/schedule-blocks` | Criar bloqueio |
| GET | `/api/v1/schedule-blocks` | Listar bloqueios por período |
| DELETE | `/api/v1/schedule-blocks/{blockId}` | Remover bloqueio futuro |

Agendamento não terá `DELETE`; cancelamento é uma transição auditável.

### 13.4 Consultas e prontuário

| Método | Rota | Finalidade |
|---|---|---|
| POST | `/api/v1/patients/{patientId}/consultations` | Criar rascunho |
| GET | `/api/v1/consultations/{consultationId}` | Consultar consulta |
| PUT | `/api/v1/consultations/{consultationId}` | Alterar rascunho |
| POST | `/api/v1/consultations/{consultationId}/finalization` | Finalizar |
| POST | `/api/v1/consultations/{consultationId}/addenda` | Incluir adendo |
| GET | `/api/v1/patients/{patientId}/medical-record` | Ler prontuário cronológico |

Filtros previstos no prontuário: `from`, `to`, `page` e `size`.

### 13.5 Anexos

| Método | Rota | Finalidade |
|---|---|---|
| POST | `/api/v1/patients/{patientId}/attachments` | Envio de múltiplas partes com consulta opcional |
| GET | `/api/v1/patients/{patientId}/attachments` | Listar anexos ativos |
| GET | `/api/v1/attachments/{attachmentId}` | Consultar metadados |
| GET | `/api/v1/attachments/{attachmentId}/content` | Baixar conteúdo |
| DELETE | `/api/v1/attachments/{attachmentId}` | Remover com justificativa |

### 13.6 Auditoria

| Método | Rota | Finalidade |
|---|---|---|
| GET | `/api/v1/audit-events` | Consultar trilha paginada e somente leitura |

Filtros previstos: `from`, `to`, `action`, `outcome`, `targetType`, `targetId`,
`page` e `size`.

### 13.7 Operação

| Método | Rota | Finalidade |
|---|---|---|
| GET | `/actuator/health` | Verificar disponibilidade sem expor detalhes |

### 13.8 Convenções HTTP

- `201 Created` para criação;
- `200 OK` para consulta e alteração com resposta;
- `204 No Content` para encerramento de sessão e remoções adequadas;
- `400 Bad Request` para formato ou validação;
- `401 Unauthorized` para ausência ou falha de autenticação;
- `403 Forbidden` para operação autenticada não permitida;
- `404 Not Found` para recurso inexistente ou não acessível;
- `409 Conflict` para estado, versão, duplicidade, imutabilidade ou sobreposição;
- `413 Payload Too Large` para arquivo acima do limite;
- `415 Unsupported Media Type` para tipo não permitido;
- `500 Internal Server Error` sanitizado para falha inesperada.

## 14. Arquitetura de pacotes sugerida

O código será organizado por módulo de negócio:

- `auth`: autenticação, conta única e segurança de sessão;
- `patient`: cadastro, estado e pesquisa;
- `schedule`: agendamentos, intervalos, bloqueios e lembretes;
- `medicalrecord`: rascunhos, finalização, prontuário e adendos;
- `attachment`: metadados, validação e armazenamento;
- `audit`: gravação imutável de eventos;
- `shared`: somente contrato de erro, identificador de correlação e tipos técnicos realmente
  compartilhados;
- `configuration`: composição e configuração técnica.

Dentro de um módulo, subpacotes serão criados somente quando houver conteúdo:

- `api`: controlador, DTO de entrada e DTO de saída;
- `application`: casos de uso, transações e coordenação;
- `domain`: entidades, objetos de valor e regras;
- `infrastructure`: JPA ou adaptadores externos quando realmente necessários.

Não serão criadas quatro camadas vazias para cada funcionalidade. Um módulo
simples pode começar com menos subpacotes e ganhar estrutura apenas quando o
volume justificar.

### 14.1 Direção das dependências

- `api` chama `application`;
- `application` coordena domínio, persistência e auditoria;
- `domain` não depende de Spring MVC, DTOs HTTP ou sistema de arquivos;
- `infrastructure` implementa fronteiras técnicas;
- `audit` não depende dos módulos auditados;
- `shared` não pode virar depósito de classes sem dono;
- entidades JPA não são serializadas como respostas REST;
- controladores não acessam JPA diretamente.

### 14.2 MVC e mero repasse

MVC será usado na fronteira HTTP:

- Controlador: protocolo, autenticação disponível, validação de entrada e tradução
  de resposta;
- Serviço de aplicação/caso de uso: transação, coordenação, autorização contextual,
  auditoria e regras que atravessam agregados;
- Domínio: invariantes e comportamento da própria entidade/objeto de valor;
- Repositório/adaptador: persistência ou E/S.

Para leituras simples, um serviço de consulta poderá ser curto. Não será adicionada
lógica artificial para que ele pareça “profundo”. Em contrapartida, operações
como finalização, reagendamento, envio e adendo não podem ser cadeias
controlador → serviço → repositório sem regras explícitas.

## 15. Autenticação, autorização, auditoria e proteção de dados

### 15.1 Autenticação

- sessão HTTP mantida no servidor em vez de JWT;
- uma conta de médico persistida no banco;
- preparação inicial por configuração segura, sem rota pública de registro;
- senha codificada com algoritmo adaptativo provido pelo Spring Security;
- resposta genérica para credenciais inválidas;
- renovação do identificador de sessão após autenticação;
- encerramento invalida sessão;
- sessões expiram por inatividade;
- proteção contra fixação de sessão;
- cookie seguro em produção.

JWT foi rejeitado no MVP porque não há múltiplos consumidores, federação ou
necessidade de token portátil. Sessão reduz código de refresh, revogação e
armazenamento inseguro no navegador.

### 15.2 Autorização

- somente `ROLE_DOCTOR`;
- negação padrão;
- saúde e autenticação são as únicas exceções anônimas;
- sem autorização por organização ou por médico, pois existe um único médico;
- regras de estado continuam no domínio/aplicação, não em anotações de segurança.

### 15.3 Auditoria

Eventos propostos:

- `AUTH_LOGIN_SUCCEEDED`, `AUTH_LOGIN_FAILED`, `AUTH_LOGOUT`;
- `PATIENT_CREATED`, `PATIENT_UPDATED`, `PATIENT_STATUS_CHANGED`;
- `MEDICAL_RECORD_VIEWED`;
- `APPOINTMENT_CREATED`, `APPOINTMENT_RESCHEDULED`,
  `APPOINTMENT_STATUS_CHANGED`;
- `SCHEDULE_BLOCK_CREATED`, `SCHEDULE_BLOCK_REMOVED`;
- `CONSULTATION_FINALIZED`, `ADDENDUM_ADDED`;
- `ATTACHMENT_UPLOADED`, `ATTACHMENT_DOWNLOADED`, `ATTACHMENT_REMOVED`.

Os eventos conterão apenas metadados mínimos. Em atualização cadastral, pode ser
registrada a lista de campos alterados, mas não seus valores. A gravação será
explícita nos casos de uso críticos; AOP e eventos assíncronos foram rejeitados
para evitar comportamento invisível e risco de a mutação confirmar sem auditoria.

### 15.4 Proteção de dados

- dados reais não serão usados no portfólio;
- HTTPS obrigatório em qualquer ambiente exposto;
- PostgreSQL e volume de anexos não serão publicados diretamente;
- banco, volume e cópias de segurança devem usar criptografia de infraestrutura quando
  implantados;
- secrets serão externos ao repositório;
- logs serão sanitizados;
- respostas terão apenas os campos necessários ao caso de uso;
- anexos usarão chave interna aleatória e diretório privado;
- nomes informados pelo usuário nunca comporão caminho físico;
- acesso a anexos será mediado pela aplicação;
- CORS será fechado por padrão e configurará apenas a origem da futura interface web;
- paginação terá limites máximos;
- erros não revelarão existência de dados além do necessário.

Criptografia de cada campo clínico na aplicação foi adiada: sem gestão correta de
chaves ela cria uma falsa sensação de segurança e aumenta muito a complexidade.
Uma implantação real exigirá análise de ameaças, LGPD, retenção, cópias de
segurança, rotação de segredos e recuperação de desastre antes de receber dados
reais.

## 16. Decisões de testes — Estratégia de testes

### 16.1 Fronteiras propostas — exigem aprovação

Serão usadas somente duas fronteiras:

1. **Fronteira REST:** a API HTTP autenticada é a fronteira principal. Testes de
   integração sobem a aplicação, aplicam migrações e usam PostgreSQL real via
   Testcontainers. Eles verificam resposta e comportamento por rotas públicas,
   inclusive segurança, persistência, concorrência e auditoria
   consultável pela própria API.
2. **Fronteira de domínio:** métodos públicos de entidades e objetos de valor serão
   testados sem Spring apenas quando contiverem regra substancial, como CPF,
   intervalos, transições, finalização e adendos.

Não haverá fronteira separada para controlador, serviço e repositório. Serviços não
serão unitariamente testados por meio de duplos dos próprios colaboradores. O
adaptador de arquivos será exercitado pela fronteira REST usando diretório temporário.

### 16.2 O que constitui um bom teste

- descreve comportamento e regra de negócio;
- usa apenas interface pública da fronteira;
- possui resultado esperado independente e explícito;
- não testa método privado;
- não verifica quantidade ou ordem de chamadas internas;
- não reproduz o algoritmo na expectativa;
- não consulta o banco como atalho para provar um comportamento que a API deveria
  tornar observável;
- permanece válido após refatoração interna.

### 16.3 Distribuição

**Testes unitários de domínio:**

- normalização e validação de CPF;
- comparação de intervalos e adjacência;
- duração permitida;
- transições de agenda;
- bloqueio de mutação após finalização;
- criação válida e inválida de adendo;
- regras de vínculo paciente/consulta quando puramente locais.

**Testes de integração REST:**

- autenticação, sessão, CSRF e encerramento;
- autorização de todos os módulos;
- cadastro, busca, paginação, duplicidade e concorrência de paciente;
- conflitos de agenda, bloqueios, estados e lembretes;
- rascunho, finalização, imutabilidade, prontuário e adendos;
- envio, tipo real, limite, baixamento e remoção;
- migrações em banco vazio;
- mapeamento de exceções e Problem Details;
- transações que combinam mutação e auditoria.

**Testes de contrato de migração:**

- inicialização do zero;
- validação do esquema por JPA;
- proibição de `ddl-auto` mutável fora de testes estritamente isolados.

### 16.4 Ciclo TDD

Para cada ticket e comportamento:

1. escrever um teste na fronteira aprovada;
2. executar e confirmar a falha pelo motivo esperado;
3. implementar apenas o suficiente para passar;
4. executar o conjunto relevante;
5. repetir com o próximo comportamento;
6. realizar refatoração apenas na etapa de revisão, com a suíte verde.

Não será usada estratégia horizontal de “escrever todos os testes e depois todo
o código”.

## 17. Lista inicial de trabalho, do simples ao complexo

Esta lista ainda não é o conjunto publicado de tickets. Após a aprovação da
especificação, ele será refinado pela habilidade `to-tickets` em fatias verticais com
bloqueios explícitos.

1. **Fundação executável:** iniciar aplicação, conectar PostgreSQL, aplicar a
   primeira migração e responder saúde.
2. **Contrato de erros:** retornar Problem Details, identificador de correlação e validações
   básicas por uma rota real.
3. **Autenticação completa:** preparação segura, autenticação, sessão, identidade, encerramento
   e auditoria de autenticação.
4. **Cadastrar e consultar paciente:** primeiro fluxo persistente autenticado com
   CPF e auditoria.
5. **Pesquisar pacientes:** filtros combináveis, paginação e ordenação.
6. **Atualizar e inativar paciente:** validação, bloqueio otimista e auditoria.
7. **Agendar consulta:** duração, cálculo do intervalo, paciente ativo e listagem
   por período.
8. **Impedir conflitos:** sobreposição de consultas e testes de adjacência.
9. **Bloquear agenda:** criar/listar/remover bloqueio e impedir conflitos.
10. **Reagendar e transicionar:** máquina de estados reduzida e auditoria.
11. **Lembretes internos:** consulta derivada das próximas 24 horas.
12. **Criar e editar rascunho clínico:** vínculo opcional com agendamento.
13. **Finalizar consulta:** campos obrigatórios, imutabilidade, conclusão do
    agendamento e auditoria atômica.
14. **Ler prontuário cronológico:** paginação, intervalo e auditoria de acesso.
15. **Adicionar adendo:** correção imutável com justificativa.
16. **Enviar e listar anexo:** armazenamento privado, validação, resumo criptográfico e vínculo opcional.
17. **Baixar anexo:** autorização, transmissão contínua segura e auditoria.
18. **Remover anexo:** justificativa, exclusão do binário e lápide auditável.
19. **Consultar auditoria:** pesquisa paginada, filtros e garantia de metadados
    mínimos.
20. **Reforço integrado de segurança:** CORS, cookies, CSRF, limites, sanitização e logs.
21. **Documentação e validação final:** contrato REST, execução local, cópia de segurança
    conceitual e suíte completa.

Cada item deverá caber em um contexto de implementação e entregar comportamento
demonstrável de ponta a ponta.

## 18. Roteiro de evolução

### 18.1 MVP

- conta única e sessão;
- pacientes;
- agenda clínica e bloqueios;
- lembretes internos;
- rascunho e finalização de consulta;
- prontuário cronológico;
- adendos;
- anexos;
- auditoria interna;
- testes e documentação.

### 18.2 Versão 2

- relatórios clínicos e operacionais;
- consulta e filtros da trilha de auditoria;
- exportação de auditoria;
- cópia/exportação do prontuário;
- política configurável de retenção;
- armazenamento de anexos em objeto externo;
- autenticação multifator;
- correção administrativa auditada de estado de agendamento;
- lembretes internos configuráveis;
- dashboard simples.

### 18.3 Possibilidades futuras

- CRM e consultas de venda;
- comunicação por e-mail, SMS ou WhatsApp;
- documentos para pacientes;
- assinatura digital;
- portal do paciente;
- mais perfis internos;
- integrações clínicas;
- busca textual controlada;
- antivírus de anexos;
- criptografia de campos com serviço real de chaves;
- múltiplos médicos ou clínica, somente se o produto mudar de objetivo.

Evoluir para múltiplos médicos não será antecipado no esquema do MVP. Se essa
necessidade surgir, será tratada como mudança real de produto, não como uma
coluna `tenant_id` preventiva em todas as tabelas.

## 19. Decisões de implementação — Padrões de projeto

### 19.1 Repositório, provido por Spring Data JPA

- **Problema concreto:** persistir e consultar agregados sem espalhar EntityManager
  ou SQL pelo código de negócio.
- **Onde:** repositórios dos módulos de pacientes, agenda, prontuário, anexos e
  auditoria.
- **Por que é melhor que implementação direta:** centraliza consultas e permite
  transações e testes reais sem duplicar acesso a dados.
- **Limite:** não será criada uma interface de domínio que apenas copie cada
  método de uma interface Spring Data. Um contrato adicional só surgirá quando
  houver isolamento ou semântica real.

### 19.2 Especificação para busca combinável

- **Problema concreto:** cinco campos opcionais de paciente, estado e paginação
  gerariam muitas combinações de métodos de repositório.
- **Onde:** pesquisa de pacientes e, se necessário, filtros combinados da agenda.
- **Por que é melhor que implementação direta:** compõe critérios sem nomes de
  método explosivos ou condicionais SQL duplicados.
- **Limite:** consultas fixas simples continuam diretas.

### 19.3 Objeto de valor

- **Problema concreto:** CPF, duração e intervalo possuem forma canônica e
  invariantes que não devem ser repetidas em controladores e serviços.
- **Onde:** `Cpf`, `AppointmentDuration` e `TimeInterval`.
- **Por que é melhor que strings e inteiros diretos:** torna estados inválidos
  difíceis de representar e mantém a regra em um único lugar, atendendo DRY.
- **Limite:** não haverá objeto de valor para todo campo textual.

### 19.4 Adaptador para armazenamento de anexos

- **Problema concreto:** sistema de arquivos é E/S externa, exige segurança específica e
  pode ser substituído por armazenamento de objetos no futuro.
- **Onde:** um contrato pequeno de armazenamento, com implementação local no MVP.
- **Por que é melhor que chamadas diretas a arquivos em vários serviços:** evita
  espalhar caminhos, limpeza e transmissão contínua, permite teste em diretório temporário
  e cria uma fronteira real.
- **Limite:** uma única interface terá uma única implementação porque representa
  fronteira externa concreta; não será criada uma hierarquia genérica de armazenamento.

### 19.5 Injeção de dependência para fronteiras variáveis

- **Problema concreto:** horário atual e sistema de arquivos tornam testes não
  determinísticos quando acessados globalmente.
- **Onde:** `Clock` e contrato de armazenamento; demais dependências seguem
  injeção por construtor do Spring.
- **Por que é melhor que chamadas estáticas diretas:** permite controlar tempo e
  I/O nos testes sem mockar classes internas.
- **Limite:** não serão criadas interfaces artificiais para todas as classes.

### 19.6 Bloqueio otimista

- **Problema concreto:** duas abas podem alterar paciente, agendamento ou
  rascunho e a última requisição apagaria silenciosamente a primeira.
- **Onde:** entidades mutáveis.
- **Por que é melhor que atualização direta:** detecta conflito com baixo custo
  e sem bloqueio duradouro, adequado ao único usuário em múltiplas sessões/abas.
- **Limite:** não substitui a validação transacional de sobreposição.

### 19.7 Bloqueio pessimista no calendário único

- **Problema concreto:** duas requisições simultâneas podem verificar a mesma
  lacuna de horário antes que qualquer uma grave, criando sobreposição.
- **Onde:** toda criação ou alteração de consulta de agenda e bloqueio obterá uma
  trava curta em uma linha técnica que representa o único calendário.
- **Por que é melhor que somente “consultar e depois inserir”:** serializa apenas
  as poucas mutações da agenda e torna a regra confiável mesmo com duas abas,
  sem exigir restrição avançada entre tabelas diferentes.
- **Limite:** a trava existe porque há um único calendário. Ela deverá ser
  redesenhada se o produto ganhar múltiplos médicos.

### 19.8 Serviço de aplicação

- **Problema concreto:** finalização, envio e reagendamento coordenam regra,
  persistência, autorização contextual e auditoria na mesma transação.
- **Onde:** casos de uso com coordenação real.
- **Por que é melhor que controlador direto no repositório:** preserva transação,
  mantém protocolo HTTP fora do domínio e oferece um fluxo legível.
- **Limite:** não haverá um serviço genérico por entidade nem métodos que apenas
  copiem todo o repositório sem motivo. Serviços de consulta simples são a exceção
  consciente de mero repasse.

### 19.9 Padrões explicitamente rejeitados no MVP

- **Estado:** poucos estados fixos; enumeração e transições explícitas são mais simples.
- **Fábrica dedicada:** criação pode ser expressa por construtor/método de fábrica da
  própria entidade.
- **Eventos de domínio/Observador para auditoria:** auditoria é obrigatória e
  transacional; chamada explícita é mais clara.
- **AOP para auditoria de negócio:** esconderia contexto e metadados importantes.
- **CQRS:** volume e complexidade não justificam modelos separados.
- **Mediador/barramento de comandos:** adicionaria indireção sem múltiplos manipuladores.
- **CRUD genérico em serviço/repositório:** reduz duplicação aparente, mas apaga a
  linguagem e as regras de cada módulo.
- **Arcabouço de mapeamento de DTOs:** mapeamentos pequenos e explícitos são mais fáceis de
  aprender e depurar.
- **Microserviços, saga e mensageria:** não existe transação distribuída.

## 20. Decisões pendentes, riscos, simplificações e evolução

### 20.1 Decisões ainda sujeitas à aprovação

1. Confirmar Java 21, Spring Boot 4.1, Maven e PostgreSQL 18.
2. Confirmar identificadores técnicos em inglês e documentação em português.
3. Confirmar os campos obrigatórios propostos no RN-003.
4. Confirmar rascunho explícito antes da finalização.
5. Confirmar vínculo opcional entre consulta e agendamento e conclusão automática
   do agendamento na finalização.
6. Confirmar janela fixa de 24 horas para lembretes.
7. Confirmar limite de 10 MiB por anexo.
8. Escolher `404` ou `410` para baixamento de anexo removido.
9. Confirmar remoção física do binário com preservação de lápide.
10. Definir nome final do artefato, identificador de grupo e pacote raiz.
11. Definir ambiente de implantação de demonstração.
12. Configurar o rastreador deste repositório antes da publicação dos tickets.
13. Aprovar as duas fronteiras de teste descritas na seção 16.

### 20.2 Riscos técnicos

- a trava do calendário pode virar gargalo se o produto deixar de ser individual;
- falha entre escrita de arquivo e confirmação da transação do banco pode produzir inconsistência
  se a compensação não for testada;
- Markdown pode causar XSS se uma interface web futura o renderizar sem sanitização;
- logs e auditoria podem vazar dados sensíveis se metadados forem amplos demais;
- imutabilidade pode ser quebrada por atualização JPA acidental se o modelo e
  permissões do banco não forem verificados;
- migrações mal planejadas podem dificultar evolução de dados clínicos;
- autenticação por sessão exige coordenação correta de CSRF e CORS com a interface web;
- sistema de arquivos local exige cópia de segurança consistente com o banco;
- CPF obrigatório limita pacientes sem CPF;
- abstrações educacionais podem crescer além do necessário;
- mero repasse pode ser medido de forma artificial e incentivar código pior;
- usar dados reais em portfólio representa risco grave de privacidade.

### 20.3 Mitigações proporcionais

- trava transacional curta no calendário e testes concorrentes antes de
  considerar restrição PostgreSQL avançada;
- operação de arquivo com área temporária e compensação;
- Markdown entregue como baixamento, nunca renderizado pela API;
- lista de permissões de metadados de auditoria;
- API sem atualização/exclusão para registros imutáveis, validações de domínio e
  migrações restritivas;
- PostgreSQL em integração via Testcontainers;
- documentação conjunta de cookie, CSRF e origem permitida;
- cópia de segurança de banco e volume como uma única unidade operacional;
- revisão arquitetural por fatia, não contagem cega de classes.

### 20.4 Simplificações adotadas

- um médico e um perfil;
- sessão em vez de JWT;
- sem interface web;
- sem CRM e comunicação externa;
- lembrete calculado sob demanda;
- sem exclusão de paciente;
- enumeração em vez do padrão Estado;
- chamadas explícitas de auditoria em vez de AOP/eventos;
- armazenamento local em vez de armazenamento de objetos;
- sem criptografia de campo gerenciada pela aplicação;
- sem rota de relatório de auditoria;
- sem modelagem preventiva de organização, clínica ou hospital;
- sem abstração genérica de CRUD;
- duas fronteiras de teste em vez de uma pirâmide de testes por camada.

### 20.5 Pontos de evolução

- trocar o adaptador local de anexos sem alterar os casos de uso;
- adicionar consulta/exportação da auditoria;
- adicionar relatórios e cópia do prontuário;
- tornar lembretes configuráveis e criar canais externos;
- introduzir MFA;
- permitir novos perfis com autorização real;
- revisar regras de retenção e exclusão;
- adicionar criptografia de campo somente com gestão profissional de chaves;
- reforçar prevenção de conflitos por restrição específica do PostgreSQL se o
  risco concorrente se materializar;
- modularizar fisicamente apenas quando os limites e a necessidade operacional
  estiverem comprovados.

## 21. Critério de aprovação desta especificação

A especificação estará aprovada quando o responsável pelo produto confirmar:

1. escopo e itens excluídos;
2. regras e critérios de aceitação;
3. decisões pendentes da seção 20.1;
4. fronteiras de teste da seção 16;
5. autorização para decompor o trabalho em tickets.

Após essa aprovação, a próxima etapa será apresentar a decomposição proposta de
tickets, seus bloqueios e entregas de ponta a ponta. Os tickets somente serão
publicados depois de uma segunda aprovação de granularidade e dependências.

## 22. Observações adicionais — Referências oficiais

A base técnica foi conferida em 23 de julho de 2026:

- [Spring Boot 4.1 — requisitos de sistema](https://docs.spring.io/spring-boot/system-requirements.html);
- [OpenJDK 21](https://openjdk.org/projects/jdk/21/);
- [Política de versões suportadas do PostgreSQL](https://www.postgresql.org/support/versioning/).

As referências servem para selecionar uma base suportada. Elas não substituem
o congelamento de versões e a validação do processo de construção no primeiro ticket técnico.
