package dev.davimf.basebot.modules.facs.actions;

/**
 * Porte de uma ação salva. Serve para agrupar os menus de seleção: o Discord limita
 * um select a 25 opções, e uma cidade passa disso somando pequenas e grandes.
 *
 * <p>A ausência de categoria é representada por {@code null} (coluna nullable), não por
 * uma constante: linhas antigas e guilds ainda não classificadas caem nesse balde e a
 * UI as mostra num grupo "Sem categoria" em vez de escondê-las.
 */
public enum ActionCategory {

    // A ordem de declaração é a ordem de exibição — dos grupos de menus e do select do
    // modal. É a única fonte dessa ordem: as queries ordenam só por nome, e o
    // agrupamento percorre values(), então acrescentar uma categoria aqui basta.
    GRANDE("Grandes"),
    PEQUENA("Pequenas");

    private final String plural;

    ActionCategory(String plural) {
        this.plural = plural;
    }

    /** Rótulo no plural, usado como título do grupo na UI. */
    public String plural() {
        return plural;
    }

    /** Rótulo no singular, usado no select do modal e no detalhe da ação. */
    public String singular() {
        return this == PEQUENA ? "Pequena" : "Grande";
    }

    /** Converte o valor gravado no banco; devolve {@code null} para nulo ou desconhecido. */
    public static ActionCategory fromDb(String value) {
        if (value == null) {
            return null;
        }
        for (ActionCategory c : values()) {
            if (c.name().equalsIgnoreCase(value.trim())) {
                return c;
            }
        }
        return null;
    }
}
