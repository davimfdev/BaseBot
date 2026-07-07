package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public final class InfoView {
    private InfoView() {}

    /** Painel de imagem com um botão-link de download dentro do próprio container (regra V2). */
    public static Container imageWithButton(int accent, String title, String imageUrl, String downloadUrl) {
        return Panels.container(accent,
                Panels.text(title),
                MediaGallery.of(MediaGalleryItem.fromUrl(imageUrl)),
                ActionRow.of(Button.link(downloadUrl, "Baixar")));
    }

    public static Container userInfo(int accent, Member m) {
        long created = m.getUser().getTimeCreated().toEpochSecond();
        long joined = m.getTimeJoined().toEpochSecond();
        String roles = m.getRoles().isEmpty() ? "nenhum"
                : m.getRoles().stream().limit(10).map(net.dv8tion.jda.api.entities.Role::getAsMention)
                        .reduce((a, b) -> a + " " + b).orElse("");
        String body = "## " + Emojis.of(Emojis.MEMBER, "👤") + " " + m.getEffectiveName() + "\n"
                + Emojis.of(Emojis.ID, "🆔") + " `" + m.getId() + "`\n"
                + Emojis.of(Emojis.CALENDAR, "📅") + " **Conta criada** · <t:" + created + ":F> • <t:" + created + ":R>\n"
                + Emojis.of(Emojis.JOIN, "📥") + " **Entrou** · <t:" + joined + ":F> • <t:" + joined + ":R>\n"
                + Emojis.of(Emojis.ROLES, "🏷️") + " **Cargos** (`" + m.getRoles().size() + "`) · " + roles;
        return Panels.container(accent, Panels.text(body));
    }

    public static Container serverInfo(int accent, Guild g) {
        long created = g.getTimeCreated().toEpochSecond();
        String body = "## " + Emojis.of(Emojis.SERVER, "🏠") + " " + g.getName() + "\n"
                + Emojis.of(Emojis.ID, "🆔") + " `" + g.getId() + "`\n"
                + Emojis.of(Emojis.MEMBER, "👑") + " **Dono** · <@" + g.getOwnerId() + ">\n"
                + Emojis.of(Emojis.CALENDAR, "📅") + " **Criado** · <t:" + created + ":F> • <t:" + created + ":R>\n"
                + Emojis.of(Emojis.MEMBERS, "👥") + " **Membros** · `" + g.getMemberCount() + "`\n"
                + Emojis.of(Emojis.CHANNEL, "#") + " **Canais** · `" + g.getTextChannels().size() + "` texto / `"
                + g.getVoiceChannels().size() + "` voz\n"
                + Emojis.of(Emojis.ROLES, "🏷️") + " **Cargos** · `" + g.getRoles().size() + "`\n"
                + Emojis.of(Emojis.BOOST, "🚀") + " **Boosts** · `" + g.getBoostCount() + "` (nível "
                + g.getBoostTier().getKey() + ")";
        return Panels.container(accent, Panels.text(body));
    }
}
