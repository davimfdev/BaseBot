package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.AutocompleteCommand;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;

/** /vip conceder — concede um VIP (plano + duração opcional) a um membro (BOTSPECS VIPs). */
public final class VipConcederCommand implements SlashCommand, AutocompleteCommand {

    private final VipService vip;

    public VipConcederCommand(VipService vip) {
        this.vip = vip;
    }

    @Override public String name() { return "conceder"; }

    @Override public SlashCommandData data() {
        return Commands.slash("conceder", "Concede um VIP a um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addOption(OptionType.USER, "membro", "Quem recebe o VIP", true)
                .addOption(OptionType.STRING, "plano", "Plano VIP", true, true)
                .addOption(OptionType.INTEGER, "dias", "Duração em dias (vazio = padrão do plano)", false)
                .addOption(OptionType.INTEGER, "horas", "Duração em horas (soma aos dias)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Member target = event.getOption("membro", OptionMapping::getAsMember);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Membro inválido (precisa estar no servidor).");
            return;
        }
        String planId = event.getOption("plano", OptionMapping::getAsString);
        Long dias = event.getOption("dias", OptionMapping::getAsLong);
        Long horas = event.getOption("horas", OptionMapping::getAsLong);
        Guild guild = event.getGuild();
        String grantedBy = event.getUser().getId();

        event.deferReply(true).queue();
        ctx.scheduler().executor().execute(() -> {
            VipPlan plan = vip.plans().findById(planId).orElse(null);
            int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guild.getId()));
            if (plan == null) {
                event.getHook().editOriginalComponents(Panels.container(accent,
                                Panels.text("Plano não encontrado. Escolha um plano da lista sugerida.")))
                        .useComponentsV2().queue(ok -> { }, err -> { });
                return;
            }
            Long durationMinutes = durationMinutesOrNull(dias, horas);
            VipService.GrantResult res = vip.grant(guild, target, plan, durationMinutes, grantedBy);
            String msg = res.ok()
                    ? Emojis.of(Emojis.GEM, "💎") + " VIP **" + plan.name() + "** concedido a "
                            + target.getAsMention() + "."
                    : (res.message() != null ? res.message() : "Falha ao conceder o VIP.");
            event.getHook().editOriginalComponents(Panels.container(accent, Panels.text(msg)))
                    .useComponentsV2().setAllowedMentions(List.of()).queue(ok -> { }, err -> { });
        });
    }

    @Override
    public void onAutocomplete(CommandAutoCompleteInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.replyChoices(List.of()).queue();
            return;
        }
        String focused = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<Command.Choice> choices = vip.plans().listEnabledByGuild(event.getGuild().getId()).stream()
                .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(focused))
                .limit(25)
                .map(p -> new Command.Choice(p.name(), p.id()))
                .toList();
        event.replyChoices(choices).queue();
    }

    /** {@code dias*1440 + horas*60} minutos, ou {@code null} (padrão do plano) se ambos vazios. */
    private static Long durationMinutesOrNull(Long dias, Long horas) {
        if (dias == null && horas == null) {
            return null;
        }
        long d = dias == null ? 0 : dias;
        long h = horas == null ? 0 : horas;
        return d * 1440 + h * 60;
    }
}
