package dev.davimf.basebot.core.command;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.EnumSet;
import java.util.List;

/**
 * Um comando de topo que agrupa vários comandos como subcomandos, para caber no limite de 100
 * comandos por servidor do Discord: um comando com subcomandos ocupa <b>um</b> slot.
 *
 * <p>Cada {@link Sub} embrulha um {@link SlashCommand} já existente. As opções vêm do próprio
 * delegate ({@code data().getOptions()}) e a execução é encaminhada a ele sem alteração — a lógica
 * de cada ação não muda de lugar.
 *
 * <p><b>Permissões.</b> O Discord aplica {@code setDefaultPermissions} só no comando de topo, nunca
 * por subcomando. Como os delegates de staff exigem permissões diferentes entre si, cada {@link Sub}
 * declara a sua e o grupo a verifica no {@code execute}. Isso é enforcement em código — mais forte
 * que o gate visual do Discord, que um admin pode reconfigurar. O {@code defaultPermissions} do topo
 * fica sendo só a visibilidade padrão do comando.
 */
public final class GroupCommand implements SlashCommand, AutocompleteCommand {

    /** Um subcomando: nome/descrição próprios, a permissão exigida e o comando que executa. */
    public record Sub(String name, String description, EnumSet<Permission> required, SlashCommand delegate) {

        /** Sem exigência de permissão (rankings, etc.). */
        public static Sub open(String name, String description, SlashCommand delegate) {
            return new Sub(name, description, EnumSet.noneOf(Permission.class), delegate);
        }

        /** Exige que quem invoca tenha todas as {@code required} (verificado no {@code execute}). */
        public static Sub gated(String name, String description, SlashCommand delegate, Permission... required) {
            EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
            set.addAll(List.of(required));
            return new Sub(name, description, set, delegate);
        }
    }

    private final String name;
    private final String description;
    private final DefaultMemberPermissions defaultPermissions;
    private final List<Sub> subs;

    public GroupCommand(String name, String description,
                        DefaultMemberPermissions defaultPermissions, List<Sub> subs) {
        this.name = name;
        this.description = description;
        this.defaultPermissions = defaultPermissions;
        this.subs = List.copyOf(subs);
    }

    @Override public String name() { return name; }

    @Override public SlashCommandData data() {
        SlashCommandData cmd = Commands.slash(name, description).setDefaultPermissions(defaultPermissions);
        for (Sub sub : subs) {
            SubcommandData sd = new SubcommandData(sub.name(), sub.description());
            List<OptionData> options = sub.delegate().data().getOptions();
            if (!options.isEmpty()) {
                sd.addOptions(options);
            }
            cmd.addSubcommands(sd);
        }
        return cmd;
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        Sub sub = find(event.getSubcommandName());
        if (sub == null) {
            Replies.ephemeral(event, ctx, "Ação desconhecida.");
            return;
        }
        if (!sub.required().isEmpty()) {
            Member member = event.getMember();
            if (member == null || !member.hasPermission(sub.required())) {
                Replies.ephemeral(event, ctx, "Você não tem permissão para usar isto.");
                return;
            }
        }
        sub.delegate().execute(event, ctx);
    }

    /** Forwards autocomplete to the delegate of the focused subcommand, if it implements
     *  {@link AutocompleteCommand}. {@link CommandManager} only checks the top-level command for
     *  this capability, so the group itself must relay — otherwise a delegate's autocomplete
     *  (e.g. an option using {@code true} for autocomplete) never fires. */
    @Override
    public void onAutocomplete(CommandAutoCompleteInteractionEvent event, BotContext ctx) {
        Sub sub = find(event.getSubcommandName());
        if (sub != null && sub.delegate() instanceof AutocompleteCommand ac) {
            ac.onAutocomplete(event, ctx);
        }
    }

    private Sub find(String subName) {
        for (Sub sub : subs) {
            if (sub.name().equals(subName)) {
                return sub;
            }
        }
        return null;
    }
}
