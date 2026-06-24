package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.actions.ActionRepository.Action;
import dev.davimf.basebot.modules.facs.actions.ActionRepository.Participant;
import dev.davimf.basebot.modules.facs.hierarchy.FacHierarchy;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Action / reservation logic (BOTSPECS Module 4): create the panel, the Entrar/Sair
 * priority queue (Elite bumps normal members to the reserve when full), Alinhamento
 * pings, and the Configurar controls (backfill late attendees, Vitória/Derrota, encerrar).
 */
public final class ActionService {

    private final BotContext ctx;
    private final ActionRepository repo;

    public ActionService(BotContext ctx, ActionRepository repo) {
        this.ctx = ctx;
        this.repo = repo;
    }

    // --- creation --------------------------------------------------------------

    public void create(ModalInteractionEvent event) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.reply("Use em um canal de texto.").setEphemeral(true).queue();
            return;
        }
        String quando = value(event, "quando");
        int vagas = parseInt(value(event, "vagas"));
        String guildId = event.getGuild().getId();
        String id = repo.create(guildId, event.getUser().getId(), quando, Math.max(0, vagas));

        event.reply("⚔️ Ação criada.").setEphemeral(true).queue();
        channel.sendMessageComponents(panel(id)).useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(msg -> repo.setMessage(id, channel.getId(), msg.getId()));
    }

    // --- join / leave ----------------------------------------------------------

    public void join(ButtonInteractionEvent event, String actionId) {
        Action action = repo.find(actionId).orElse(null);
        if (action == null || !"OPEN".equals(action.status()) || event.getMember() == null) {
            event.reply("Ação indisponível.").setEphemeral(true).queue();
            return;
        }
        String userId = event.getUser().getId();
        if (repo.participant(actionId, userId).isPresent()) {
            event.reply("Você já está nesta ação.").setEphemeral(true).queue();
            return;
        }
        int priority = isPriority(event.getMember(),
                ctx.database().guildConfig().findOrEmpty(action.guildId())) ? 1 : 0;
        int confirmed = repo.countConfirmed(actionId);
        boolean hasRoom = action.capacity() == 0 || confirmed < action.capacity();

        if (hasRoom) {
            repo.put(actionId, userId, ActionRepository.CONFIRMED, priority);
        } else if (priority == 1) {
            Optional<Participant> bump = repo.confirmedByBumpOrder(actionId).stream()
                    .filter(p -> p.priority() == 0).findFirst();
            if (bump.isPresent()) {
                repo.put(actionId, bump.get().userId(), ActionRepository.RESERVE, bump.get().priority());
                repo.put(actionId, userId, ActionRepository.CONFIRMED, 1);
            } else {
                repo.put(actionId, userId, ActionRepository.RESERVE, 1);
            }
        } else {
            repo.put(actionId, userId, ActionRepository.RESERVE, 0);
        }
        refresh(event, actionId);
    }

    public void leave(ButtonInteractionEvent event, String actionId) {
        Action action = repo.find(actionId).orElse(null);
        if (action == null || event.getMember() == null) {
            event.reply("Ação indisponível.").setEphemeral(true).queue();
            return;
        }
        String userId = event.getUser().getId();
        Optional<Participant> existing = repo.participant(actionId, userId);
        if (existing.isEmpty()) {
            event.reply("Você não está nesta ação.").setEphemeral(true).queue();
            return;
        }
        boolean wasConfirmed = ActionRepository.CONFIRMED.equals(existing.get().kind());
        repo.remove(actionId, userId);
        if (wasConfirmed && action.capacity() > 0 && repo.countConfirmed(actionId) < action.capacity()) {
            repo.reserveByPromotionOrder(actionId).stream().findFirst().ifPresent(p ->
                    repo.put(actionId, p.userId(), ActionRepository.CONFIRMED, p.priority()));
        }
        refresh(event, actionId);
    }

    // --- alignment + config ----------------------------------------------------

    public void align(ButtonInteractionEvent event, String actionId) {
        if (!managerGate(event)) {
            return;
        }
        List<String> ids = new ArrayList<>();
        repo.confirmed(actionId).forEach(p -> ids.add(p.userId()));
        repo.reserveByPromotionOrder(actionId).forEach(p -> ids.add(p.userId()));
        if (ids.isEmpty()) {
            event.reply("Ninguém confirmado para alinhar.").setEphemeral(true).queue();
            return;
        }
        String pings = ids.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(" "));
        event.reply("📣 **Alinhamento!** " + pings).queue();
    }

    public void config(ButtonInteractionEvent event, String actionId) {
        if (!managerGate(event)) {
            return;
        }
        event.replyComponents(ActionView.configPanel(accent(guildId(event)), actionId))
                .useComponentsV2().setEphemeral(true).queue();
    }

    public void backfill(EntitySelectInteractionEvent event, String actionId) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("Apenas a gerência pode adicionar presenças.").setEphemeral(true).queue();
            return;
        }
        event.getValues().forEach(v -> repo.put(actionId, v.getId(), ActionRepository.CONFIRMED, 0));
        refreshMain(actionId);
        event.reply("Presenças adicionadas.").setEphemeral(true).queue();
    }

    public void result(ButtonInteractionEvent event, String actionId, String status, String label) {
        if (!managerGate(event)) {
            return;
        }
        repo.setStatus(actionId, status);
        repo.find(actionId).ifPresent(a -> {
            logResult(a, label, event.getUser().getId());
            refreshMain(actionId);
        });
        event.editComponents(ActionView.configPanel(accent(guildId(event)), actionId))
                .useComponentsV2().queue();
        event.getHook().sendMessage("Resultado registrado: " + label).setEphemeral(true).queue();
    }

    private void logResult(Action a, String label, String actorId) {
        FacsLog.post(ctx, a.guildId(), "log-acoes",
                "## ⚔️ Ação — " + label + "\n**Quando:** " + a.whenText()
                        + "\n**Registrado por:** <@" + actorId + ">");
        ctx.database().actionLogs().log(a.guildId(), actorId, null, "ACTION_RESULT", label);
    }

    // --- helpers ---------------------------------------------------------------

    private boolean isPriority(Member member, GuildConfig cfg) {
        int eliteIdx = FacHierarchy.LEVELS.size();
        for (int i = 0; i < FacHierarchy.LEVELS.size(); i++) {
            if ("elite".equals(FacHierarchy.LEVELS.get(i).key())) {
                eliteIdx = i;
                break;
            }
        }
        for (int i = 0; i <= eliteIdx && i < FacHierarchy.LEVELS.size(); i++) {
            String roleId = cfg.role(FacHierarchy.LEVELS.get(i).key());
            if (roleId != null && member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

    private net.dv8tion.jda.api.components.container.Container panel(String actionId) {
        Action action = repo.find(actionId).orElseThrow();
        return ActionView.panel(accent(action.guildId()), action,
                repo.confirmed(actionId), repo.reserveByPromotionOrder(actionId));
    }

    private void refresh(ButtonInteractionEvent event, String actionId) {
        event.editComponents(panel(actionId)).useComponentsV2().setAllowedMentions(List.of()).queue();
    }

    /** Edits the stored panel message (for actions taken from the ephemeral config panel). */
    private void refreshMain(String actionId) {
        Action action = repo.find(actionId).orElse(null);
        if (action == null || action.channelId() == null || action.messageId() == null || ctx.jda() == null) {
            return;
        }
        TextChannel channel = ctx.jda().getTextChannelById(action.channelId());
        if (channel == null) {
            return;
        }
        channel.retrieveMessageById(action.messageId())
                .flatMap(m -> m.editMessageComponents(panel(actionId)).useComponentsV2()
                        .setAllowedMentions(List.of()))
                .queue(ok -> {}, err -> {});
    }

    private boolean managerGate(ButtonInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("Apenas a gerência pode usar esta ação.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }

    private static String guildId(ButtonInteractionEvent event) {
        return event.getGuild() == null ? "0" : event.getGuild().getId();
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? "" : m.getAsString();
    }
}
