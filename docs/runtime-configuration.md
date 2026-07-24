# Configuração de execução

A API fecha CORS por padrão, exige CSRF nas mutações autenticadas por cookie e
mantém o cookie de sessão seguro por padrão. Para desenvolvimento HTTP local,
desabilite `SESSION_COOKIE_SECURE` explicitamente; nunca use esse ajuste em um
ambiente exposto.

## Variáveis

| Variável | Obrigatória | Uso |
| --- | --- | --- |
| `DB_URL` | sim fora dos testes | JDBC do endpoint direto Neon, sem `-pooler`, com TLS obrigatório |
| `DB_USERNAME` | sim fora dos testes | role de runtime do PostgreSQL |
| `DB_PASSWORD` | sim fora dos testes | senha da role de runtime; não deve ser versionada |
| `DOCTOR_USERNAME` | sim | usuário único do médico |
| `DOCTOR_PASSWORD` | sim | senha inicial; a aplicação persiste somente o resumo criptográfico adaptativo |
| `APP_CORS_ALLOWED_ORIGIN` | não | única origem exata aceita; vazia rejeita todo acesso entre origens |
| `APP_TIME_ZONE` | não | zona de apresentação, padrão `America/Sao_Paulo` |
| `ATTACHMENT_STORAGE_DIRECTORY` | não | diretório privado dos anexos |
| `SESSION_TIMEOUT` | não | duração da sessão, padrão `30m` |
| `SESSION_COOKIE_SECURE` | somente desenvolvimento HTTP | padrão `true`; use `false` apenas em loopback local |
| `TRUSTED_PROXY_NETWORKS` | produção | expressão regular dos endereços internos dos pods do Traefik |

O limite público de arquivo é 10 MiB. O envelope de múltiplas partes do servidor aceita
uma pequena margem para os metadados do formulário, mas o serviço interrompe a
leitura do arquivo no limite público. Todas as listagens paginadas aceitam
`page` e `size`, com padrões `0` e `20` e máximo uniforme de `100`.

## Produção atrás do Traefik

Ative o perfil `prod`. Ele desabilita o Flyway e configura o HikariCP com
`minimum-idle=1` e `maximum-pool-size=5`. No Kubernetes, o `ConfigMap` também
impõe `sslmode=require` ao driver PostgreSQL. O perfil fixa `Secure` no cookie de
sessão, produz logs JSON e usa o processamento nativo de cabeçalhos encaminhados
do Tomcat. O
`X-Forwarded-Proto` só é considerado quando a conexão imediata vem de
`TRUSTED_PROXY_NETWORKS`; um cliente fora dessa rede não consegue declarar a si
mesmo como HTTPS.

O Traefik deve ser a única entrada e deve:

1. terminar TLS;
2. redirecionar HTTP para HTTPS;
3. substituir, e não concatenar a partir do cliente, `X-Forwarded-Proto` e
   `X-Forwarded-For`;
4. encaminhar para o `ClusterIP` da API.

Não exponha o serviço da API por `NodePort`, `LoadBalancer`, `hostPort` ou
`port-forward` em produção. Os manifestos e o Ingress pertencem ao PP-021.

Os logs de requisição contêm somente método, caminho sem parâmetros de consulta, estado,
duração e identificador de correlação. Corpos, CPF, conteúdo clínico, nomes/conteúdo de
anexos e parâmetros de consulta não entram nesse log.
