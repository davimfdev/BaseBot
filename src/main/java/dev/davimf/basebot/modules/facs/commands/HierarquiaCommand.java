// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.commands
// 
// Class: HierarquiaCommand
// 
// Constructors:
//   - `Constructor` : `public HierarquiaCommand(HierarchyService service)`
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// 
// Fields:
//   - `Field` : `private final HierarchyService service`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.facs.hierarchy.HierarchyService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * /hierarquia publicar — posts the auto-updating chain-of-command panel in the current
 * channel (BOTSPECS Module 4). It then refreshes itself on role changes (debounced).
 */
public final class HierarquiaCommand implements SlashCommand {

    private final HierarchyService service;

    public HierarquiaCommand(HierarchyService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "hierarquia";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("hierarquia", "Painel de hierarquia da facção.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addSubcommands(new SubcommandData("publicar",
                        "Publica neste canal o painel auto-atualizável da hierarquia."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        service.publish(event);
    }
}
