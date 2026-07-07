# Deduplicacao segura no setup rapido de logs

## Objetivo

Ao executar o setup rapido, garantir que exista exatamente um canal com o nome padrao de cada tipo de log ativo. O bot deve preservar preferencialmente o canal ja configurado e remover somente duplicatas inequívocas.

## Comportamento

Para cada tipo de log ativo:

1. Carregar a configuracao atual e localizar todos os canais de texto cujo nome corresponda, sem diferenca entre maiusculas e minusculas, ao nome padrao daquele tipo.
2. Se o ID configurado apontar para um canal vivo, ele sera sempre preservado. Caso ele tenha o nome padrao, os demais canais com esse nome serao duplicatas.
3. Se nao houver canal configurado vivo, preservar o primeiro canal com nome padrao e persistir seu ID.
4. Se nenhum existir, criar o canal padrao e persistir seu ID.
5. Somente depois de existir uma configuracao persistida, excluir os outros canais com o mesmo nome padrao.

Um canal configurado vivo com nome diferente nao sera apagado nem substituido. Nesse caso, o bot preserva tambem o primeiro canal com nome padrao e remove apenas as repeticoes adicionais desse nome, sem trocar o ID configurado.

## Seguranca e falhas

- A persistencia acontece antes das exclusoes.
- Falha ao salvar interrompe o processamento daquele tipo antes de qualquer exclusao.
- Falha ao excluir uma duplicata nao afeta o canal preservado nem desfaz a configuracao.
- Canais com nomes diferentes nunca entram na limpeza.

## Testes

Os testes cobrem a selecao pura do canal preservado:

- canal configurado vivo tem prioridade entre duplicatas;
- sem configuracao viva, o primeiro canal padrao e preservado;
- os demais IDs padrao sao identificados como duplicatas;
- canal configurado com nome diferente permanece configurado, enquanto os canais padrao repetidos sao reduzidos a um.

O teste existente de reconhecimento do nome padrao permanece como regressao para a associacao sem recriacao.

## Bootstrap no onReady

Depois de registrar a instancia e sincronizar os snapshots no `onReady`, o bot percorre as guilds conectadas. Para cada guild sem nenhuma linha em `guild_config`, executa o mesmo setup rapido de logs fora da thread do JDA. Guilds que ja possuem uma linha, mesmo que parcialmente configurada, nao recebem criacao automatica no restart. Uma falha em uma guild e registrada e nao impede o bootstrap das demais.
