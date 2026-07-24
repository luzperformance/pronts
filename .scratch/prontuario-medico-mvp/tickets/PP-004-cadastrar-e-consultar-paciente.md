# PP-004 — Cadastrar e consultar paciente

## Resultado

Permitir ao médico autenticado cadastrar um paciente válido e consultar o
cadastro persistido pelo identificador estável.

## Dependências

- PP-003.

## Escopo

- criar entidade, tabela e contratos de entrada/saída de `Patient`;
- implementar `Cpf` como objeto de valor público do domínio;
- normalizar CPF e telefone, validar CPF e garantir unicidade no banco;
- tratar data de nascimento como data civil;
- implementar `POST /api/v1/patients`;
- implementar `GET /api/v1/patients/{patientId}`;
- criar paciente inicialmente ativo e com versão otimista;
- auditar `PATIENT_CREATED` na mesma transação, sem valores sensíveis.

## Fora do escopo

- pesquisa, atualização, mudança de estado ou exclusão;
- novos objetos de valor para textos sem invariantes;
- serialização direta da entidade JPA.

## Critérios de aceitação

- cadastro válido retorna `201` e pode ser consultado;
- campos obrigatórios ausentes ou em branco retornam `400` por campo;
- CPF formatado e sem formato convergem à mesma forma canônica;
- CPF inválido retorna `400` e CPF duplicado retorna `409`;
- campos opcionais permanecem opcionais;
- paciente nasce `ACTIVE`;
- criação e auditoria confirmam ou falham juntas.

## Estratégia TDD

- fronteira de domínio para exemplos válidos e inválidos de `Cpf`;
- fronteira REST para cadastro, consulta, validação, duplicidade e autenticação;
- PostgreSQL garante a prova de unicidade; não usar H2 nem duplo de repositório.

## Requisitos

RF-007–009; RN-003–006, RN-010, RN-046–047, RN-049; RNF-015–018, RNF-025–026,
RNF-030–031; CA-002.
