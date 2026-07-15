package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/** Render dos eventos de chat (namespace "chatevt"). */
public final class ChatEventView {

    public static final String NS = "chatevt";

    private ChatEventView() {}

    public static Container panel(int accent, ChatEvent ev) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.GIFT, "🎉") + " Evento de chat!"));
        kids.add(Panels.divider());
        kids.add(Panels.text(prompt(ev)));
        switch (ev.type()) {
            case QUIZ -> {
                List<Button> buttons = new ArrayList<>();
                List<String> opts = ev.options();
                for (int i = 0; i < opts.size(); i++) {
                    buttons.add(Button.secondary(ComponentId.of(NS, "ans", String.valueOf(i)), trim(opts.get(i))));
                }
                kids.add(ActionRow.of(buttons));
            }
            case GRAB -> kids.add(ActionRow.of(
                    Button.success(ComponentId.of(NS, "grab"), "Pegar!").withEmoji(Emojis.button(Emojis.GIFT))));
            default -> kids.add(Panels.text("-# Responda no chat! O primeiro a acertar leva."));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container resolved(int accent, ChatEvent ev, String winnerMention, String rewardText) {
        String verb = ev.type() == ChatEventType.GRAB ? "pegou primeiro" : "acertou";
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.TROPHY, "🏆") + " Evento encerrado"),
                Panels.divider(),
                Panels.text(recap(ev)),
                Panels.divider(),
                Panels.text(Emojis.of(Emojis.TROPHY, "🏆") + " " + winnerMention + " " + verb + "! Ganhou " + rewardText));
    }

    public static Container expired(int accent) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CLOCK, "⏰") + " Evento expirado"),
                Panels.divider(),
                Panels.text("Ninguém acertou a tempo."));
    }

    /** Reexibe o enunciado ao encerrar, sem revelar a alternativa correta. */
    private static String recap(ChatEvent ev) {
        return switch (ev.type()) {
            case QUIZ -> {
                StringBuilder sb = new StringBuilder("**").append(ev.prompt()).append("**");
                List<String> opts = ev.options();
                for (int i = 0; i < opts.size(); i++) {
                    sb.append("\n> ").append((char) ('A' + i)).append(") ").append(opts.get(i));
                }
                yield sb.toString();
            }
            case MATH -> "Resolva: **" + ev.prompt() + "**";
            case TYPING -> "Palavra: **" + ev.answer() + "**";
            case GRAB -> "Corrida do " + Emojis.of(Emojis.GIFT, "🎁") + "!";
        };
    }

    private static String prompt(ChatEvent ev) {
        return switch (ev.type()) {
            case QUIZ -> "**" + ev.prompt() + "**\nEscolha a alternativa correta:";
            case TYPING -> "Primeiro a digitar: **" + ev.answer() + "**";
            case MATH -> "Resolva: **" + ev.prompt() + "**";
            case GRAB -> "Clique em **Pegar!** antes de todo mundo!";
        };
    }

    private static String trim(String s) {
        return s.length() > 78 ? s.substring(0, 78) : s;
    }
}
