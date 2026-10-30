# ADR 001: Processamento assíncrono da ativação

## Status

Aceito.

## Contexto

O provedor externo pode ficar lento ou indisponível. Esperar sua resposta no endpoint de criação aumentaria a latência e acoplaria a disponibilidade da API à integração.

## Decisão

O POST persiste a solicitação como PENDING e retorna imediatamente. Um worker reivindica o registro em uma transação curta, chama o provedor sem transação de banco aberta e persiste o resultado em outra transação.

## Consequências

A API responde rapidamente e solicitações persistidas sobrevivem a reinícios. O estado passa a ser eventualmente consistente e exige reconciliação de registros presos em PROCESSING.
