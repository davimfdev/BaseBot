package dev.davimf.basebot.modules.base.leveling;

/** Dados de rank de um usuário — contrato reusável pelo painel V2 e por um renderer de imagem futuro. */
public record RankData(int level, long xpTotal, long into, long needed, int rank) {}
