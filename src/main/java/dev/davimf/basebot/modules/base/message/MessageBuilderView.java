package dev.davimf.basebot.modules.base.message;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.modals.Modal;

import java.util.ArrayList;
import java.util.List;

/** Renders the {@code /mensagem} builder panel + its modals (BOTSPECS Module 1). */
public final class MessageBuilderView {

    public static final String NS = "msg";

    /** A classic-embed field editable from a button. */
    public record Field(String id, String label, String stateKey, boolean paragraph, String placeholder) {}

    public static final List<Field> FIELDS = List.of(
            new Field("cor", "Cor", "color", false, "Ex: #5865F2"),
            new Field("titulo", "Título", "title", false, "Título do embed"),
            new Field("autor", "Autor", "author", false, "Nome do autor"),
            new Field("descricao", "Descrição", "description", true, "Texto do embed"),
            new Field("imagem", "Imagem (URL)", "image", false, "https://..."),
            new Field("thumbnail", "Thumbnail (URL)", "thumbnail", false, "https://..."),
            new Field("rodape", "Rodapé", "footer", false, "Texto do rodapé"),
            new Field("conteudo", "Conteúdo", "content", true, "Texto fora do embed"));

    private MessageBuilderView() {}

    public static Field field(String id) {
        return FIELDS.stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
    }

    // --- main panel ------------------------------------------------------------

