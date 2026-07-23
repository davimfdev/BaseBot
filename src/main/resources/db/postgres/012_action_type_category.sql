-- Categoria das ações salvas (Pequena / Grande).
--
-- Existe porque o Discord limita um select a 25 opções: com todas as ações da cidade
-- num único menu, /setup → Ações estourava e /painel-acoes escondia as excedentes em
-- silêncio. Agrupar por categoria mantém cada menu bem abaixo do limite.
--
-- Nullable de propósito: NULL = "sem categoria", o balde das linhas antigas e das
-- guilds que não foram classificadas. A UI mostra esse grupo à parte para que nada
-- desapareça, e o modal de criar/editar exige a categoria daqui em diante.
ALTER TABLE fac_action_types ADD COLUMN IF NOT EXISTS category TEXT;

-- Backfill da cidade (guild 1503112622827110517), classificação fornecida pelo dono
-- do servidor. Escopado por guild: a regra "banco/aeroporto é grande" é dessa cidade,
-- não do bot. Outras guilds ficam NULL e classificam pela UI.
UPDATE fac_action_types SET category = 'GRANDE'
 WHERE guild_id = '1503112622827110517'
   AND name IN ('Aeroporto', 'Banco Central', 'Banco Paleto', 'Cinema',
                'Hollywood', 'Joalheria', 'Nióbio', 'Porta-Aviões');

UPDATE fac_action_types SET category = 'PEQUENA'
 WHERE guild_id = '1503112622827110517'
   AND category IS NULL;
