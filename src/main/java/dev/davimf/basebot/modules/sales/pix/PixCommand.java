package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Module 3 Pix command. The guild's configured "vendedor" role (set in
 * {@code /setup → Cargos}, stored in {@code guild_config.roles}) gates who may use the
 * command — but each seller's Pix keys are stored <b>per person</b> (by user id). A single
 * {@code /pix} opens an ephemeral panel to send a charge or manage (add/edit/remove) keys;
 * all interactions live in {@link PixComponentHandler}.
 */
public final class PixCommand implements SlashCommand {

    /** Logical role key in guild_config.roles — must match SetupRoleKeys' "vendedor". */
    private static final String SELLER_ROLE_KEY = "vendedor";

    private final PixKeyRepository keys;

    public PixCommand(PixKeyRepository keys) {
        this.keys = keys;
    }

    @Override
    public String name() {
        return "pix";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("pix", "Painel de chaves e cobranças Pix.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String sellerRoleId = cfg.role(SELLER_ROLE_KEY);
        if (sellerRoleId == null || sellerRoleId.isBlank()) {
            Replies.ephemeral(event, ctx,
                    "Cargo de vendedor não configurado. Defina em /setup → Cargos → Vendedor (Pix).");
            return;
        }
        boolean isSeller = event.getMember().getRoles().stream()
                .anyMatch(r -> r.getId().equals(sellerRoleId));
        if (!isSeller) {
            Replies.ephemeral(event, ctx,
                    "Apenas membros com o cargo de vendedor (<@&" + sellerRoleId + ">) podem usar o /pix.");
            return;
        }
        int count = keys.list(event.getGuild().getId(), event.getUser().getId()).size();
        event.replyComponents(PixPanelView.root(EmbedColor.resolve(cfg), count))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
