package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Botões Entrar/Iniciar do crime organizado (namespace "org"). */
public final class OrgCrimeComponentHandler implements ComponentHandler {

    private final OrgCrimeService svc;

    public OrgCrimeComponentHandler(OrgCrimeService svc) { this.svc = svc; }

    @Override public String namespace() { return OrgCrimeView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        String userId = event.getMember().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        // Botão público pode ser clicado depois que alguém desligou a economia — checa antes de tudo.
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "A economia está desligada.");
            return;
        }
        int accent = EmbedColor.resolve(cfg);
        switch (id.action()) {
            case "join" -> {
                String err = svc.join(guildId, userId);
                if (err != null) { Replies.ephemeral(event, ctx, err); return; }
                var lobby = svc.lobby(guildId);                 // pode ter expirado entre o join e o edit
                if (lobby == null) { Replies.ephemeral(event, ctx, "O lobby expirou."); return; }
                event.editComponents(OrgCrimeView.panel(accent, lobby, cfg)).useComponentsV2().queue();
            }
            case "start" -> {
                var l = svc.lobby(guildId);
                if (l == null) { Replies.ephemeral(event, ctx, "Não há lobby."); }
                else if (!l.leaderId().equals(userId)) { Replies.ephemeral(event, ctx, "Só quem abriu pode iniciar."); }
                else {
                    String res = svc.start(guildId);
                    event.editComponents(OrgCrimeView.result(accent, res)).useComponentsV2().queue();
                }
            }
            default -> { }
        }
    }
}
