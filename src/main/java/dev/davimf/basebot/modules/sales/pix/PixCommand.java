package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.utils.FileUpload;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Module 3 Pix command. The guild's configured "vendedor" role (set in
 * {@code /setup → Cargos}, stored in {@code guild_config.roles}) gates who may use the
 * command — but each seller's Pix key is stored <b>per person</b> (by user id), not per
 * role. {@code /pix registrar} stores the invoking seller's own key; {@code /pix gerar}
 * generates a BR Code (copy-paste + QR) from it with an owner-restricted confirmation
 * button.
 */
public final class PixCommand implements SlashCommand {

    /** Logical role key in guild_config.roles — must match SetupRoleKeys' "vendedor". */
    private static final String SELLER_ROLE_KEY = "vendedor";

    private final PixKeyRepository keys;

    public PixCommand(PixKeyRepository keys) {
        this.keys = keys;
    }

    @Override
    public String name() {
        return "pix";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("pix", "Gerenciar e gerar cobranças Pix.")
                .addSubcommands(
                        new SubcommandData("registrar", "Registra a sua chave Pix (vendedor).")
                                .addOptions(new OptionData(OptionType.STRING, "tipo", "Tipo da chave", true)
                                        .addChoice("CPF", "CPF").addChoice("CNPJ", "CNPJ")
                                        .addChoice("E-mail", "EMAIL").addChoice("Telefone", "PHONE")
                                        .addChoice("Aleatória", "RANDOM"))
                                .addOption(OptionType.STRING, "chave", "Valor da chave Pix", true)
                                .addOption(OptionType.STRING, "nome", "Nome do recebedor (máx 25)", true)
                                .addOption(OptionType.STRING, "cidade", "Cidade do recebedor (máx 15)", true),
                        new SubcommandData("gerar", "Gera uma cobrança com a sua chave Pix.")
                                .addOption(OptionType.NUMBER, "valor", "Valor (opcional)", false)
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String sellerRoleId = cfg.role(SELLER_ROLE_KEY);
        if (sellerRoleId == null || sellerRoleId.isBlank()) {
            event.reply("Cargo de vendedor não configurado. Defina em /setup → Cargos → Vendedor (Pix).")
                    .setEphemeral(true).queue();
            return;
        }
        // The seller role only gates access; each seller's Pix key is stored per person.
        boolean isSeller = event.getMember().getRoles().stream()
                .anyMatch(r -> r.getId().equals(sellerRoleId));
        if (!isSeller) {
            event.reply("Apenas membros com o cargo de vendedor (<@&" + sellerRoleId + ">) podem usar o /pix.")
                    .setEphemeral(true).queue();
            return;
        }
        String sub = event.getSubcommandName();
        if ("registrar".equals(sub)) {
            registrar(event);
        } else if ("gerar".equals(sub)) {
            gerar(event, EmbedColor.resolve(cfg));
        } else {
            event.reply("Subcomando inválido.").setEphemeral(true).queue();
        }
    }

    private void registrar(SlashCommandInteractionEvent event) {
        String tipo = event.getOption("tipo", OptionMapping::getAsString);
        String chave = event.getOption("chave", OptionMapping::getAsString);
        String nome = event.getOption("nome", OptionMapping::getAsString);
        String cidade = event.getOption("cidade", OptionMapping::getAsString);

        keys.upsert(new PixKey(event.getGuild().getId(), event.getUser().getId(), tipo, chave, nome, cidade));
        event.reply("Sua chave Pix foi registrada.").setEphemeral(true).queue();
    }

    private void gerar(SlashCommandInteractionEvent event, int accent) {
        Optional<PixKey> maybe = keys.findByUser(event.getGuild().getId(), event.getUser().getId());
        if (maybe.isEmpty()) {
            event.reply("Você ainda não registrou sua chave Pix. Use /pix registrar primeiro.")
                    .setEphemeral(true).queue();
            return;
        }
        PixKey key = maybe.get();

        Double valor = event.getOption("valor", OptionMapping::getAsDouble);
        PixPayload.Builder builder = PixPayload.builder()
                .key(key.keyValue())
                .merchantName(key.merchantName())
                .merchantCity(key.merchantCity());
        if (valor != null && valor > 0) {
            builder.amount(BigDecimal.valueOf(valor));
        }
        String brCode = builder.build().toBrCode();
        byte[] qrPng = PixQrCode.pngBytes(brCode, 360);
        FileUpload qr = FileUpload.fromData(qrPng, "pix.png");

        String ownerId = event.getUser().getId();
        String text = "## Cobrança Pix\n**Pix Copia e Cola:**\n```" + brCode + "```"
                + ((valor != null && valor > 0) ? "\n**Valor:** R$ " + String.format("%.2f", valor) : "");
        Button confirm = Button.success(
                ComponentId.of("pix", "confirmar", ownerId), "Confirmar Pagamento");

        Container container = Panels.container(accent,
                Panels.text(text),
                MediaGallery.of(MediaGalleryItem.fromUrl("attachment://pix.png")),
                ActionRow.of(confirm));

        event.replyComponents(container)
                .useComponentsV2()
                .addFiles(qr)
                .queue();
    }
}
