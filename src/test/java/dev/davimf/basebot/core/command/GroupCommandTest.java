package dev.davimf.basebot.core.command;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A montagem do {@link GroupCommand} (subcomandos + cópia de opções) é testável sem JDA vivo:
 * {@code Commands.slash(...)} devolve builders, não faz rede.
 */
class GroupCommandTest {

    /** Um SlashCommand de mentira, sem Mockito: só o {@code data()} importa para estes testes. */
    private static SlashCommand fake(String name, OptionData... options) {
        return new SlashCommand() {
            @Override public String name() { return name; }
            @Override public SlashCommandData data() {
                SlashCommandData d = Commands.slash(name, "desc de " + name);
                if (options.length > 0) {
                    d.addOptions(options);
                }
                return d;
            }
            @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) { }
        };
    }

    @Test
    void nameIsTheGroupNameNotTheDelegates() {
        GroupCommand g = new GroupCommand("top", "Rankings", DefaultMemberPermissions.ENABLED,
                List.of(GroupCommand.Sub.open("rico", "Ricos", fake("rico"))));
        assertEquals("top", g.name());
    }

    @Test
    void buildsOneSubcommandPerSub() {
        GroupCommand g = new GroupCommand("top", "Rankings", DefaultMemberPermissions.ENABLED, List.of(
                GroupCommand.Sub.open("rico", "Ricos", fake("rico")),
                GroupCommand.Sub.open("xp", "Nível", fake("xp")),
                GroupCommand.Sub.open("call", "Call", fake("topcall"))));

        List<SubcommandData> subs = g.data().getSubcommands();
        assertEquals(3, subs.size());
        assertEquals(Set.of("rico", "xp", "call"),
                subs.stream().map(SubcommandData::getName).collect(Collectors.toSet()));
    }

    @Test
    void copiesTheDelegatesOptionsIntoTheSubcommand() {
        SlashCommand mover = fake("voice-move",
                new OptionData(OptionType.USER, "membro", "quem", true),
                new OptionData(OptionType.CHANNEL, "canal", "para onde", true));
        GroupCommand g = new GroupCommand("voz", "Voz", DefaultMemberPermissions.ENABLED,
                List.of(GroupCommand.Sub.open("mover", "Move", mover)));

        SubcommandData sub = g.data().getSubcommands().get(0);
        assertEquals(List.of("membro", "canal"),
                sub.getOptions().stream().map(OptionData::getName).toList());
    }

    @Test
    void subWithoutOptionsProducesAnEmptyOptionList() {
        GroupCommand g = new GroupCommand("canal", "Canal", DefaultMemberPermissions.ENABLED,
                List.of(GroupCommand.Sub.open("trancar", "Tranca", fake("lock"))));
        assertTrue(g.data().getSubcommands().get(0).getOptions().isEmpty());
    }
}
