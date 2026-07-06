// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.actions
// 
// Class: ActionService
// 
// Constructors:
//   - `Constructor` : `public ActionService(BotContext ctx, ActionRepository repo, EconomyRepository economy)`
// 
// Methods:
//   - `Method` : `private static final Logger log = LoggerFactory. getLogger(ActionService.class)`
//   - `Method` : `private static final Pattern TIME = Pattern. compile()`
//   - `Method` : `private static final Pattern DATE = Pattern. compile()`
//   - `Method` : `private static String skippedNote(List<String> skipped)`
//   - `Method` : `private static int membroIndex()`
//   - `Method` : `private static boolean rankRolesConfigured(GuildConfig cfg)`
//   - `Method` : `private static boolean isSetMember(Member member, GuildConfig cfg)`
//   - `Method` : `private boolean isPriority(Member member, GuildConfig cfg)`
//   - `Method` : `private Container panel(String actionId)`
//   - `Method` : `private TextChannel channelOrFallback(String guildId, String key, IReplyCallback event)`
//   - `Method` : `private boolean managerGate(IReplyCallback event)`
//   - `Method` : `private int accent(String guildId)`
//   - `Method` : `private static String guildId(IReplyCallback event)`
//   - `Method` : `package-private static LocalDateTime parseWhen(String date, String time)`
//   - `Method` : `package-private static String formatWhen(LocalDateTime dt)`
//   - `Method` : `private static String value(ModalInteractionEvent event, String key)`
// 
// Fields:
//   - `Field` : `public static final String CH_ESCALACOES`
//   - `Field` : `public static final String CH_ALINHAMENTOS`
//   - `Field` : `private final BotContext ctx`
//   - `Field` : `private final ActionRepository repo`
//   - `Field` : `private final EconomyRepository economy`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.sqlite.ActionTypeRepository.ActionType;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.actions.ActionRepository.Action;
import dev.davimf.basebot.modules.facs.actions.ActionRepository.Participant;
import dev.davimf.basebot.modules.facs.economy.EconomyRepository;
import dev.davimf.basebot.modules.facs.hierarchy.FacHierarchy;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Action / reservation logic (BOTSPECS Module 4). The {@code /painel-acoes} panel registers
 * actions (past or scheduled future), runs the Entrar/Sair Elite-priority queue, the
 * Alinhamento pings, and the Configurar controls (members, entry lock, time, Vitória/Derrota).
 * A scheduler reveals the Vitória/Derrota buttons when a scheduled action's time arrives.
 */
