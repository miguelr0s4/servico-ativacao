# ADR 002: Idempotência e duplicidade

## Status

Aceito.

## Contexto

Duas requisições com o mesmo externalId podem chegar simultaneamente, e uma verificação antes do insert não elimina a condição de corrida.

## Decisão

A unicidade é garantida por constraint no PostgreSQL. Em conflito, o registro é relido: conteúdo idêntico retorna o recurso existente; conteúdo divergente retorna conflito. A chamada ao provedor usa o ID da solicitação como Idempotency-Key.

## Consequências

A regra funciona com múltiplas instâncias. O provedor precisa honrar a chave de idempotência para eliminar ativações duplicadas após timeouts ambíguos.
