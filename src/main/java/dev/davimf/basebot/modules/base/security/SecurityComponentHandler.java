package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.postgres.VerificationQuestionRepository;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Security buttons posted outside /setup (namespace "sec") — the anti-raid
 *  "Desativar lockdown" alert button and the verification "Verificar" flow
 *  (user-select → questions modal → approval queue with Aprovar/Recusar). */
public final class SecurityComponentHandler implements ComponentHandler {

    private final BotContext ctx;

    public SecurityComponentHandler(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String namespace() {
        return AntiRaidService.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        switch (id.action()) {
            case "raidunlock" -> raidUnlock(event, ctx);
            case "verify" -> verifyStart(event, ctx);
            case "vskip" -> verifySkip(event, ctx);
            case "vapprove" -> approve(event, ctx, id.arg(0));
            case "vreject" -> reject(event, ctx, id.arg(0));
            default -> { /* not ours */ }
        }
    }

    private void raidUnlock(ButtonInteractionEvent event, BotContext ctx) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas quem tem **Gerenciar Servidor** pode desativar o lockdown.");
            return;
        }
        AntiRaidService.unlock(ctx, event.getGuild());
        Replies.reply(event, ctx, "Lockdown desativado.");
    }

    // --- verificação: início do fluxo ------------------------------------------

    private void verifyStart(ButtonInteractionEvent event, BotContext ctx) {
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.verify(cfg)) {
            Replies.ephemeral(event, ctx, "A verificação não está ativa.");
            return;
        }
        String userId = event.getUser().getId();
        if (ctx.database().verification().isVerified(guild.getId(), userId)) {
            Replies.ephemeral(event, ctx, "Você já está verificado neste servidor.");
            return;
        }
        if (ctx.database().verification().hasPending(guild.getId(), userId)) {
            Replies.ephemeral(event, ctx, "Você já tem um pedido em análise, aguarde um moderador.");
            return;
        }
        if (cfg.channel(SecurityConfig.CHANNEL_VERIFY) == null) {
            Replies.ephemeral(event, ctx,
                    "A verificação não tem canal de aprovação configurado. Avise a administração.");
            return;
        }
        List<VerificationQuestionRepository.Question> questions =
                ctx.database().verificationQuestions().listByGuild(guild.getId());
        boolean userSelect = SecurityConfig.verifyUserSelect(cfg);

        if (userSelect) {
            // 1º passo: seleção de usuário (o modal, se houver, vem depois).
            EntitySelectMenu menu = EntitySelectMenu
                    .create(ComponentId.of(AntiRaidService.NS, "vusers"), EntitySelectMenu.SelectTarget.USER)
                    .setPlaceholder("Marque quem você conhece aqui (opcional)")
                    .setRequiredRange(0, 4)
                    .build();
            Button skip = Button.primary(ComponentId.of(AntiRaidService.NS, "vskip"), "Continuar")
                    .withEmoji(Emojis.button(Emojis.CHECK_YES));
            Container prompt = Panels.container(EmbedColor.resolve(cfg),
                    Panels.text("## " + Emojis.of(Emojis.MEMBERS, "👥") + " Quem você conhece?"),
                    Panels.divider(),
                    Panels.text("> Marque membros que já te conhecem aqui (opcional) e continue."),
                    ActionRow.of(menu),
                    ActionRow.of(skip));
            event.replyComponents(prompt).useComponentsV2().setEphemeral(true).queue();
            return;
        }
        if (!questions.isEmpty()) {
            event.replyModal(verifyModal("", questions)).queue();
            return;
        }
        // Sem perguntas nem seleção: pedido direto.
        finalizeRequest(event, ctx, "*(sem perguntas)*");
    }

    // --- verificação: seleção de usuário e modal -------------------------------

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || !"vusers".equals(id.action())) {
            return;
        }
        String users = event.getMentions().getUsers().stream()
                .limit(4).map(u -> u.getId()).collect(Collectors.joining("-"));
        advanceUserSelect(event, ctx, users);
    }

    /** "Continuar" no passo de seleção: avança sem marcar ninguém. */
    private void verifySkip(ButtonInteractionEvent event, BotContext ctx) {
        advanceUserSelect(event, ctx, "");
    }

    /** Passo seguinte à seleção de usuário com os {@code users} escolhidos (pode ser vazio):
     *  abre o modal de perguntas se houver, senão finaliza o pedido direto. */
    private <E extends IReplyCallback & IModalCallback> void advanceUserSelect(
            E event, BotContext ctx, String users) {
        List<VerificationQuestionRepository.Question> questions =
                ctx.database().verificationQuestions().listByGuild(event.getGuild().getId());
        if (!questions.isEmpty()) {
            event.replyModal(verifyModal(users, questions)).queue();
        } else {
            finalizeRequest(event, ctx, renderAnswers(event.getGuild(), users, List.of(), List.of()));
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || !"vsubmit".equals(id.action())) {
            return;
        }
        String users = id.arg(0);
        List<VerificationQuestionRepository.Question> questions =
                ctx.database().verificationQuestions().listByGuild(event.getGuild().getId());
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            var v = event.getValue("q" + i);
            answers.add(v == null ? "" : v.getAsString());
        }
        List<String> prompts = questions.stream()
                .map(VerificationQuestionRepository.Question::prompt).toList();
        finalizeRequest(event, ctx, renderAnswers(event.getGuild(), users, prompts, answers));
    }

    private Modal verifyModal(String users, List<VerificationQuestionRepository.Question> questions) {
        List<Label> rows = new ArrayList<>();
        for (int i = 0; i < questions.size() && i < 5; i++) {
            var q = questions.get(i);
            TextInput input = TextInput.create("q" + i, TextInputStyle.PARAGRAPH)
                    .setRequired(q.required())
                    .setMaxLength(300)
                    .build();
            // Discord caps a Label at 45 chars — truncate long prompts to stay valid.
            String label = q.prompt() == null ? ("Pergunta " + (i + 1)) : q.prompt();
            if (label.length() > 45) {
                label = label.substring(0, 45);
            }
            rows.add(Label.of(label, input));
        }
        return Modal.create(ComponentId.of(AntiRaidService.NS, "vsubmit", users), "Verificação")
                .addComponents(rows)
                .build();
    }

    // --- verificação: render das respostas e postagem na fila ------------------

    private String renderAnswers(Guild guild, String usersJoined,
                                 List<String> prompts, List<String> answers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < prompts.size(); i++) {
            String a = i < answers.size() && answers.get(i) != null && !answers.get(i).isBlank()
                    ? answers.get(i) : "*(vazio)*";
            sb.append("**").append(prompts.get(i)).append("**\n> ").append(a).append('\n');
        }
        if (usersJoined != null && !usersJoined.isBlank()) {
            String mentions = Arrays.stream(usersJoined.split("-"))
                    .filter(s -> !s.isBlank()).map(s -> "<@" + s + ">").collect(Collectors.joining(", "));
            sb.append("**Conhece:** ").append(mentions).append('\n');
        }
        return sb.length() == 0 ? "*(sem respostas)*" : sb.toString();
    }

    private void finalizeRequest(IReplyCallback event, BotContext ctx, String answers) {
        Guild guild = event.getGuild();
        var user = event.getUser();
        boolean opened = ctx.database().verification()
                .openRequest(guild.getId(), user.getId(), answers, System.currentTimeMillis());
        if (!opened) {
            Replies.ephemeral(event, ctx, "Você já tem um pedido em análise, aguarde um moderador.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String chId = cfg.channel(SecurityConfig.CHANNEL_VERIFY);
        GuildMessageChannel ch = chId == null ? null : guild.getChannelById(GuildMessageChannel.class, chId);
        if (ch == null) {
            ctx.database().verification().closeRequest(guild.getId(), user.getId());
            Replies.ephemeral(event, ctx, "Canal de aprovação indisponível. Avise a administração.");
            return;
        }
        int accent = EmbedColor.resolve(cfg);
        Container panel = Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Pedido de verificação"),
                Panels.divider(),
                Panels.text("Candidato: " + user.getAsMention() + " · `" + user.getId() + "`"),
                Panels.text(answers),
                ActionRow.of(
                        Button.success(ComponentId.of(AntiRaidService.NS, "vapprove", user.getId()), "Aprovar")
                                .withEmoji(Emojis.button(Emojis.CHECK_YES)),
                        Button.danger(ComponentId.of(AntiRaidService.NS, "vreject", user.getId()), "Recusar")
                                .withEmoji(Emojis.button(Emojis.CHECK_NO))));
        String gid = guild.getId();
        String uid = user.getId();
        ch.sendMessageComponents(panel).useComponentsV2().queue(
                msg -> ctx.database().verification().attachMessage(gid, uid, msg.getId()),
                err -> { });
        Replies.ephemeral(event, ctx,
                Emojis.of(Emojis.CHECK_YES, "✅") + " Pedido enviado! Aguarde a análise de um moderador.");
    }

    // --- verificação: aprovar / recusar ----------------------------------------

    private void approve(ButtonInteractionEvent event, BotContext ctx, String targetId) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas quem tem **Gerenciar Servidor** pode aprovar.");
            return;
        }
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String memberRoleId = cfg.role("membro");
        Role memberRole = memberRoleId == null ? null : guild.getRoleById(memberRoleId);
        if (memberRole == null || !guild.getSelfMember().canInteract(memberRole)) {
            Replies.ephemeral(event, ctx, "Cargo de **membro** não configurado ou acima do meu cargo.");
            return;
        }
        guild.retrieveMemberById(targetId).queue(m -> {
            guild.addRoleToMember(m, memberRole)
                    .reason("Verificação aprovada por " + event.getUser().getName()).queue();
            String unvId = cfg.role("nao-verificado");
            Role unv = unvId == null ? null : guild.getRoleById(unvId);
            if (unv != null && m.getRoles().contains(unv) && guild.getSelfMember().canInteract(unv)) {
                guild.removeRoleFromMember(m, unv).reason("Verificação aprovada").queue();
            }
        }, err -> { });
        ctx.database().verification().markVerified(guild.getId(), targetId, System.currentTimeMillis());
        ctx.database().verification().closeRequest(guild.getId(), targetId);
        event.editComponents(Panels.container(EmbedColor.resolve(cfg),
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Verificação aprovada"),
                Panels.divider(),
                Panels.text("<@" + targetId + "> aprovado por " + event.getUser().getAsMention() + ".")))
                .useComponentsV2().queue();
    }

    private void reject(ButtonInteractionEvent event, BotContext ctx, String targetId) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas quem tem **Gerenciar Servidor** pode recusar.");
            return;
        }
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        ctx.database().verification().closeRequest(guild.getId(), targetId);
        event.editComponents(Panels.container(EmbedColor.resolve(cfg),
                Panels.text("## " + Emojis.of(Emojis.WARN, "⚠️") + " Verificação recusada"),
                Panels.divider(),
                Panels.text("<@" + targetId + "> recusado por " + event.getUser().getAsMention()
                        + ". Pode tentar de novo.")))
                .useComponentsV2().queue();
    }
}
