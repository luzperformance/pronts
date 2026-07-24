# Contrato HTTP seguro

O contrato normativo da versão é
[`openapi.yaml`](openapi.yaml). Todas as rotas de negócio começam com
`/api/v1`; o único endpoint operacional exposto é `GET /actuator/health`.

Os exemplos usam uma pessoa e credenciais fictícias. Nunca copie dados clínicos,
CPFs, anexos, cookies ou senhas reais para comandos, logs ou documentação.

## Sessão e cookies

`POST /api/v1/auth/login` recebe JSON e cria uma sessão no servidor. Em sucesso,
o cliente recebe `JSESSIONID` com `HttpOnly`, `SameSite=Lax` e `Secure` na
demonstração HTTPS. A senha não volta na resposta.

O token CSRF usa o cookie `XSRF-TOKEN`, legível pelo cliente e com
`SameSite=Lax`. Atrás do Traefik HTTPS ele também é `Secure`. O valor do cookie
não autentica: a identidade vem exclusivamente de `JSESSIONID`.

Exemplo local seguro, com valores deliberadamente fictícios:

```bash
base_url='http://localhost:8080'
cookie_jar="$(mktemp)"

curl --fail --silent --show-error \
  --cookie-jar "$cookie_jar" \
  --header 'Content-Type: application/json' \
  --data '{"username":"doctor","password":"local-demo-doctor-password"}' \
  "$base_url/api/v1/auth/login"

curl --fail --silent --show-error \
  --cookie "$cookie_jar" \
  --cookie-jar "$cookie_jar" \
  "$base_url/api/v1/auth/me"
```

Remova o arquivo temporário após o uso. Na URL HTTPS local, substitua a base e
use `--cacert deploy/tls/ca.crt`; não use `--insecure`.

## CSRF

Login é a única mutação dispensada do token. Para `POST`, `PUT`, `PATCH` ou
`DELETE` autenticado:

1. obtenha `GET /api/v1/auth/csrf` usando o mesmo cookie jar;
2. leia `headerName` e `token` do JSON;
3. envie o header indicado e os dois cookies na mutação.

```bash
csrf="$(
  curl --fail --silent --show-error \
    --cookie "$cookie_jar" \
    --cookie-jar "$cookie_jar" \
    "$base_url/api/v1/auth/csrf"
)"
csrf_header="$(jq -er '.headerName' <<<"$csrf")"
csrf_token="$(jq -er '.token' <<<"$csrf")"

curl --fail --silent --show-error \
  --cookie "$cookie_jar" \
  --cookie-jar "$cookie_jar" \
  --header 'Content-Type: application/json' \
  --header "$csrf_header: $csrf_token" \
  --data '{
    "fullName":"Paciente Fictícia de Demonstração",
    "motherName":"Mãe Fictícia de Demonstração",
    "birthDate":"1990-01-01",
    "cpf":"52998224725",
    "phone":"11999990000"
  }' \
  "$base_url/api/v1/patients"
```

`POST /api/v1/auth/logout` também exige CSRF e responde `204`. Uma falha de CSRF
responde `403` como `application/problem+json`.

## CORS

O uso normal é same-origin pelo host do Traefik e não precisa de CORS. Quando
`APP_CORS_ALLOWED_ORIGIN` está vazio, nenhuma origem cross-origin é autorizada.
Se um cliente de demonstração separado for indispensável, configure uma única
origem exata, por exemplo `https://cliente.prontuario.local`.

O servidor permite credenciais, os métodos `GET`, `POST`, `PUT`, `PATCH`,
`DELETE` e `OPTIONS`, e apenas os headers `Content-Type`, `X-XSRF-TOKEN` e
`X-Correlation-ID`. O navegador precisa enviar cookies (`credentials:
include`). Não configure `*` com credenciais.

## Paginação e datas

As coleções paginadas retornam:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`page` começa em zero e `size` aceita de 1 a 100. Pacientes aceitam `sort` no
formato `campo,direção`; os demais recursos têm ordenação fixa e determinística.
Datas de entrada da agenda não têm offset e são interpretadas por
`APP_TIME_ZONE`. Respostas da agenda e do prontuário incluem offset.

`GET /api/v1/schedule-blocks` aplica paginação na consulta, mas mantém por
compatibilidade uma resposta em array, sem envelope nem total.

## Auditoria somente leitura

`GET /api/v1/audit-events` exige sessão autenticada, limita `size` a 100 e
ordena por `occurredAt` e `id`, ambos decrescentes. Os filtros opcionais podem
ser usados isoladamente ou em conjunto:

- `from` inclusivo e `to` exclusivo, ambos em ISO 8601 com offset;
- `action` e `outcome`;
- `targetType` e `targetId`.

O retorno expõe apenas identificadores, ação, resultado, instante, correlação e
a lista fechada de nomes de campos alterados. Não contém valores cadastrais,
conteúdo clínico, senha, payload, binário ou caminho. Não existem operações
REST de alteração, exclusão ou exportação da auditoria; a migration V16 também
rejeita `UPDATE` e `DELETE` no PostgreSQL.

## Problem Details e correlação

Erros seguem RFC 9457 em `application/problem+json`:

```json
{
  "type": "urn:problem:invalid-request",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "Um ou mais campos são inválidos.",
  "correlationId": "57dce92c-5f70-43e9-b74f-7b884e74ac11",
  "errors": [
    {"field": "fullName", "message": "é obrigatório"}
  ]
}
```

O cliente pode fornecer `X-Correlation-ID` como UUID válido; caso contrário, a
API cria um valor. A resposta sempre expõe o ID, inclusive em erros. Não use o
campo para transportar dados pessoais.

Tipos públicos possíveis incluem `invalid-request`, `invalid-credentials`,
`authentication-required`, `access-denied`, `resource-not-found`,
`resource-gone`, `conflict`, `unsupported-media-type`, `payload-too-large`,
`method-not-allowed` e `internal-error`, todos sob `urn:problem:`.
