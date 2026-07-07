package dev.davimf.basebot.modules.base.economy;

/** Um item da loja da economia (migração 034). {@code stock}/{@code perUser} null = ilimitado. */
public record ShopItem(long id, String guildId, Type type, String roleId, String name,
                       String description, long price, Long durationS, Integer stock,
                       Integer perUser, int sold, long createdAt) {

    public enum Type { ROLE_PERM, ROLE_TEMP, CUSTOM }

    public boolean limited() { return stock != null; }

    public boolean soldOut() { return stock != null && sold >= stock; }

    /** Unidades restantes; {@link Integer#MAX_VALUE} quando ilimitado. */
    public int remaining() { return stock == null ? Integer.MAX_VALUE : Math.max(0, stock - sold); }

    /** Cópia com o contador {@code sold} substituído (preenchido a partir do shop_stock). */
    public ShopItem withSold(int newSold) {
        return new ShopItem(id, guildId, type, roleId, name, description,
                price, durationS, stock, perUser, newSold, createdAt);
    }
}
