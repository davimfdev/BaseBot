package dev.davimf.basebot.modules.sales.budget;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.Budget;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * /orçamento — a seller builds an interactive budget for a client (BOTSPECS Module 3).
 * Gated by the configured "vendedor" role; the seller must have a Pix key registered so
 * approval can auto-dispatch the charge.
 */
public final class BudgetCommand implements SlashCommand {

    /** Logical role key in guild_config.roles — matches PixCommand's SELLER_ROLE_KEY. */
    private static final String SELLER_ROLE_KEY = "vendedor";

    private final BudgetService service;

    public BudgetCommand(BudgetService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "orçamento";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("orçamento", "Cria um orçamento interativo para um cliente.")
                .addOption(OptionType.USER, "cliente", "Cliente que vai aprovar o orçamento", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String sellerRoleId = cfg.role(SELLER_ROLE_KEY);
        if (sellerRoleId == null || sellerRoleId.isBlank()) {
            event.reply("Cargo de vendedor não configurado. Defina em /setup → Cargos → Vendedor (Pix).")
                    .setEphemeral(true).queue();
            return;
        }
        Member member = event.getMember();
        boolean isSeller = member.getRoles().stream().anyMatch(r -> r.getId().equals(sellerRoleId));
        if (!isSeller) {
            event.reply("Apenas vendedores (<@&" + sellerRoleId + ">) podem criar orçamentos.")
                    .setEphemeral(true).queue();
            return;
        }
        Member client = event.getOption("cliente", OptionMapping::getAsMember);
        if (client == null || client.getUser().isBot()) {
            event.reply("Selecione um cliente válido (não-bot).").setEphemeral(true).queue();
            return;
        }

        String budgetId = service.repository().createDraft(
                event.getGuild().getId(), member.getId(), client.getId());
        Budget budget = service.repository().find(budgetId).orElseThrow();
        event.replyComponents(service.builderView(budget)).useComponentsV2().setEphemeral(true).queue();
    }
}
