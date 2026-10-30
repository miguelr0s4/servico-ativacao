# ADR 003: OAuth 2.0 Resource Server

## Status

Aceito.

## Contexto

A API é consumida por outros sistemas e precisa separar permissão de escrita, leitura e acesso aos dados de cada consumidor.

## Decisão

Usar Spring Security como OAuth 2.0 Resource Server, fluxo Client Credentials no emissor e autorização pelos escopos requests:write e requests:read. O subject do JWT identifica o consumidor e restringe consultas aos registros criados por ele.

## Consequências

O ambiente local usa HS256 para não exigir um servidor de autorização. Em produção, a configuração deve usar o JWK Set do provedor de identidade e validar assinatura, emissor, audiência e expiração.
