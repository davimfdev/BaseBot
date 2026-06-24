package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.util.Money;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.utils.FileUpload;

import java.math.BigDecimal;

/**
 * Renders a Pix charge (BR Code copy-paste + QR image + owner-restricted confirm button)
 * as a reusable Components V2 payload, so both {@code /pix gerar} and budget approval
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
     */
    public static Rendered render(PixKey key, long amountCents, int accent, String ownerId) {
        PixPayload.Builder builder = PixPayload.builder()
                .key(key.keyValue())
                .merchantName(key.merchantName())
                .merchantCity(key.merchantCity());
        if (amountCents > 0) {
            builder.amount(BigDecimal.valueOf(amountCents, 2));
        }
        String brCode = builder.build().toBrCode();
        FileUpload qr = FileUpload.fromData(PixQrCode.pngBytes(brCode, 360), "pix.png");

        String text = "## Cobrança Pix\n**Pix Copia e Cola:**\n```" + brCode + "```"
                + (amountCents > 0 ? "\n**Valor:** " + Money.format(amountCents) : "");
        Container container = Panels.container(accent,
                Panels.text(text),
                MediaGallery.of(MediaGalleryItem.fromUrl("attachment://pix.png")),
                ActionRow.of(Button.success(ComponentId.of("pix", "confirmar", ownerId), "Confirmar Pagamento")));
        return new Rendered(container, qr);
    }
}
