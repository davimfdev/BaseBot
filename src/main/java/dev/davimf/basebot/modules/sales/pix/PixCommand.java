package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.PixKey;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Module 3 Pix command. The seller is identified by the guild's configured "vendedor"
 * role (set in {@code /setup → Cargos}, stored in {@code guild_config.roles}); the
 * command never takes a role option. {@code /pix registrar} stores the seller's key;
 * {@code /pix gerar} generates a BR Code (copy-paste + QR) with an owner-restricted
 * confirmation button.
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
                        new SubcommandData("registrar", "Registra a chave Pix do vendedor configurado.")
                                .addOptions(new OptionData(OptionType.STRING, "tipo", "Tipo da chave", true)
                                        .addChoice("CPF", "CPF").addChoice("CNPJ", "CNPJ")
                                        .addChoice("E-mail", "EMAIL").addChoice("Telefone", "PHONE")
                                        .addChoice("Aleatória", "RANDOM"))
                                .addOption(OptionType.STRING, "chave", "Valor da chave Pix", true)
                                .addOption(OptionType.STRING, "nome", "Nome do recebedor (máx 25)", true)
                                .addOption(OptionType.STRING, "cidade", "Cidade do recebedor (máx 15)", true),
                        new SubcommandData("gerar", "Gera uma cobrança Pix do vendedor configurado.")
                                .addOption(OptionType.NUMBER, "valor", "Valor (opcional)", false)
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String sellerRoleId = sellerRoleId(event, ctx);
        if (sellerRoleId == null) {
            event.reply("Cargo de vendedor não configurado. Defina em /setup → Cargos → Vendedor (Pix).")
                    .setEphemeral(true).queue();
            return;
        }
        String sub = event.getSubcommandName();
        if ("registrar".equals(sub)) {
            registrar(event, sellerRoleId);
        } else if ("gerar".equals(sub)) {
            gerar(event, sellerRoleId);
        } else {
            event.reply("Subcomando inválido.").setEphemeral(true).queue();
        }
    }

    /** Resolves the configured seller role id from guild config, or null if unset. */
    private String sellerRoleId(SlashCommandInteractionEvent event, BotContext ctx) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String roleId = cfg.role(SELLER_ROLE_KEY);
        return (roleId == null || roleId.isBlank()) ? null : roleId;
    }

    private void registrar(SlashCommandInteractionEvent event, String sellerRoleId) {
        String tipo = event.getOption("tipo", OptionMapping::getAsString);
        String chave = event.getOption("chave", OptionMapping::getAsString);
        String nome = event.getOption("nome", OptionMapping::getAsString);
        String cidade = event.getOption("cidade", OptionMapping::getAsString);

        keys.upsert(new PixKey(event.getGuild().getId(), sellerRoleId, tipo, chave, nome, cidade));
        event.reply("Chave Pix registrada para o cargo de vendedor <@&" + sellerRoleId + ">.")
                .setEphemeral(true).queue();
    }

    private void gerar(SlashCommandInteractionEvent event, String sellerRoleId) {
        Optional<PixKey> maybe = keys.findByRole(event.getGuild().getId(), sellerRoleId);
        if (maybe.isEmpty()) {
            event.reply("Nenhuma chave Pix registrada para o vendedor. Use /pix registrar primeiro.")
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

        String ownerId = event.getUser().getId();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Cobrança Pix")
                .setDescription("**Pix Copia e Cola:**\n```" + brCode + "```")
                .setImage("attachment://pix.png")
                .setColor(0x00B894);
        if (valor != null && valor > 0) {
            embed.addField("Valor", "R$ " + String.format("%.2f", valor), true);
        }

        Button confirm = Button.success(
                ComponentId.of("pix", "confirmar", ownerId), "Confirmar Pagamento");

        event.replyEmbeds(embed.build())
                .addFiles(FileUpload.fromData(qrPng, "pix.png"))
                .addComponents(ActionRow.of(confirm))
                .queue();
    }
}