public final class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    public static final String CH_ESCALACOES = "acoes-escalacoes";
    public static final String CH_ALINHAMENTOS = "acoes-alinhamentos";

    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final Pattern DATE = Pattern.compile("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}|\\d{4}))?$");

    private final BotContext ctx;
    private final ActionRepository repo;
    private final EconomyRepository economy;

    public ActionService(BotContext ctx, ActionRepository repo, EconomyRepository economy) {
        this.ctx = ctx;
        this.repo = repo;
        this.economy = economy;
    }

    // --- management panel ------------------------------------------------------

    public void postManagementPanel(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, accent(guildId(event)),"Use em um canal de texto de um servidor.");
            return;
        }
        channel.sendMessageComponents(ActionView.managementPanel(accent(event.getGuild().getId())))
                .useComponentsV2().queue();
        Replies.ephemeral(event, accent(guildId(event)),Emojis.of(Emojis.WEAPON, "🔫") + " Painel de ações enviado.");
    }

    // --- registration flow -----------------------------------------------------

    public void register(ButtonInteractionEvent event) {
        if (!managerGate(event) || event.getGuild() == null) {
            return;
        }
        List<ActionType> types = ctx.database().actionTypes().listByGuild(event.getGuild().getId());
        if (types.isEmpty()) {
            Replies.ephemeral(event, accent(guildId(event)),"Nenhuma ação salva. Cadastre em `/setup → Ações` antes de registrar.");
            return;
        }
        event.replyComponents(ActionView.registerPickType(accent(guildId(event)), types))
                .useComponentsV2().setEphemeral(true).queue();
    }

    public void registerTypePicked(StringSelectInteractionEvent event) {
        ActionType type = ctx.database().actionTypes().find(event.getValues().get(0)).orElse(null);
        if (type == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação não encontrada.");
            return;
        }
        event.editComponents(ActionView.registerPastFuture(accent(guildId(event)), type.id(), type.name()))
                .useComponentsV2().queue();
    }

    public void registerFuture(ButtonInteractionEvent event, String typeId) {
        event.replyModal(ActionView.futureModal(typeId)).queue();
    }

    public void registerPast(ButtonInteractionEvent event, String typeId) {
        event.replyModal(ActionView.pastModal(typeId)).queue();
    }

    public void createFuture(ModalInteractionEvent event, String typeId) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Use em um servidor.");
            return;
        }
        LocalDateTime when = parseWhen(value(event, "data"), value(event, "hora"));
        if (when == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Hora ou data inválida. Use `HH:mm` e `dd/mm`, `dd/mm/yy` ou `dd/mm/yyyy`.");
            return;
        }
        ActionType type = ctx.database().actionTypes().find(typeId).orElse(null);
        if (type == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação salva não encontrada.");
            return;
        }
        String guildId = event.getGuild().getId();
        long dueAt = when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String id = repo.create(guildId, event.getUser().getId(), formatWhen(when),
                Math.max(0, type.maxContingent()), type.name(), Math.max(0, type.minContingent()),
                Math.max(0, type.dirtyMoney()), false, true, dueAt);
        logRegistered(guildId, type.name(), event.getUser().getId(), "agendada para " + formatWhen(when));
        postActionEmbed(event, id, guildId, "");
    }

    public void createPast(ModalInteractionEvent event, String typeId) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Use em um servidor.");
            return;
        }
        ActionType type = ctx.database().actionTypes().find(typeId).orElse(null);
        if (type == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação salva não encontrada.");
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        ModalMapping membros = event.getValue("membros");
        List<Member> selected = membros == null ? List.of() : membros.getAsMentions().getMembers();
        boolean validate = rankRolesConfigured(cfg);
        List<String> valid = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Member m : selected) {
            if (!validate || isSetMember(m, cfg)) {
                valid.add(m.getId());
            } else {
                skipped.add(m.getId());
            }
        }
        if (valid.isEmpty()) {
            Replies.ephemeral(event, accent(guildId(event)),"Nenhum dos selecionados é membro da facção (cargo Membro ou superior).");
            return;
        }
        String id = repo.create(guildId, event.getUser().getId(), null,
                Math.max(0, type.maxContingent()), type.name(), Math.max(0, type.minContingent()),
                Math.max(0, type.dirtyMoney()), true, false, 0);
        valid.forEach(uid -> repo.put(id, uid, ActionRepository.CONFIRMED, 0));
        logRegistered(guildId, type.name(), event.getUser().getId(),
                "já realizada (" + valid.size() + " participantes)");
        postActionEmbed(event, id, guildId, skippedNote(skipped));
    }

    private void postActionEmbed(IReplyCallback event, String id, String guildId, String note) {
        TextChannel target = channelOrFallback(guildId, CH_ESCALACOES, event);
        if (target == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Não há canal de escalações configurado e este não é um canal de texto. "
                    + "Defina um em `/setup → Ações`.");
            return;
        }
        try {
            target.sendMessageComponents(panel(id)).useComponentsV2()
                    .setAllowedMentions(List.of())
                    .queue(msg -> repo.setMessage(id, target.getId(), msg.getId()),
                            err -> log.error("Failed to post action panel {} in {}", id, target.getId(), err));
            Replies.ephemeral(event, accent(guildId(event)),Emojis.of(Emojis.WEAPON, "🔫") + " Ação registrada em " + target.getAsMention() + "." + note);
        } catch (RuntimeException e) {
            log.error("Failed to build/send action panel {}", id, e);
            Replies.ephemeral(event, accent(guildId(event)),"Falha ao postar o painel da ação: " + e.getMessage());
        }
    }

    // --- join / leave ----------------------------------------------------------

    public void join(ButtonInteractionEvent event, String actionId) {
        Action action = repo.find(actionId).orElse(null);
        if (action == null || !"OPEN".equals(action.status()) || action.isPast()
                || event.getMember() == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação indisponível.");
            return;
        }
        if (!action.entriesOpen()) {
            Replies.ephemeral(event, accent(guildId(event)),"As entradas desta ação estão bloqueadas.");
            return;
        }
        String userId = event.getUser().getId();
        if (repo.participant(actionId, userId).isPresent()) {
            Replies.ephemeral(event, accent(guildId(event)),"Você já está nesta ação.");
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
                notifyBumped(event, bump.get().userId(), action);
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
            Replies.ephemeral(event, accent(guildId(event)),"Ação indisponível.");
            return;
        }
        String userId = event.getUser().getId();
        Optional<Participant> existing = repo.participant(actionId, userId);
        if (existing.isEmpty()) {
            Replies.ephemeral(event, accent(guildId(event)),"Você não está nesta ação.");
            return;
        }
        boolean wasConfirmed = ActionRepository.CONFIRMED.equals(existing.get().kind());
        repo.remove(actionId, userId);
        if (wasConfirmed) {
            promoteIfRoom(action);
        }
        refresh(event, actionId);
    }

    private void promoteIfRoom(Action action) {
        if (action.capacity() > 0 && repo.countConfirmed(action.id()) < action.capacity()) {
            repo.reserveByPromotionOrder(action.id()).stream().findFirst().ifPresent(p ->
                    repo.put(action.id(), p.userId(), ActionRepository.CONFIRMED, p.priority()));
        }
    }

    // --- alignment -------------------------------------------------------------

    public void align(ButtonInteractionEvent event, String actionId) {
        if (!managerGate(event)) {
            return;
        }
        Action action = repo.find(actionId).orElse(null);
        if (action == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação indisponível.");
            return;
        }
        List<String> ids = new ArrayList<>();
        repo.confirmed(actionId).forEach(p -> ids.add(p.userId()));
        repo.reserveByPromotionOrder(actionId).forEach(p -> ids.add(p.userId()));
        if (ids.isEmpty()) {
            Replies.ephemeral(event, accent(guildId(event)),"Ninguém confirmado para alinhar.");
            return;
        }
        int confirmedCount = repo.countConfirmed(actionId);
        String pings = ids.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(" "));
        String name = action.actionName() == null || action.actionName().isBlank()
                ? "Ação" : action.actionName();

        TextChannel target = channelOrFallback(action.guildId(), CH_ALINHAMENTOS, event);
        if (target == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Configure o canal de alinhamentos em `/setup → Ações`.");
            return;
        }
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.VOICE, "🔊") + " Alinhamento · " + name));
        kids.add(Panels.divider());
        if (action.minContingent() > 0 && confirmedCount < action.minContingent()) {
            kids.add(Panels.text(Emojis.of(Emojis.WARN, "⚠️") + " **Abaixo do mínimo** · `"
                    + confirmedCount + "/" + action.minContingent() + "`"));
        }
        kids.add(Panels.text(pings));
        target.sendMessageComponents(Panels.container(accent(action.guildId()),
                        kids.toArray(new ContainerChildComponent[0])))
                .useComponentsV2()
                .setAllowedMentions(EnumSet.of(Message.MentionType.USER))
                .queue();
        Replies.ephemeral(event, accent(guildId(event)),Emojis.of(Emojis.VOICE, "🔊") + " Alinhamento enviado em " + target.getAsMention() + ".");
    }

    // --- configure -------------------------------------------------------------

    public void config(ButtonInteractionEvent event, String actionId) {
        if (!managerGate(event)) {
            return;
        }
        Action action = repo.find(actionId).orElse(null);
        if (action == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação indisponível.");
            return;
        }
        event.replyComponents(ActionView.configPanel(accent(guildId(event)), action))
                .useComponentsV2().setEphemeral(true).queue();
    }

    public void backfill(EntitySelectInteractionEvent event, String actionId) {
        if (!managerGate(event) || event.getGuild() == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        boolean validate = rankRolesConfigured(cfg);
        List<String> skipped = new ArrayList<>();
        for (Member m : event.getMentions().getMembers()) {
            if (!validate || isSetMember(m, cfg)) {
                repo.put(actionId, m.getId(), ActionRepository.CONFIRMED, 0);
            } else {
                skipped.add(m.getId());
            }
        }
        if (skipped.isEmpty()) {
            event.deferEdit().queue();
        } else {
            Replies.ephemeral(event, accent(guildId(event)),skippedNote(skipped).trim());
        }
        refreshMain(actionId);
    }

    public void removeMember(EntitySelectInteractionEvent event, String actionId) {
        if (!managerGate(event)) {
            return;
        }
        Action action = repo.find(actionId).orElse(null);
        event.getValues().forEach(v -> repo.remove(actionId, v.getId()));
        if (action != null) {
            promoteIfRoom(action);
        }
        event.deferEdit().queue();
        refreshMain(actionId);
    }

    public void toggleEntries(ButtonInteractionEvent event, String actionId) {
        if (!managerGate(event)) {
            return;
        }
        Action action = repo.find(actionId).orElse(null);
        if (action == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação indisponível.");
            return;
        }
        repo.setEntriesOpen(actionId, !action.entriesOpen());
        refreshMain(actionId);
        repo.find(actionId).ifPresent(updated -> event.editComponents(
                ActionView.configPanel(accent(guildId(event)), updated)).useComponentsV2().queue());
    }

    public void changeTime(ButtonInteractionEvent event, String actionId) {
        if (!managerGate(event)) {
            return;
        }
        Action action = repo.find(actionId).orElse(null);
        if (action == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação indisponível.");
            return;
        }
        event.replyModal(ActionView.timeModal(actionId, action)).queue();
    }

    public void setTime(ModalInteractionEvent event, String actionId) {
        LocalDateTime when = parseWhen(value(event, "data"), value(event, "hora"));
        if (when == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Hora ou data inválida. Use `HH:mm` e `dd/mm`, `dd/mm/yy` ou `dd/mm/yyyy`.");
            return;
        }
        long dueAt = when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        repo.setWhen(actionId, formatWhen(when), dueAt);
        refreshMain(actionId);
        Replies.ephemeral(event, accent(guildId(event)),"" + Emojis.of(Emojis.CLOCK, "🕒") + " Horário atualizado para " + formatWhen(when) + ".");
    }

    // --- "Editar ações" --------------------------------------------------------

    public void manage(ButtonInteractionEvent event) {
        if (!managerGate(event) || event.getGuild() == null) {
            return;
        }
        event.replyComponents(ActionView.manageList(accent(guildId(event)),
                        repo.listOpenByGuild(event.getGuild().getId())))
                .useComponentsV2().setEphemeral(true).queue();
    }

    public void managePick(StringSelectInteractionEvent event) {
        Action action = repo.find(event.getValues().get(0)).orElse(null);
        if (action == null) {
            Replies.ephemeral(event, accent(guildId(event)),"Ação não encontrada (talvez já encerrada).");
            return;
        }
        event.editComponents(ActionView.configPanel(accent(guildId(event)), action))
                .useComponentsV2().queue();
    }

    // --- result ----------------------------------------------------------------

    public void result(ButtonInteractionEvent event, String actionId, String status, String label) {
        if (!managerGate(event)) {
            return;
        }
        repo.setStatus(actionId, status);
        repo.find(actionId).ifPresent(a -> {
            if ("VICTORY".equals(status) && a.dirtyMoney() > 0) {
                economy.addStock(a.guildId(), "dinheiro_sujo", a.dirtyMoney());
            }
            logResult(a, label, event.getUser().getId());
            refreshMain(actionId);
        });
        Replies.ephemeral(event, accent(guildId(event)),"Resultado registrado: " + label);
    }

    // --- scheduler -------------------------------------------------------------

    /** Reveals Vitória/Derrota on scheduled actions whose time has arrived (runs every minute). */
    public void sweepDueActions() {
        for (Action a : repo.dueForReveal(System.currentTimeMillis())) {
            repo.markDueNotified(a.id());
            refreshMain(a.id());
        }
    }

    // --- helpers ---------------------------------------------------------------

    private void logRegistered(String guildId, String name, String actorId, String detail) {
        FacsLog.post(ctx, guildId, "log-acoes", "## " + Emojis.of(Emojis.WEAPON, "🔫") + " Ação registrada · " + name
                + "\n---\n" + Emojis.of(Emojis.LIST, "📋") + " **Detalhe** · " + detail + "\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Por** · <@" + actorId + ">");
    }

    private void logResult(Action a, String label, String actorId) {
        String name = a.actionName() == null || a.actionName().isBlank() ? "Ação" : a.actionName();
        FacsLog.post(ctx, a.guildId(), "log-acoes",
                "## " + Emojis.of(Emojis.WEAPON, "🔫") + " " + name + " · " + label + "\n---"
                        + (a.whenText() == null ? "" : "\n" + Emojis.of(Emojis.CLOCK, "🕒") + " **Quando** · `" + a.whenText() + "`")
                        + "\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Registrado por** · <@" + actorId + ">");
        ctx.database().actionLogs().log(a.guildId(), actorId, a.id(), "ACTION_RESULT", label);
    }

    /** DMs a member who was bumped to the reserve by an arriving Elite (silent if DMs are closed). */
    private void notifyBumped(ButtonInteractionEvent event, String bumpedUserId, Action action) {
        String when = action.whenText() == null ? "" : " (" + action.whenText() + ")";
        String name = action.actionName() == null || action.actionName().isBlank()
                ? "uma ação" : "**" + action.actionName() + "**";
        Container dm = Panels.container(accent(action.guildId()),
                Panels.text("## " + Emojis.of(Emojis.WARN, "⚠️") + " Movido para a reserva"),
                Panels.divider(),
                Panels.text("> Você foi movido para a reserva de " + name + when
                        + " porque um membro Elite confirmou presença."));
        event.getJDA().retrieveUserById(bumpedUserId)
                .flatMap(u -> u.openPrivateChannel())
                .flatMap(ch -> ch.sendMessageComponents(dm).useComponentsV2())
                .queue(ok -> {}, err -> {});
    }

    private static String skippedNote(List<String> skipped) {
        if (skipped.isEmpty()) {
            return "";
        }
        return " Ignorados (sem cargo de Membro ou superior): "
                + skipped.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(" "));
    }

    /** Index of the "membro" rung — members at or above it are considered faction members. */
    private static int membroIndex() {
        for (int i = 0; i < FacHierarchy.LEVELS.size(); i++) {
            if ("membro".equals(FacHierarchy.LEVELS.get(i).key())) {
                return i;
            }
        }
        return FacHierarchy.LEVELS.size() - 1;
    }

    /** True if at least one rank role (Membro or higher) is configured — else validation is off. */
    private static boolean rankRolesConfigured(GuildConfig cfg) {
        int membroIdx = membroIndex();
        for (int i = 0; i <= membroIdx; i++) {
            if (cfg.role(FacHierarchy.LEVELS.get(i).key()) != null) {
                return true;
            }
        }
        return false;
    }

    /** True if the member holds the Membro role or any role above it in the chain of command. */
    private static boolean isSetMember(Member member, GuildConfig cfg) {
        int membroIdx = membroIndex();
        for (int i = 0; i <= membroIdx; i++) {
            String roleId = cfg.role(FacHierarchy.LEVELS.get(i).key());
            if (roleId != null && member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

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
        // Elite Feminina shares Elite's queue priority regardless of its list position.
        String femId = cfg.role("elite-feminina");
        return femId != null && member.getRoles().stream().anyMatch(r -> r.getId().equals(femId));
    }

    private Container panel(String actionId) {
        Action action = repo.find(actionId).orElseThrow();
        return ActionView.panel(accent(action.guildId()), action,
                repo.confirmed(actionId), repo.reserveByPromotionOrder(actionId));
    }

    private void refresh(ButtonInteractionEvent event, String actionId) {
        event.editComponents(panel(actionId)).useComponentsV2().setAllowedMentions(List.of()).queue();
    }

    /** Edits the stored panel message (for actions taken from an ephemeral panel / scheduler). */
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

    /** Resolves a configured action channel by key, falling back to the interaction channel. */
    private TextChannel channelOrFallback(String guildId, String key, IReplyCallback event) {
        String channelId = ctx.database().guildConfig().findOrEmpty(guildId).channel(key);
        if (channelId != null && ctx.jda() != null) {
            TextChannel ch = ctx.jda().getTextChannelById(channelId);
            if (ch != null) {
                return ch;
            }
        }
        return event.getChannel() instanceof TextChannel tc ? tc : null;
    }

    private boolean managerGate(IReplyCallback event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId(event));
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.ACOES)) {
            Replies.ephemeral(event, accent(guildId(event)), "Apenas a gerência de **Ações** pode usar isto.");
            return false;
        }
        return true;
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }

    private static String guildId(IReplyCallback event) {
        return event.getGuild() == null ? "0" : event.getGuild().getId();
    }

    // --- date/time parsing -----------------------------------------------------

    /** Parses "data" + "hora" into a LocalDateTime, or null if either is invalid. */
    static LocalDateTime parseWhen(String date, String time) {
        Matcher t = TIME.matcher(time == null ? "" : time.trim());
        Matcher d = DATE.matcher(date == null ? "" : date.trim());
        if (!t.matches() || !d.matches()) {
            return null;
        }
        int hh = Integer.parseInt(t.group(1));
        int mm = Integer.parseInt(t.group(2));
        if (hh > 23 || mm > 59) {
            return null;
        }
        int day = Integer.parseInt(d.group(1));
        int month = Integer.parseInt(d.group(2));
        int year;
        if (d.group(3) == null) {
            year = LocalDate.now().getYear();
        } else if (d.group(3).length() == 2) {
            year = 2000 + Integer.parseInt(d.group(3));
        } else {
            year = Integer.parseInt(d.group(3));
        }
        try {
            return LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hh, mm));
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    static String formatWhen(LocalDateTime dt) {
        return String.format("%02d/%02d/%04d %02d:%02d",
                dt.getDayOfMonth(), dt.getMonthValue(), dt.getYear(), dt.getHour(), dt.getMinute());
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? "" : m.getAsString();
    }
}
