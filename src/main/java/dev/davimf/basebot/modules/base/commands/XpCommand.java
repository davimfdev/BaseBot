package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.leveling.LevelingService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/** /xp (admin) — add/remove/set/reset do XP de um membro. */
public final class XpCommand implements SlashCommand {

    private final LevelingService leveling;

    public XpCommand(LevelingService leveling) { this.leveling = leveling; }

    @Override
    public String name() { return "xp"; }

    @Override
    public SlashCommandData data() {
        OptionData user = new OptionData(OptionType.USER, "usuario", "Membro", true);
        OptionData qtd = new OptionData(OptionType.INTEGER, "quantidade", "Quantidade de XP", true);
        return Commands.slash("xp", "Gerencia o XP de um membro (admin).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("add", "Adiciona XP").addOptions(user, qtd),
                        new SubcommandData("remove", "Remove XP").addOptions(user, qtd),
                        new SubcommandData("set", "Define o XP").addOptions(user, qtd),
                        new SubcommandData("reset", "Zera o XP")
                                .addOptions(new OptionData(OptionType.USER, "usuario", "Membro", true)));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping userOpt = event.getOption("usuario");
        User user = userOpt == null ? null : userOpt.getAsUser();
        Member member = userOpt == null ? null : userOpt.getAsMember();
        if (user == null) {
            Replies.ephemeral(event, ctx, "Usuário inválido.");
            return;
        }
        String guildId = event.getGuild().getId();
        String userId = user.getId();
        String sub = event.getSubcommandName() == null ? "" : event.getSubcommandName();
        OptionMapping qtdOpt = event.getOption("quantidade");
        long qtd = qtdOpt == null ? 0 : qtdOpt.getAsLong();

        switch (sub) {
            case "add" -> {
                if (member != null) {
                    leveling.award(event.getGuild(), member, qtd, null);
                } else {
                    leveling.users().addXp(guildId, userId, qtd);
                }
                Replies.reply(event, ctx, "Adicionado `" + qtd + "` XP a <@" + userId + ">.");
            }
            case "remove" -> {
                long novo = Math.max(0, leveling.users().xp(guildId, userId) - qtd);
                leveling.users().setXp(guildId, userId, novo);
                Replies.reply(event, ctx, "Removido `" + qtd + "` XP de <@" + userId + ">.");
            }
            case "set" -> {
                leveling.users().setXp(guildId, userId, qtd);
                Replies.reply(event, ctx, "XP de <@" + userId + "> definido para `" + qtd + "`.");
            }
            case "reset" -> {
                leveling.users().setXp(guildId, userId, 0);
                Replies.reply(event, ctx, "XP de <@" + userId + "> zerado.");
            }
            default -> Replies.ephemeral(event, ctx, "Subcomando inválido.");
        }
    }
}
