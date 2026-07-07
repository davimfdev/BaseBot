# Registro incremental de comandos por guild

## Objetivo

Preservar comandos especificos por guild sem sobrescrever todos os comandos a cada startup.

## Fluxo

No `onReady` e no `onGuildJoin`, o bot consulta os comandos existentes da guild. Compara os nomes existentes com os comandos desejados e registra individualmente apenas os ausentes. Comandos existentes nao sao atualizados nem removidos neste escopo. A guild de vault e ignorada sem executar chamadas de escrita.

O registro continua sendo por guild para permitir que o futuro sistema de modulos forneca uma lista desejada diferente para cada servidor.

## Falhas

Falha ao consultar uma guild e registrada sem afetar as demais. Falha ao criar um comando identifica guild e comando. O rate limiter da JDA continua controlando retries.

## Testes

A selecao pura dos ausentes cobre lista vazia, conjunto parcialmente registrado e conjunto completo.
