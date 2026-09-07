package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Money;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;

import java.math.BigDecimal;

/**
 * Renders a Pix charge (BR Code copy-paste + QR image + owner-restricted confirm button)
 * as a reusable Components V2 payload, so both the {@code /pix} send panel and budget approval
 * (auto-dispatch) produce the identical embed.
 */
public final class PixDispatch {

    private PixDispatch() {}

    /** A ready-to-send Pix charge: the container plus the QR image to attach. */
    public record Rendered(Container container, FileUpload file) {}

    /**
     * @param key         the seller's Pix key
     * @param amountCents charge amount in cents; {@code <= 0} produces an open-value charge
     * @param accent      embed accent colour
     * @param ownerId     the user allowed to press "Confirmar Pagamento" (the seller)
     * @param clientId    optional client attributed to the charge ({@code null}/blank = none)
     */
    public static Rendered render(PixKey key, long amountCents, int accent, String ownerId, String clientId) {
        PixPayload.Builder builder = PixPayload.builder()
                .key(key.keyValue())
                .merchantName(key.merchantName());
        if (amountCents > 0) {
            builder.amount(BigDecimal.valueOf(amountCents, 2));
        }
        String brCode = builder.build().toBrCode();
        FileUpload qr = FileUpload.fromData(PixQrCode.pngBytes(brCode, 360), "pix.png");

        boolean hasClient = clientId != null && !clientId.isBlank();
        String intro = "> Use o **Pix Copia e Cola** abaixo ou leia o QR Code para efetuar o pagamento."
                + (amountCents > 0 ? "\n" + Emojis.of(Emojis.CASH, "💵") + " **Valor** · `" + Money.format(amountCents) + "`" : "")
                + (hasClient ? "\n" + Emojis.of(Emojis.MEMBER, "🧑") + " **Cliente** · <@" + clientId + ">" : "");
        Container container = Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.GEM, "💠") + " Cobrança Pix"),
                Panels.divider(),
                Panels.text(intro),
                Panels.text("```" + brCode + "```"),
                MediaGallery.of(MediaGalleryItem.fromUrl("attachment://pix.png")),
                ActionRow.of(Button.success(
                                ComponentId.of("pix", "confirmar", ownerId, String.valueOf(amountCents),
                                        hasClient ? clientId : ""),
                                "Confirmar pagamento")
                        .withEmoji(Emojis.button(Emojis.CHECK_YES))));
        return new Rendered(container, qr);
    }

    /**
     * Painel que substitui a cobrança depois que o vendedor confirma o pagamento: sem chave, sem
     * QR e sem botão — só o título de confirmação, quem confirmou, o cliente (se houver) e o valor.
     */
    public static Container confirmedContainer(int accent, long amountCents, String sellerId, String clientId) {
        StringBuilder body = new StringBuilder("> Pagamento recebido e confirmado pelo vendedor <@" + sellerId + ">.");
        if (clientId != null && !clientId.isBlank()) {
            body.append("\n").append(Emojis.of(Emojis.MEMBER, "🧑")).append(" **Cliente** · <@").append(clientId).append(">");
        }
        if (amountCents > 0) {
            body.append("\n").append(Emojis.of(Emojis.CASH, "💵")).append(" **Valor** · `").append(Money.format(amountCents)).append("`");
        }
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Pagamento Confirmado"),
                Panels.divider(),
                Panels.text(body.toString()));
    }
}