    public static Container panel(ObjectNode state) {
        boolean container = MessageState.isContainer(state);
        boolean wh = state.path("webhook").asBoolean();
        boolean editing = state.hasNonNull("editMessageId");
        String whName = MessageState.str(state, "webhookName");
        String channelId = MessageState.str(state, "channelId");

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## </> Construtor de mensagem" + (editing ? " · editando" : "")
                + "\nTipo atual: **" + (container ? "Container V2" : "Embed clássico") + "**"
                + (wh ? " · via webhook" + (whName != null ? " como `" + whName + "`" : "") : "")));
        kids.add(ActionRow.of(StringSelectMenu.create(ComponentId.of(NS, "type"))
                .addOption("Embed clássico", MessageState.CLASSIC)
                .addOption("Container V2", MessageState.CONTAINER)
                .setDefaultValues(container ? MessageState.CONTAINER : MessageState.CLASSIC)
                .build()));

        if (container) {
            kids.add(ActionRow.of(Button.secondary(ComponentId.of(NS, "ccolor"), "Cor de destaque")));
            kids.add(ActionRow.of(StringSelectMenu.create(ComponentId.of(NS, "addblock"))
                    .setPlaceholder("Adicionar bloco")
                    .addOption("Texto", "text")
                    .addOption("Botões de link", "buttons")
                    .addOption("Separador (com linha)", "sep-line")
                    .addOption("Separador (sem linha)", "sep-plain")
                    .build()));
            ArrayNode blocks = MessageState.blocks(state);
            if (blocks.isEmpty()) {
                kids.add(Panels.text("*(sem blocos — adicione um acima)*"));
            } else {
                StringBuilder list = new StringBuilder("**Blocos:**");
                StringSelectMenu.Builder manage = StringSelectMenu.create(ComponentId.of(NS, "manage"))
                        .setPlaceholder("Gerenciar um bloco");
                for (int i = 0; i < blocks.size(); i++) {
                    String desc = MessageState.describe((ObjectNode) blocks.get(i));
                    list.append("\n").append(i + 1).append(". ").append(trim(desc, 90));
                    manage.addOption(trim((i + 1) + ". " + desc, 100), String.valueOf(i));
                }
                kids.add(Panels.text(list.toString()));
                kids.add(ActionRow.of(manage.build()));
            }
        } else {
            kids.add(Panels.text("**Embed clássico** — escolha um campo para editar:"));
            kids.add(ActionRow.of(fieldButton("cor"), fieldButton("titulo"),
                    fieldButton("autor"), fieldButton("descricao")));
            kids.add(ActionRow.of(fieldButton("imagem"), fieldButton("thumbnail"),
                    fieldButton("rodape"), fieldButton("conteudo")));
        }

        kids.add(Panels.divider());
        if (editing) {
            // Editing an existing message: target channel + webhook-ness are fixed.
            kids.add(Panels.text("**Edição** — salva diretamente na mensagem original."));
        } else {
            kids.add(Panels.text("**Envio**" + (wh && whName != null ? " · webhook como `" + whName + "`" : "")));
            List<Button> envio = new ArrayList<>();
            envio.add(wh ? Button.success(ComponentId.of(NS, "webhook"), "Webhook: ON")
                    : Button.secondary(ComponentId.of(NS, "webhook"), "Webhook: OFF"));
            if (wh) {
                envio.add(Button.secondary(ComponentId.of(NS, "whname"), "Nome"));
                envio.add(Button.secondary(ComponentId.of(NS, "whavatar"), "Avatar"));
            }
            kids.add(ActionRow.of(envio));
            EntitySelectMenu.Builder channel = EntitySelectMenu
                    .create(ComponentId.of(NS, "channel"), SelectTarget.CHANNEL)
                    .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                    .setPlaceholder("Canal de destino");
            if (channelId != null) {
                channel.setDefaultValues(DefaultValue.channel(channelId));
            }
            kids.add(ActionRow.of(channel.build()));
        }
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "send"), editing ? "Salvar" : "Enviar"),
                Button.danger(ComponentId.of(NS, "cancel"), "Cancelar")));
        return Panels.container(builderAccent(state), kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container blockPanel(ObjectNode state, int index) {
        ArrayNode blocks = MessageState.blocks(state);
        ObjectNode block = (ObjectNode) blocks.get(index);
        return Panels.container(builderAccent(state),
                Panels.text("## Bloco " + (index + 1) + "\n" + MessageState.describe(block)),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "bedit", String.valueOf(index)), "Editar"),
                        Button.danger(ComponentId.of(NS, "bdel", String.valueOf(index)), "Remover"),
                        Button.secondary(ComponentId.of(NS, "bup", String.valueOf(index)), "↑")
                                .withDisabled(index == 0),
                        Button.secondary(ComponentId.of(NS, "bdown", String.valueOf(index)), "↓")
                                .withDisabled(index == blocks.size() - 1)),
                ActionRow.of(Button.secondary(ComponentId.of(NS, "back"), "◀ Voltar")));
    }

    // --- modals ----------------------------------------------------------------

    public static Modal fieldModal(Field f, String current) {
        TextInput input = TextInput.create("v", f.paragraph() ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT)
                .setPlaceholder(f.placeholder()).setRequired(false).setMaxLength(f.paragraph() ? 4000 : 256)
                .setValue(current).build();
        return Modal.create(ComponentId.of(NS, "fldform", f.id()), f.label())
                .addComponents(Label.of(f.label(), input)).build();
    }

    public static Modal colorModal(String current) {
        TextInput input = TextInput.create("v", TextInputStyle.SHORT)
                .setPlaceholder("Ex: #5865F2").setRequired(false).setMaxLength(9).setValue(current).build();
        return Modal.create(ComponentId.of(NS, "ccolorform"), "Cor de destaque")
                .addComponents(Label.of("Cor (hex)", input)).build();
    }

    public static Modal textBlockModal(String customId, String current) {
        TextInput input = TextInput.create("v", TextInputStyle.PARAGRAPH)
                .setPlaceholder("# Título\nTexto em markdown…").setRequired(true).setMaxLength(4000)
                .setValue(current).build();
        return Modal.create(customId, "Bloco de texto")
                .addComponents(Label.of("Markdown do bloco", input)).build();
    }

    public static Modal buttonModal(String customId) {
        TextInput label = TextInput.create("label", TextInputStyle.SHORT)
                .setPlaceholder("Nome do botão").setRequired(true).setMaxLength(80).build();
        TextInput url = TextInput.create("url", TextInputStyle.SHORT)
                .setPlaceholder("https://…").setRequired(true).setMaxLength(500).build();
        return Modal.create(customId, "Botão de link")
                .addComponents(Label.of("Rótulo", label), Label.of("Link (URL)", url)).build();
    }

    public static Modal textModal(String action, String title, String labelText, String placeholder, String current) {
        TextInput input = TextInput.create("v", TextInputStyle.SHORT)
                .setPlaceholder(placeholder).setRequired(false).setMaxLength(400).setValue(current).build();
        return Modal.create(ComponentId.of(NS, action), title)
                .addComponents(Label.of(labelText, input)).build();
    }

    // --- helpers ---------------------------------------------------------------

    private static Button fieldButton(String id) {
        return Button.secondary(ComponentId.of(NS, "fld", id), field(id).label().split(" ")[0]);
    }

    private static int builderAccent(ObjectNode state) {
        String key = MessageState.isContainer(state)
                ? MessageState.str(MessageState.container(state), "color")
                : MessageState.str(MessageState.classic(state), "color");
        return EmbedColor.parse(key).orElse(EmbedColor.DEFAULT);
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
