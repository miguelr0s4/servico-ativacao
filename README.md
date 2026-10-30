# Serviço de Ativação

Serviço que registra solicitações de ativação de produtos, consulta status e integra
com um provedor externo.

## Sumário

- [Como executar](#como-executar)
- [Arquitetura](#arquitetura)
- [Decisões técnicas](#decisões-técnicas)
- [Cenário de análise](#cenário-de-análise)
- [Produção](#produção)
- [Pontos pendentes](#pontos-pendentes)
- [Melhorias futuras](#melhorias-futuras)
- [Uso de IA](#uso-de-ia)

## Como executar

### Com Docker Compose

```bash
docker compose up --build
```

Sobe a aplicação (`8080`), PostgreSQL e um WireMock (`8089`) simulando o provedor.
O Flyway aplica as migrações na inicialização.

O `mkjwt.js` (só Node, sem dependências) gera um token HS256 com a mesma chave
usada no `docker-compose.yml`:

```bash
TOKEN=$(node mkjwt.js)

curl -s -X POST http://localhost:8080/api/v1/requests \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}'
```

Esse token é só pra teste local (ver [Segurança](#segurança-item-7)). O
`productCode` decide o cenário simulado pelo provedor: `-ERRO400`, `-ERRO500`,
`-RATELIMIT`, `-INDISPONIVEL`, `-TIMEOUT`; qualquer outro valor vira sucesso.

### Local, sem Docker

Requer Java 21, Maven e PostgreSQL (`ativacao`/`ativacao`/`ativacao`).

```bash
./mvnw spring-boot:run
```

### Testes

```bash
./mvnw verify
```

A maioria usa H2 e WireMock. O de concorrência usa PostgreSQL real via
Testcontainers e precisa de Docker. No Windows, `mvnw.cmd verify`.

| Classe | Tipo | O que cobre |
|---|---|---|
| `SolicitacaoTest` | Unitário | Máquina de estados isolada, sem Spring. |
| `SolicitacaoServiceIntegrationTest` | Integração, H2 | Criação, retry, conflito, busca e listagem. |
| `SolicitacaoConcorrenciaPostgresTest` | Testcontainers | 10 criações simultâneas no Postgres real, só um registro sobrevive. |
| `SolicitacaoRepositoryFiltroTest` | Integração, H2 | Query JPQL de filtros contra banco real. |
| `SolicitacaoControllerTest` | `@WebMvcTest` | Validação, erro HTTP e segurança, com service mockado. |
| `SolicitacaoRateLimitTest` | `@SpringBootTest` | Rate limit real precisa do aspecto AOP do Resilience4j. |
| `AtivacaoWorkerIntegrationTest` | Integração, WireMock | Chamada HTTP real, timeout e idempotência. |
| `SolicitacaoMetricasTest` | Integração | Métrica registrada no `MeterRegistry` real. |
| `ArquiteturaTest` | ArchUnit | Regras de dependência entre camadas, via bytecode. |

## Arquitetura

```
api/           controllers, DTOs, segurança, erro HTTP
aplicacao/     casos de uso (SolicitacaoService)
dominio/       entidade rica, transições de estado, repositório
integracao/    client do provedor, worker assíncrono, provedor simulado
excecao/       hierarquia de exceções, compartilhada por todas as camadas
```

`Solicitacao` é um domínio rico, sem setter de status. As transições
(`iniciarProcessamento`, `concluir`, `registrarFalhaTemporaria`,
`falharDefinitivamente`) ficam em `StatusSolicitacao.podeTransicionarPara`. Um
teste de ArchUnit garante as regras de dependência entre camadas, incluindo que
nenhum `@RestController` acesse o repositório direto.

```
ServicoAtivacaoException
├── NegocioException (sealed)
│    ├── SolicitacaoNaoEncontradaException   → 404
│    ├── SolicitacaoDivergenteException      → 409
│    └── TransicaoDeStatusInvalidaException  → 409
└── ErroInternoException
     └── IntegracaoExternaException
```

`NegocioException` é `sealed`: o `GlobalExceptionHandler` usa `switch`
exaustivo, então um novo subtipo sem mapeamento de status HTTP não compila.

Sem arquitetura hexagonal. Com um endpoint de entrada e um provedor de saída,
só adicionaria indireção.

## Decisões técnicas

### API (item 2)

| Endpoint | Escopo | Descrição |
|---|---|---|
| `POST /api/v1/requests` | `requests:write` | `201` + `Location` na criação; `200` se retry idêntico; `409` se divergente. |
| `GET /api/v1/requests/{id}` | `requests:read` | `200` ou `404`. |
| `GET /api/v1/requests?status=&customerId=&page=&size=` | `requests:read` | Listagem paginada (item 4, diferencial). |

No `POST`, `externalId`, `customerId` e `productCode` são obrigatórios, até 100
caracteres; erro de validação volta em `400`, Problem Details (RFC 9457).

DTOs separados da entidade JPA. `createdAt`/`updatedAt` em UTC/ISO-8601
(`Instant`), sem configuração extra.

Listagem (item 4, diferencial) usa uma query JPQL com filtros opcionais, em vez
de um método por combinação. Índice `(customer_id, status)`, nessa ordem porque
`customer_id` filtra mais.

### Duplicidade (item 3)

Checar e só depois inserir não resolve: duas requisições podem passar pelo
check ao mesmo tempo. A garantia real é a constraint `UNIQUE (external_id)`.

`SolicitacaoService.criar` insere direto; se o banco rejeitar por violação de
unicidade, relê o registro existente e decide:

| Cenário | Resposta |
|---|---|
| Mesmo `externalId`, mesmo conteúdo (retry) | `200` com o registro existente |
| Mesmo `externalId`, conteúdo diferente | `409` |
| Duas requisições idênticas em paralelo | Uma ganha `201`; a outra colide e recebe `200` |

Testado com 10 threads concorrentes contra banco real: só um registro é criado.

### Integração com o provedor (item 5)

| Retorno | Novo status | Retenta? | Por quê |
|---|---|---|---|
| Sucesso (2xx) | `COMPLETED` | Não | Ativação confirmada. |
| HTTP 400 | `FAILED` | Não | Erro do cliente; repetir não muda nada. |
| HTTP 500 | `PROCESSING` → `FAILED` ao esgotar | Sim | Erro transiente do provedor. |
| Timeout | `PROCESSING` → `FAILED` ao esgotar | Sim | Resultado real é desconhecido. |
| 429/503 com `Retry-After` | `PROCESSING`, retenta no horário sugerido | Sim | Respeita o backpressure do provedor. |

Processamento assíncrono: o `POST` grava `PENDING` e devolve na hora; o
`AtivacaoWorker` (`@Scheduled`) processa depois.

O worker nunca chama o provedor com uma transação de banco aberta: reivindica
o registro (`PROCESSING`, comita), chama o provedor, grava o resultado numa
transação separada. Se a aplicação cair no meio, o registro fica pendente pro
próximo ciclo.

Cada chamada envia `Idempotency-Key` com o id da solicitação, pra um timeout
não virar ativação duplicada numa nova tentativa.

Limite de tentativas (`provider.max-tentativas`) com backoff exponencial e
jitter, circuit breaker (Resilience4j), e reconciliação de registros presos em
`PROCESSING` sem próxima tentativa agendada (`provider.reconciliacao-timeout`).

Timeouts de conexão e leitura configuráveis via `application.yml`.

### Segurança (item 7)

Resource Server OAuth2/JWT. `POST` exige `requests:write`, `GET` exige
`requests:read`; sem token `401`, escopo insuficiente `403`.

Hoje valido só assinatura (HS256, chave simétrica de dev) e expiração. Em
produção seria `jwk-set-uri` pro IdP real (Keycloak, Cognito, Auth0), com RS256
e validação de emissor/audiência também. Fluxo *Client Credentials*, sem
usuário no meio. Credencial do provedor num cofre de segredos da cloud.

Isolamento entre consumidores (BOLA): cada solicitação grava quem a criou (o
`sub` do JWT), e toda consulta filtra por esse campo; de outro sistema, `404`
em vez de `403`.

Rate limit (`429`) e limite de tamanho do corpo (`413`) no `POST`.

### Estados e transições (item 4)

`PENDING → PROCESSING → COMPLETED` ou `FAILED`, validado em
`StatusSolicitacao`, nunca por setter. Falha temporária mantém `PROCESSING`
com contador de tentativas e próxima tentativa agendada. Lock otimista
(`@Version`) evita que duas instâncias reivindiquem o mesmo registro.

## Cenário de análise

> API 300ms→2,5s; CPU 45%; memória 60%; algumas queries 50ms→1,2s; provedor 400ms→3s.

**O que investigaria primeiro?** O provedor externo: maior salto relativo, e
CPU/memória normais descartam gargalo da aplicação.

**Aplicação, banco ou integração?** CPU/memória normais indicam espera, não
processamento. Latência por dependência mostra onde: se provedor e API sobem
juntos, a causa é o provedor; o banco lento é sintoma.

**Logs ou métricas?** p50/p95/p99 por dependência, taxa de erro do provedor,
pool HTTP e `HikariPool`, logs por `traceId`.

**Que melhoria?** Isolar a chamada do provedor da requisição síncrona e um
circuit breaker; ambos já implementados.

**Causa comum? (diferencial)** Sim: um pool HTTP subdimensionado represa
threads que seguram conexões de banco, e as queries "lentas" só estão na fila.
Confirmaria vendo se os dois aumentos de latência coincidem no tempo.

**Validar a correção? (diferencial)** Comparando p95 de API, banco e provedor
antes/depois, em canário, confirmando que a correlação desaparece.

## Produção

Em produção, a imagem rodaria em Kubernetes ou ECS, múltiplas réplicas, config
via env/ConfigMap, segredos num serviço dedicado. Probes de liveness/readiness
já existem; métricas via `/actuator/prometheus`.

Métricas de latência (API e provedor) vêm da auto-instrumentação do Spring
Boot; solicitações por status é um `Gauge` em `SolicitacaoMetricas`. Logs em
JSON (`ecs`) só em produção. Tracing via Micrometer/OpenTelemetry, hoje só
logado.

Como microsserviço em cloud:

- Múltiplas instâncias. Duplicidade já é segura (constraint no banco); o
  worker funciona mas desperdiça trabalho competindo pelas mesmas linhas. Numa
  escala maior, `SELECT ... FOR UPDATE SKIP LOCKED` ou fila com grupo de
  workers.
- Config e segredos. Variável de ambiente pra config, cofre da cloud pra
  segredos.
- Notificar outros serviços. Publicar evento (`ativacao.concluida`) numa
  fila/tópico em vez de pollar.
- CI/CD. Build, testes, imagem Docker, deploy; exemplo em
  `.github/workflows/ci.yml`.

## Pontos pendentes

Lock otimista em vez de `SELECT ... FOR UPDATE SKIP LOCKED`: portável entre H2
e Postgres, mas não é o mais eficiente sob concorrência real.

O `ProviderSimuladoController` (provedor simulado usado sem Docker) vai dentro
do artefato real, como endpoint sem autenticação. Numa entrega real, isolaria
num profile separado.

ADRs de processamento assíncrono, idempotência e segurança em `docs/adr/`.

## Melhorias futuras

- Processamento por fila em vez de polling agendado.
- Autenticação real via Authorization Server.
- Exportador OTLP real e dashboards de latência por dependência.
- Testes de carga pra validar o dimensionamento dos pools.

## Uso de IA

Usei o Claude Code (Anthropic) como apoio pontual em três frentes: modelagem da
hierarquia de exceções (`excecao/`), DTOs (`api/dto/`) e montagem da suíte de
testes unitários.
