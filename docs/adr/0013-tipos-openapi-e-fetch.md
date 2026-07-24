# Tipos OpenAPI e cliente fetch

O contrato `docs/openapi.yaml` será a fonte para gerar os tipos TypeScript
consumidos pelo frontend. Não será mantida uma segunda descrição manual dos
corpos, parâmetros e respostas da API.

As requisições serão feitas por um cliente pequeno baseado no `fetch` nativo.
Esse cliente concentrará o envio das credenciais de sessão e o fluxo de CSRF
documentado pela API. Axios e geradores de clientes HTTP completos não serão
adicionados sem uma necessidade posterior comprovada.
