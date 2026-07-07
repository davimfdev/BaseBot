package dev.davimf.basebot.modules.base.economy;

/** Chaves de cooldown (iguais às literais usadas pelos serviços — centralizadas anti-drift). */
public final class EconomyCooldownKeys {
    private EconomyCooldownKeys() {}
    public static final String CD_DAILY = "daily";
    public static final String CD_WORK = "work";
    public static final String CD_MINE = "minerar";
    public static final String CD_COOK = "cozinhar";
    public static final String CD_DELIVERY = "entregar";
    public static final String CD_CRIME = "crime";
    public static final String CD_ROB = "rob";
    public static final String CD_ORG = "orgcrime";
}
